package zelytra.librarius.imports;

import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import zelytra.librarius.catalog.CatalogQuery;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.catalog.CatalogService;
import zelytra.librarius.catalog.VolumeParser;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.repository.EditionRepository;

import java.text.Normalizer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Completes imported titles from the catalog, off the request path: the cover, the ISBN, the
 * page count and the publisher an import did not carry.
 *
 * <p>A CSV brings a title and an author and nothing else, and a Booknode scrape a cover at
 * best, so an imported library used to be a wall of blank tiles with "—" for every length. This
 * looks each such edition up in the catalog by title and author, in small batches so one run
 * cannot spend the provider quota, and fills only the fields the edition lacks from the match.
 *
 * <p>Best-effort, and careful: a length or a cover taken from the wrong book is worse than
 * none, so a candidate must be the same book — the same volume of the same series, or the
 * same title by a credited author. Every edition looked up is stamped, matched or not, and
 * only retried after {@link #RETRY_AFTER}: before that stamp existed, the newest titles the
 * catalog did not know were picked again on every run and the rest of a large import never was.
 *
 * <p>Nothing here is user-scoped — an {@link Edition} is shared catalog data — and the catalog
 * is called outside any transaction, so no database connection is held across the outbound call.
 */
@ApplicationScoped
public class ImportEnricher {

    /** Editions resolved per run: a batch, so one run cannot spend the whole provider quota. */
    private static final int BATCH = 25;

    /** Catalog answers to weigh before giving up on a title — the top few, ranked by relevance. */
    private static final int CANDIDATES = 8;

    /** How long an edition the catalog could not complete waits before it is looked up again. */
    static final Duration RETRY_AFTER = Duration.ofDays(7);

    @Inject
    CatalogService catalog;

    @Inject
    EditionRepository editions;

    /** One edition's identity, carried out of the read transaction so the lookup holds no connection. */
    record Target(UUID id, Kind kind, String title, String author, String seriesTitle,
            Integer volumeNumber) {
    }

    /**
     * Off in the test profile (interval {@code off}). {@code SKIP} keeps a run that overran its
     * interval from being doubled up.
     */
    @Scheduled(every = "{librarius.imports.enrich.every}",
            delayed = "{librarius.imports.enrich.delayed}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledEnrich() {
        int completed = enrichNow();
        if (completed > 0) {
            Log.infof("Import enrichment: %d editions completed from the catalog", completed);
        }
    }

    /** Runs one batch now; returns how many editions gained at least one field. */
    public int enrichNow() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Target> batch = QuarkusTransaction.requiringNew().call(() ->
                editions.needingEnrichment(now.minus(RETRY_AFTER), BATCH).stream()
                        .map(e -> new Target(e.id, e.work.kind, e.work.title, e.work.authorsText,
                                e.work.seriesTitle, e.work.volumeNumber))
                        .toList());
        int completed = 0;
        for (Target target : batch) {
            CatalogResult match = lookup(target);
            completed += QuarkusTransaction.requiringNew().call(() -> apply(target.id(), match, now));
        }
        return completed;
    }

    private CatalogResult lookup(Target target) {
        if (target.title() == null || target.title().isBlank()) {
            return null;
        }
        Set<Kind> kinds = target.kind() != null ? Set.of(target.kind()) : Set.of();
        try {
            List<CatalogResult> results = catalog.search(kinds,
                    new CatalogQuery(target.title(), target.author(), null, null, null, null),
                    CANDIDATES);
            return results.stream().filter(r -> sameBook(target, r))
                    .min(PREFERRED).orElse(null);
        } catch (RuntimeException e) {
            Log.warnf("Import enrichment lookup failed for %s: %s", target.id(), e.getMessage());
            return null;
        }
    }

    /**
     * Among the candidates that are the same book, the French edition first — the application
     * is French, and "Dune" otherwise took the ISBN and publisher of an American audiobook —
     * then the one carrying a cover; the catalog's own ranking breaks the remaining ties.
     */
    static final java.util.Comparator<CatalogResult> PREFERRED = java.util.Comparator
            .comparing((CatalogResult r) -> !isFrench(r.language()))
            .thenComparing(r -> !notBlank(r.coverUrl()));

    private static boolean isFrench(String language) {
        return language != null && (language.equalsIgnoreCase("fre") || language.equalsIgnoreCase("fr")
                || language.equalsIgnoreCase("fra") || language.equalsIgnoreCase("français"));
    }

    /**
     * Fills what the edition lacks from the match, and stamps the attempt either way. Only the
     * empty fields: an edition is shared, and nothing the reader or the import set is replaced.
     */
    int apply(UUID editionId, CatalogResult match, OffsetDateTime now) {
        Edition found = editions.findById(editionId);
        if (found == null) {
            return 0;
        }
        found.enrichAttemptedAt = now;
        if (match == null) {
            return 0;
        }
        boolean changed = false;
        if (found.coverUrl == null && notBlank(match.coverUrl())) {
            found.coverUrl = match.coverUrl();
            changed = true;
        }
        if (found.isbn13 == null && notBlank(match.isbn13())) {
            found.isbn13 = match.isbn13();
            changed = true;
        }
        if (found.pageCount == null && match.pageCount() != null && match.pageCount() > 0) {
            found.pageCount = match.pageCount();
            changed = true;
        }
        if (found.publisher == null && notBlank(match.publisher())) {
            found.publisher = match.publisher();
            changed = true;
        }
        return changed ? 1 : 0;
    }

    /**
     * Whether a catalog result is the same book as the imported edition.
     *
     * <p>A volume is matched as a volume: the same series and the same number, whatever wording
     * each side gave it ("One Piece Tome 1" and the BnF's "One piece. 1, East blue"). Anything
     * else must carry the same title — or that title followed by a subtitle — and, when both
     * sides name someone, share a credited name: "Dune" must not take the cover of "Dune
     * Messiah", nor of somebody else's "Dune".
     */
    static boolean sameBook(Target target, CatalogResult result) {
        Integer volume = target.volumeNumber();
        String series = target.seriesTitle();
        if (volume != null && series != null) {
            VolumeParser.Parsed parsed = result.volumeNumber() != null && result.seriesTitle() != null
                    ? new VolumeParser.Parsed(result.seriesTitle(), result.volumeNumber())
                    : VolumeParser.parse(result.title());
            return volume.equals(parsed.volumeNumber())
                    && normalize(series).equals(normalize(parsed.seriesTitle()))
                    && authorsOverlap(target.author(), result.authors());
        }
        String wanted = normalize(target.title());
        String found = normalize(result.title());
        if (wanted.isEmpty() || found.isEmpty()) {
            return false;
        }
        boolean sameTitle = found.equals(wanted)
                || (found.startsWith(wanted + " ") && hasSubtitle(result.title(), target.title()));
        return sameTitle && authorsOverlap(target.author(), result.authors());
    }

    /** The result's title continues past the imported one with a subtitle separator. */
    private static boolean hasSubtitle(String resultTitle, String importedTitle) {
        if (resultTitle == null || importedTitle == null) {
            return false;
        }
        String rest = resultTitle.substring(Math.min(resultTitle.length(),
                importedTitle.trim().length())).trim();
        return rest.startsWith(":") || rest.startsWith("-") || rest.startsWith("—")
                || rest.startsWith(",") || rest.startsWith("(");
    }

    /**
     * Whether the two credit lines share a name. Either side missing proves nothing against the
     * match, so it passes; otherwise some word of three letters or more — a family name, in
     * practice — has to appear on both ("Eiichiro Oda" and "Eiichirō Oda" share "oda" once
     * folded).
     */
    static boolean authorsOverlap(String a, String b) {
        Set<String> left = nameWords(a);
        Set<String> right = nameWords(b);
        if (left.isEmpty() || right.isEmpty()) {
            return true;
        }
        return left.stream().anyMatch(right::contains);
    }

    private static Set<String> nameWords(String names) {
        return Arrays.stream(normalize(names).split(" "))
                .filter(w -> w.length() >= 3)
                .collect(Collectors.toSet());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
