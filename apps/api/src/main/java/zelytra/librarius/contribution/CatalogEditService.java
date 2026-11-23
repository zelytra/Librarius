package zelytra.librarius.contribution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.CatalogEdit;
import zelytra.librarius.domain.CatalogEditStatus;
import zelytra.librarius.domain.CatalogEditTarget;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.EditionSaleLink;
import zelytra.librarius.domain.Series;
import zelytra.librarius.domain.VerificationOutcome;
import zelytra.librarius.domain.repository.AppUserRepository;
import zelytra.librarius.domain.repository.CatalogEditRepository;
import zelytra.librarius.domain.repository.EditionRepository;
import zelytra.librarius.domain.repository.EditionSaleLinkRepository;
import zelytra.librarius.domain.repository.SeriesRepository;
import zelytra.librarius.social.NotificationService;
import zelytra.librarius.web.ApiDtos.CatalogEditCreateDto;
import zelytra.librarius.web.ApiDtos.CatalogEditDto;
import zelytra.librarius.web.ApiDtos.CatalogEditFieldsDto;
import zelytra.librarius.web.ApiDtos.CatalogEditPageDto;
import zelytra.librarius.web.ApiDtos.FieldChangeDto;
import zelytra.librarius.web.ApiDtos.MemberSummaryDto;
import zelytra.librarius.web.ApiDtos.SaleLinkDto;
import zelytra.librarius.web.PageRequest;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Member edits of the shared catalog (#618), the sale links they carry (#619), and the
 * automatic check against a store page (#620).
 *
 * <p>A proposal is the diff of what it changes. It waits for an admin — unless an admin wrote
 * it, or it adds a sale link whose store page confirms the book (ISBN and title), in which case
 * it is applied at once and still listed for the admins to see, and revert if need be.
 *
 * <p>The store page is fetched outside any transaction: a slow store must not hold a database
 * connection.
 */
@ApplicationScoped
public class CatalogEditService {

    private static final Logger LOG = Logger.getLogger(CatalogEditService.class);

    /** The pseudo-field the sale links travel under in a diff: their URLs, one per line. */
    static final String SALE_LINKS = "saleLinks";

    /** One changed field: the value replaced and the value proposed, both canonical. */
    record Change(String before, String after) {
    }

    @Inject
    CatalogEditRepository edits;

    @Inject
    EditionRepository editions;

    @Inject
    SeriesRepository seriesRepository;

    @Inject
    EditionSaleLinkRepository saleLinks;

    @Inject
    AppUserRepository users;

    @Inject
    CatalogFields fields;

    @Inject
    StorePageFetcher stores;

    @Inject
    NotificationService notifications;

    @Inject
    ObjectMapper json;

    // ── Reading ─────────────────────────────────────────────────────────────

    @Transactional
    public CatalogEditFieldsDto current(String userId, CatalogEditTarget type, UUID targetId) {
        Object target = target(type, targetId);
        List<SaleLinkDto> links = type == CatalogEditTarget.EDITION
                ? saleLinks.ofEdition(targetId).stream().map(SaleLinkDto::of).toList()
                : List.of();
        return new CatalogEditFieldsDto(type.name(), targetId, fields.read(type, target), links,
                edits.hasPending(userId, type, targetId));
    }

    @Transactional
    public List<SaleLinkDto> saleLinksOf(UUID editionId) {
        if (editions.findById(editionId) == null) {
            throw new NotFoundException();
        }
        return saleLinks.ofEdition(editionId).stream().map(SaleLinkDto::of).toList();
    }

    @Transactional
    public CatalogEditPageDto list(CatalogEditStatus status, PageRequest window) {
        var query = status == null
                ? edits.find("order by createdAt desc, id")
                : edits.find("status = ?1 order by createdAt desc, id", status);
        List<CatalogEditDto> items = query.page(window.page(), window.size()).list().stream()
                .map(this::toDto).toList();
        return new CatalogEditPageDto(items, window.page(), window.size(), query.count());
    }

    // ── Submitting ──────────────────────────────────────────────────────────

    /**
     * Records {@code submitterId}'s proposal: applied at once for an admin or when a store page
     * confirms the book, pending otherwise. 400 on an unknown field, a bad value or a proposal
     * that changes nothing; 404 on an unknown record; 409 when the member already has one
     * pending on it, or when a series would take the title of another.
     */
    public CatalogEditDto submit(String submitterId, boolean admin, CatalogEditCreateDto dto) {
        Prepared prepared = QuarkusTransaction.requiringNew().call(() -> prepare(submitterId, dto));

        VerificationOutcome verification = VerificationOutcome.NOT_RUN;
        String detail = null;
        String checkedUrl = null;
        if (!admin && !prepared.addedLinks().isEmpty()) {
            verification = VerificationOutcome.FAILED;
            for (String url : prepared.addedLinks()) {
                checkedUrl = url;
                detail = verify(url, prepared);
                if (detail == null) {
                    verification = VerificationOutcome.PASSED;
                    break;
                }
            }
        }
        VerificationOutcome outcome = verification;
        String why = detail;
        String url = checkedUrl;
        return QuarkusTransaction.requiringNew().call(() -> record(submitterId, admin, dto, prepared, outcome, why, url));
    }

    /** What a proposal changes, and what the store check needs to know about the book. */
    private record Prepared(Map<String, Change> changes, List<String> addedLinks,
            String isbn13, String isbn10, String title) {
    }

    private Prepared prepare(String submitterId, CatalogEditCreateDto dto) {
        CatalogEditTarget type = dto.targetType();
        Object target = target(type, dto.targetId());
        if (edits.hasPending(submitterId, type, dto.targetId())) {
            throw conflict("A proposal of yours is already waiting on this record");
        }
        Map<String, Change> changes = new LinkedHashMap<>();
        Map<String, String> proposed = dto.fields() == null ? Map.of() : dto.fields();
        proposed.forEach((name, raw) -> {
            String after = fields.parse(type, name, raw);
            String before = fields.read(type, target, name);
            if (!java.util.Objects.equals(before, after)) {
                changes.put(name, new Change(before, after));
            }
        });
        List<String> added = List.of();
        if (dto.saleLinks() != null) {
            if (type != CatalogEditTarget.EDITION) {
                throw new BadRequestException("Only an edition has sale links");
            }
            String before = joinLinks(saleLinks.ofEdition(dto.targetId()).stream().map(l -> l.url).toList());
            List<String> after = canonicalLinks(dto.saleLinks());
            if (!java.util.Objects.equals(before, joinLinks(after))) {
                changes.put(SALE_LINKS, new Change(before, joinLinks(after)));
                Set<String> known = new HashSet<>(splitLinks(before));
                added = after.stream().filter(link -> !known.contains(link)).toList();
            }
        }
        if (changes.isEmpty()) {
            throw new BadRequestException("This proposal changes nothing");
        }
        if (type == CatalogEditTarget.SERIES && changes.containsKey("title")) {
            refuseTakenSeriesTitle((Series) target, changes.get("title").after());
        }
        String isbn13 = null;
        String isbn10 = null;
        String title = null;
        if (target instanceof Edition edition) {
            isbn13 = proposedOr(changes, "isbn13", edition.isbn13);
            isbn10 = proposedOr(changes, "isbn10", edition.isbn10);
            title = proposedOr(changes, "title", edition.work.title);
        }
        return new Prepared(changes, added, isbn13, isbn10, title);
    }

    /** {@code null} when the page confirms the book, else why it does not. */
    private String verify(String url, Prepared book) {
        try {
            StorePageCheck.Outcome outcome = StorePageCheck.check(stores.fetch(url), book.isbn13(), book.isbn10(), book.title());
            return outcome == StorePageCheck.Outcome.PASSED ? null : outcome.name();
        } catch (IOException | RuntimeException e) {
            LOG.infof("Store page check could not read %s: %s", url, e.getMessage());
            return "FETCH_FAILED";
        }
    }

    private CatalogEditDto record(String submitterId, boolean admin, CatalogEditCreateDto dto, Prepared prepared,
            VerificationOutcome verification, String detail, String url) {
        // Re-checked in this transaction: the first one ended before the store was fetched.
        if (edits.hasPending(submitterId, dto.targetType(), dto.targetId())) {
            throw conflict("A proposal of yours is already waiting on this record");
        }
        CatalogEdit edit = new CatalogEdit();
        edit.submitterId = submitterId;
        edit.targetType = dto.targetType();
        edit.targetId = dto.targetId();
        edit.changes = write(prepared.changes());
        edit.verification = verification;
        edit.verificationDetail = verification == VerificationOutcome.FAILED ? detail : null;
        edit.verificationUrl = url;
        if (admin || verification == VerificationOutcome.PASSED) {
            apply(edit, prepared.changes(), true);
            edit.status = CatalogEditStatus.APPLIED;
            edit.reviewedAt = OffsetDateTime.now();
            if (admin) {
                edit.reviewerId = submitterId;
            }
        }
        edits.persist(edit);
        edits.flush();
        // created_at is the database's clock: read it back for the answer.
        edits.getEntityManager().refresh(edit);
        return toDto(edit);
    }

    // ── Reviewing ───────────────────────────────────────────────────────────

    @Transactional
    public CatalogEditDto approve(String adminId, UUID editId, String note) {
        CatalogEdit edit = decided(editId, CatalogEditStatus.PENDING);
        Map<String, Change> changes = read(edit.changes);
        if (edit.targetType == CatalogEditTarget.SERIES && changes.containsKey("title")) {
            refuseTakenSeriesTitle((Series) target(edit.targetType, edit.targetId), changes.get("title").after());
        }
        apply(edit, changes, true);
        return close(adminId, edit, CatalogEditStatus.APPLIED, note);
    }

    @Transactional
    public CatalogEditDto reject(String adminId, UUID editId, String note) {
        return close(adminId, decided(editId, CatalogEditStatus.PENDING), CatalogEditStatus.REJECTED, note);
    }

    /** Writes back the values an applied proposal replaced. */
    @Transactional
    public CatalogEditDto revert(String adminId, UUID editId, String note) {
        CatalogEdit edit = decided(editId, CatalogEditStatus.APPLIED);
        apply(edit, read(edit.changes), false);
        return close(adminId, edit, CatalogEditStatus.REVERTED, note);
    }

    private CatalogEdit decided(UUID editId, CatalogEditStatus expected) {
        CatalogEdit edit = edits.findById(editId);
        if (edit == null) {
            throw new NotFoundException();
        }
        if (edit.status != expected) {
            throw conflict("This proposal is " + edit.status);
        }
        return edit;
    }

    private CatalogEditDto close(String adminId, CatalogEdit edit, CatalogEditStatus status, String note) {
        edit.status = status;
        edit.reviewerId = adminId;
        edit.reviewNote = note == null || note.isBlank() ? null : note.trim();
        edit.reviewedAt = OffsetDateTime.now();
        notifications.edit(adminId, edit.submitterId, edit.id);
        return toDto(edit);
    }

    // ── Writing to the catalog ──────────────────────────────────────────────

    /** Writes each change's {@code after} (forward) or {@code before} (a revert) onto the record. */
    private void apply(CatalogEdit edit, Map<String, Change> changes, boolean forward) {
        Object target;
        try {
            target = target(edit.targetType, edit.targetId);
        } catch (NotFoundException gone) {
            throw conflict("The record this proposal changes no longer exists");
        }
        changes.forEach((name, change) -> {
            String value = forward ? change.after() : change.before();
            if (SALE_LINKS.equals(name)) {
                replaceLinks(edit.targetId, splitLinks(value));
            } else {
                fields.write(edit.targetType, target, name, value);
            }
        });
    }

    private void replaceLinks(UUID editionId, List<String> urls) {
        saleLinks.delete("editionId", editionId);
        saleLinks.flush();
        for (String url : urls) {
            EditionSaleLink link = new EditionSaleLink();
            link.editionId = editionId;
            link.store = SaleStore.of(url).orElseThrow();
            link.url = url;
            saleLinks.persist(link);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Object target(CatalogEditTarget type, UUID id) {
        Object target = type == CatalogEditTarget.EDITION ? editions.findById(id) : seriesRepository.findById(id);
        if (target == null) {
            throw new NotFoundException();
        }
        return target;
    }

    private void refuseTakenSeriesTitle(Series series, String title) {
        seriesRepository.findByKindAndTitle(series.kind, title)
                .filter(other -> !other.id.equals(series.id))
                .ifPresent(other -> {
                    throw conflict("Another series already has this title");
                });
    }

    /** One https link per known store, ordered by store so that two equal lists compare equal. */
    private static List<String> canonicalLinks(List<String> raw) {
        Map<SaleStore, String> byStore = new LinkedHashMap<>();
        for (String link : raw) {
            // Stored, compared and fetched without its tracking parameters (#628).
            String url = SaleStore.clean(link)
                    .orElseThrow(() -> new BadRequestException("Not a page of a known store: " + link.trim()));
            SaleStore store = SaleStore.of(url).orElseThrow();
            if (byStore.put(store, url) != null) {
                throw new BadRequestException("Only one link per store: " + store);
            }
        }
        return byStore.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue).toList();
    }

    private static String joinLinks(List<String> urls) {
        List<String> sorted = new ArrayList<>(urls);
        sorted.sort(Comparator.comparing(url -> SaleStore.of(url).map(Enum::ordinal).orElse(Integer.MAX_VALUE)));
        return sorted.isEmpty() ? null : String.join("\n", sorted);
    }

    private static List<String> splitLinks(String joined) {
        return joined == null ? List.of() : List.of(joined.split("\n"));
    }

    private static String proposedOr(Map<String, Change> changes, String field, String current) {
        Change change = changes.get(field);
        return change != null ? change.after() : current;
    }

    private CatalogEditDto toDto(CatalogEdit edit) {
        Object target = edit.targetType == CatalogEditTarget.EDITION
                ? editions.findById(edit.targetId) : seriesRepository.findById(edit.targetId);
        String title = null;
        String cover = null;
        if (target instanceof Edition edition) {
            title = edition.work.title;
            cover = edition.coverUrl;
        } else if (target instanceof Series series) {
            title = series.title;
            cover = series.coverUrl;
        }
        List<FieldChangeDto> changes = new ArrayList<>();
        for (var entry : read(edit.changes).entrySet()) {
            String current = target == null ? null
                    : SALE_LINKS.equals(entry.getKey())
                            ? joinLinks(saleLinks.ofEdition(edit.targetId).stream().map(l -> l.url).toList())
                            : fields.knows(edit.targetType, entry.getKey())
                                    ? fields.read(edit.targetType, target, entry.getKey()) : null;
            changes.add(new FieldChangeDto(entry.getKey(), entry.getValue().before(), entry.getValue().after(), current));
        }
        return new CatalogEditDto(edit.id, edit.targetType.name(), edit.targetId, title, cover,
                edit.status.name(), edit.verification.name(), edit.verificationDetail, edit.verificationUrl,
                member(edit.submitterId), member(edit.reviewerId), edit.reviewNote, changes,
                edit.createdAt, edit.reviewedAt);
    }

    private MemberSummaryDto member(String id) {
        AppUser user = id == null ? null : users.findById(id);
        return user == null ? null : MemberSummaryDto.of(user);
    }

    private String write(Map<String, Change> changes) {
        try {
            return json.writeValueAsString(changes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Change> read(String changes) {
        try {
            return json.readValue(changes, new TypeReference<LinkedHashMap<String, Change>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ClientErrorException conflict(String message) {
        return new ClientErrorException(message, Response.Status.CONFLICT);
    }
}
