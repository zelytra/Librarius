package zelytra.librarius.library;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.catalog.CatalogService;
import zelytra.librarius.catalog.RateLimiter;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.ReadingProgress;
import zelytra.librarius.domain.Work;
import zelytra.librarius.domain.repository.EditionRepository;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.domain.repository.ReadingProgressRepository;
import zelytra.librarius.web.ApiDtos.EditionDto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The editions of a work, and the ownership row that points at one of them.
 *
 * <p><strong>Scoping.</strong> An edition is shared catalog data — publisher, ISBN, page
 * count — and says nothing about who owns it. The ownership row is the opposite: it is the
 * caller's alone. So the listing is only opened to a work the caller owns something of (an
 * unknown work and someone else's answer the same 404), and a switch only ever moves the
 * caller's own item.
 */
@ApplicationScoped
public class EditionService {

    @Inject
    LibraryItemRepository items;

    @Inject
    EditionRepository editions;

    @Inject
    ReadingProgressRepository progresses;

    @Inject
    CatalogService catalog;

    @Inject
    RateLimiter rateLimiter;

    @Inject
    zelytra.librarius.catalog.CatalogEntryService entries;

    /** How many provider editions to merge in at most, so a large work cannot flood the list. */
    private static final int PROVIDER_EDITION_LIMIT = 40;

    /** Why a switch was refused, so the resource can answer with the right status. */
    public enum Refusal {
        /** The item does not exist, or belongs to someone else. */
        UNKNOWN_ITEM,
        /** The target edition does not exist, or materialises another work. */
        NOT_AN_EDITION_OF_THIS_WORK,
        /** The caller already owns that edition — {@code UNIQUE(user_id, edition_id)}. */
        ALREADY_OWNED
    }

    /**
     * Outcome of a switch: the updated item, or the reason it was refused.
     *
     * @param item     the item, now pointing at the chosen edition; null when refused
     * @param refusal  why it was refused; null on success
     */
    public record SwitchOutcome(LibraryItem item, Refusal refusal) {

        public boolean ok() {
            return refusal == null;
        }

        static SwitchOutcome done(LibraryItem item) {
            return new SwitchOutcome(item, null);
        }

        static SwitchOutcome refused(Refusal refusal) {
            return new SwitchOutcome(null, refusal);
        }
    }

    /**
     * The editions of a work: the ones users of this instance entered, marked with whether the
     * caller owns them, and — when the work came from a provider — the other printings that
     * provider knows of it, merged in and deduplicated.
     *
     * <p>The enrichment is best-effort. A work with no provider reference (a hand-typed entry,
     * or one predating V12) is answered from the stored editions alone, exactly as before; and
     * a provider that returns nothing, is over quota or is down leaves the same stored list
     * rather than failing the read. The provider editions are catalog data only — they carry
     * no {@code id}, and nothing is persisted here.
     *
     * @return empty when the caller owns nothing of that work, which the resource turns into
     *         a 404 — the same answer an unknown identifier gets
     */
    public Optional<List<EditionDto>> editionsOf(String userId, UUID workId) {
        if (workId == null || !items.ownsWork(userId, workId)) {
            return Optional.empty();
        }
        Set<UUID> owned = items.ownedEditionIds(userId, workId);
        List<Edition> stored = editions.listByWork(workId);
        List<EditionDto> result = new ArrayList<>(distinct(stored, owned).stream()
                .map(edition -> EditionDto.of(edition, owned.contains(edition.id)))
                .toList());
        enrichFromProvider(userId, stored, result);
        return Optional.of(result);
    }

    /**
     * Appends the printings the work's provider knows and that no user of this instance
     * entered. The work is read off the stored editions — ownership guarantees at least one —
     * so its {@code provider}/{@code providerRef} decide whether there is anything to ask.
     *
     * <p>The call is charged against the same per-caller quota as a search and served through
     * the same catalog cache: browsing to a detail screen is still an outbound provider call,
     * not an exemption from the quota. Over the limit, the enrichment is simply skipped — a
     * detail screen must not answer 429.
     */
    /**
     * The editions worth offering: one per distinct description, the caller's own first, and
     * none that describes nothing. An edition with no publisher, ISBN, length or date — what
     * every import of the same title by another reader used to leave behind — is not a choice
     * anyone can make, and listed it read "Unknown publisher" once per reader who had it.
     */
    static List<Edition> distinct(List<Edition> stored, Set<UUID> owned) {
        java.util.Map<String, Edition> kept = new java.util.LinkedHashMap<>();
        List<Edition> ordered = new ArrayList<>(stored.stream().filter(e -> owned.contains(e.id)).toList());
        ordered.addAll(stored.stream().filter(e -> !owned.contains(e.id)).toList());
        for (Edition edition : ordered) {
            if (!owned.contains(edition.id) && describesNothing(edition)) {
                continue;
            }
            String key = owned.contains(edition.id) ? "own:" + edition.id : signature(edition);
            kept.putIfAbsent(key, edition);
        }
        // Back in the stored order, which is the order editions were recorded in.
        return stored.stream().filter(kept::containsValue).toList();
    }

    private static boolean describesNothing(Edition e) {
        return blank(e.publisher) && blank(e.isbn13) && e.pageCount == null && e.releaseDate == null
                && blank(e.format) && blank(e.language);
    }

    private static String signature(Edition e) {
        String isbn = normalizeIsbn(e.isbn13);
        if (isbn != null) {
            return "isbn:" + isbn;
        }
        return String.join("|", lower(e.publisher), lower(e.language), lower(e.format),
                String.valueOf(e.pageCount), String.valueOf(e.releaseDate));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void enrichFromProvider(String userId, List<Edition> stored, List<EditionDto> result) {
        Work work = stored.isEmpty() ? null : stored.get(0).work;
        if (work == null || work.provider == null || work.providerRef == null) {
            return;
        }
        if (!rateLimiter.check(userId).allowed()) {
            return;
        }
        List<CatalogResult> fromProvider;
        try {
            fromProvider = catalog.editionsOf(work.provider, work.providerRef, PROVIDER_EDITION_LIMIT);
        } catch (RuntimeException e) {
            Log.warnf("Edition enrichment failed for work %s: %s", work.id, e.getMessage());
            return;
        }
        Set<String> seen = new HashSet<>();
        for (Edition edition : stored) {
            String isbn = normalizeIsbn(edition.isbn13);
            if (isbn != null) {
                seen.add("isbn:" + isbn);
            }
        }
        for (CatalogResult candidate : fromProvider) {
            if (seen.add(dedupKey(candidate))) {
                result.add(providerEdition(candidate));
            }
        }
    }

    /**
     * The identity a provider edition is deduplicated on: its ISBN-13 when it has one — the
     * same printing on two catalogues shares it — then its own reference, then the little the
     * record still carries. Kept in step with the {@code isbn:} keys the stored editions seed.
     */
    private static String dedupKey(CatalogResult candidate) {
        String isbn = normalizeIsbn(candidate.isbn13());
        if (isbn != null) {
            return "isbn:" + isbn;
        }
        if (candidate.providerRef() != null && !candidate.providerRef().isBlank()) {
            return "ref:" + candidate.providerRef();
        }
        return "sig:" + candidate.publisher() + '|' + candidate.language() + '|' + candidate.releaseDate();
    }

    private static String normalizeIsbn(String isbn13) {
        if (isbn13 == null) {
            return null;
        }
        String digits = isbn13.replaceAll("[^0-9Xx]", "");
        return digits.isEmpty() ? null : digits;
    }

    /** A provider printing as an unowned, unpersisted edition — no {@code id}, its own cover. */
    private static EditionDto providerEdition(CatalogResult candidate) {
        return new EditionDto(null, candidate.isbn13(), candidate.publisher(), candidate.language(),
                null, null, candidate.releaseDate(), candidate.coverUrl(), false);
    }

    /**
     * Points an owned title at another edition of the same work — "this is the one I
     * actually own".
     *
     * <p>Everything the row records about the reader is left alone: status, rating, review,
     * rank, acquisition date, and the reading dates. They describe the reader, not the
     * object; buying the hardcover does not un-read a book. The reading position is the one
     * exception, and {@link #reanchor} explains why.
     *
     * <p>Switching onto the edition already in force is a no-op rather than an error: a
     * double click on the section must not be an accident.
     */
    public SwitchOutcome switchEdition(String userId, UUID itemId, UUID editionId) {
        LibraryItem item = items.findOwned(userId, itemId).orElse(null);
        if (item == null) {
            return SwitchOutcome.refused(Refusal.UNKNOWN_ITEM);
        }
        if (editionId == null) {
            return SwitchOutcome.refused(Refusal.NOT_AN_EDITION_OF_THIS_WORK);
        }
        if (editionId.equals(item.edition.id)) {
            return SwitchOutcome.done(item);
        }
        Edition target = editions.findById(editionId);
        if (target == null || !target.work.id.equals(item.edition.work.id)) {
            return SwitchOutcome.refused(Refusal.NOT_AN_EDITION_OF_THIS_WORK);
        }
        // Checked rather than left to the constraint: the user is told which edition they
        // already own, where a violation would surface as a 500.
        if (items.findByEdition(userId, editionId).isPresent()) {
            return SwitchOutcome.refused(Refusal.ALREADY_OWNED);
        }

        item.edition = target;
        reanchor(progresses.findByItem(item.id).orElse(null), item.effectivePageCount());
        return SwitchOutcome.done(item);
    }

    /**
     * Moves a title onto an edition that is not a row yet (#445): a printing a provider lists,
     * or one the reader describes. It is created under the title's own work — it is another
     * materialisation of the same book, never a match for another — then switched to like any
     * stored edition. An ISBN this work already carries reuses that edition instead.
     */
    public SwitchOutcome chooseNewEdition(String userId, UUID itemId,
            zelytra.librarius.web.ApiDtos.EditionChoiceDto dto) {
        LibraryItem item = items.findOwned(userId, itemId).orElse(null);
        if (item == null) {
            return SwitchOutcome.refused(Refusal.UNKNOWN_ITEM);
        }
        if (dto == null) {
            return SwitchOutcome.refused(Refusal.NOT_AN_EDITION_OF_THIS_WORK);
        }
        String isbn = zelytra.librarius.scan.Isbn.toIsbn13(dto.isbn13());
        if (isbn != null) {
            Edition known = editions.find("work.id = ?1 and isbn13 = ?2", item.edition.work.id, isbn)
                    .firstResult();
            if (known != null) {
                return switchEdition(userId, itemId, known.id);
            }
        }
        Edition created = new Edition();
        created.work = item.edition.work;
        created.isbn13 = isbn;
        created.publisher = blankToNull(dto.publisher());
        created.language = blankToNull(dto.language());
        created.pageCount = dto.pageCount();
        created.coverUrl = blankToNull(dto.coverUrl());
        created.format = blankToNull(dto.format());
        created.releaseDate = dto.releaseDate();
        // A provider reference is a pair (V12): both, or neither.
        if (blankToNull(dto.provider()) != null && blankToNull(dto.providerRef()) != null) {
            created.provider = dto.provider().trim();
            created.providerRef = dto.providerRef().trim();
        }
        editions.persist(created);
        return switchEdition(userId, itemId, created.id);
    }

    /**
     * Files the reader's title under another kind — a book, a comic, a manga (#454). The kind
     * belongs to the shared work, so it is never changed for everyone: the reader's copy moves
     * to an edition of a work of that kind (found, or created) carrying what the old edition
     * knew — ISBN, publisher, length, cover — and the same title, authors, series and volume.
     * Everything of the reader's own (status, progress, rating, price, format) stays on the
     * item, and every figure keyed on the kind (goals, statistics, filters) follows at once.
     */
    public java.util.Optional<LibraryItem> moveToKind(String userId, UUID itemId,
            zelytra.librarius.domain.Kind kind) {
        LibraryItem item = items.findOwned(userId, itemId).orElse(null);
        if (item == null || kind == null) {
            return java.util.Optional.ofNullable(item);
        }
        Edition old = item.edition;
        var work = old.work;
        if (work.kind == kind) {
            return java.util.Optional.of(item);
        }
        item.edition = entries.createManualEdition(new zelytra.librarius.web.ApiDtos.ManualBookDto(kind,
                work.title, work.authorsText, work.seriesTitle, work.volumeNumber, old.isbn13,
                old.publisher, old.language, old.pageCount, old.coverUrl, old.format, old.releaseDate,
                work.originalYear, work.synopsis, work.genresText, null, null), userId);
        return java.util.Optional.of(item);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Carries the reading position over to the new edition.
     *
     * <p>The percentage is the only measure that means the same thing in two paginations:
     * being 40 % into a novel is being 40 % into it whichever printing sits on the shelf. A
     * page number is not — page 120 of a 512-page paperback is nowhere near page 120 of a
     * 640-page hardcover. So the position is kept as its percentage and the page is
     * recomputed from the page count of the edition the user now owns, exactly the way
     * {@link ReadingProgressService} derives one side from the other on every save.
     *
     * <p>An edition with no page count leaves the page empty, which is honest: 40 % read,
     * and no way to name the page. And when no percentage was ever recorded — a raw page
     * typed on an edition that carried no page count either — the page is left untouched: it
     * is the only thing the reader ever told us, and re-deriving it would be inventing a
     * conversion from a total nobody knows.
     */
    private static void reanchor(ReadingProgress progress, Integer pageCount) {
        if (progress == null || progress.percent == null) {
            return;
        }
        progress.currentPage = ReadingProgress.pageOf(progress.percent, pageCount);
    }
}
