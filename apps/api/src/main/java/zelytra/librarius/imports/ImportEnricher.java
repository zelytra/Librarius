package zelytra.librarius.imports;

import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import zelytra.librarius.catalog.CatalogQuery;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.catalog.CatalogService;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.repository.EditionRepository;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Fills the page count of imported titles off the request path.
 *
 * <p>A scrape brings a cover and a title but no length — Booknode's table has none — so an
 * imported library reads "—" for its page counts, and the reading-height stack and any page
 * total under-report. This looks each such edition up in the catalog by title and author, in
 * small batches so one run cannot spend the provider quota, and writes the length the best
 * match reports. Best-effort: a title the catalog does not fold onto, or that resolves to a
 * different book, is left as it was, to try again another day rather than take a wrong length.
 *
 * <p>Nothing here is user-scoped — an {@link Edition} is shared catalog data — and the catalog
 * is called outside any transaction, so no database connection is held across the outbound call.
 */
@ApplicationScoped
public class ImportEnricher {

    /** Editions resolved per run: a batch, so one run cannot spend the whole provider quota. */
    private static final int BATCH = 25;

    /** Catalog answers to weigh before giving up on a title — the top few, ranked by relevance. */
    private static final int CANDIDATES = 5;

    @Inject
    CatalogService catalog;

    @Inject
    EditionRepository editions;

    /** One edition's identity, carried out of the read transaction so the lookup holds no connection. */
    private record Target(UUID id, Kind kind, String title, String author) {
    }

    /**
     * Off in the test profile (interval {@code off}). {@code SKIP} keeps a run that overran its
     * interval from being doubled up.
     */
    @Scheduled(every = "{librarius.imports.enrich.every}",
            delayed = "{librarius.imports.enrich.delayed}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledEnrich() {
        int filled = enrichNow();
        if (filled > 0) {
            Log.infof("Import enrichment: %d page counts filled", filled);
        }
    }

    /** Resolves a batch of editions still missing a page count; returns how many were filled. */
    public int enrichNow() {
        List<Target> batch = QuarkusTransaction.requiringNew().call(() ->
                editions.needingPageCount(BATCH).stream()
                        .map(e -> new Target(e.id, e.work.kind, e.work.title, e.work.authorsText))
                        .toList());
        int filled = 0;
        for (Target target : batch) {
            Integer pages = lookupPages(target);
            if (pages != null) {
                filled += QuarkusTransaction.requiringNew().call(() -> setPageCount(target.id(), pages));
            }
        }
        return filled;
    }

    /** The page count of the best catalog match that is the same title, or null. */
    private Integer lookupPages(Target target) {
        if (target.title() == null || target.title().isBlank()) {
            return null;
        }
        Set<Kind> kinds = target.kind() != null ? Set.of(target.kind()) : Set.of();
        List<CatalogResult> results = catalog.search(kinds,
                new CatalogQuery(target.title(), target.author(), null, null, null, null), CANDIDATES);
        String wanted = normalize(target.title());
        for (CatalogResult result : results) {
            if (result.pageCount() != null && result.pageCount() > 0
                    && titlesMatch(wanted, normalize(result.title()))) {
                return result.pageCount();
            }
        }
        return null;
    }

    /**
     * Writes the page count onto an edition that still lacks one, and reports whether it did — an
     * edition a concurrent run or a manual edit filled meanwhile is left alone. In a transaction.
     */
    int setPageCount(UUID editionId, int pages) {
        Edition found = editions.findById(editionId);
        if (found == null || found.pageCount != null) {
            return 0;
        }
        found.pageCount = pages;
        return 1;
    }

    /** The same title, give or take the volume decoration each catalog spells its own way. */
    private static boolean titlesMatch(String a, String b) {
        return !a.isEmpty() && !b.isEmpty() && (a.equals(b) || a.contains(b) || b.contains(a));
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
