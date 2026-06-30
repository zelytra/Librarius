package zelytra.librarius.catalog.provider;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import zelytra.librarius.catalog.CatalogProvider;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.domain.Kind;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Fournisseur de catalogue manga adossé à AniList (GraphQL). */
@ApplicationScoped
public class AniListProvider implements CatalogProvider {

    private static final String FIELDS = """
            id title { romaji english } startDate { year month day }
            coverImage { large } description(asHtml: false)
            staff(perPage: 1) { edges { role node { name { full } } } }
            """;

    @Inject
    @RestClient
    AniListClient client;

    @Override
    public Kind kind() {
        return Kind.MANGA;
    }

    @Override
    public List<CatalogResult> search(String query, int limit) {
        // isAdult: false filtre le contenu explicite côté serveur (filtre NSFW).
        String gql = "query ($q: String, $n: Int) { Page(perPage: $n) { media("
                + "type: MANGA, search: $q, sort: SEARCH_MATCH, isAdult: false) { " + FIELDS + " } } }";
        return run(gql, Map.of("q", query, "n", limit));
    }

    @Override
    public List<CatalogResult> upcoming(int limit) {
        String gql = "query ($n: Int) { Page(perPage: $n) { media("
                + "type: MANGA, status: NOT_YET_RELEASED, sort: START_DATE, isAdult: false) { "
                + FIELDS + " } } }";
        return run(gql, Map.of("n", limit));
    }

    private List<CatalogResult> run(String gql, Map<String, Object> variables) {
        try {
            AniListClient.GqlResponse res = client.query(new AniListClient.GqlRequest(gql, variables));
            if (res == null || res.data() == null || res.data().page() == null
                    || res.data().page().media() == null) {
                return List.of();
            }
            return res.data().page().media().stream().map(this::toResult).toList();
        } catch (Exception e) {
            Log.warnf("Recherche AniList échouée : %s", e.getMessage());
            return List.of();
        }
    }

    private CatalogResult toResult(AniListClient.Media m) {
        String title = m.title() == null ? null
                : firstNonBlank(m.title().english(), m.title().romaji());
        String author = m.staff() != null && m.staff().edges() != null && !m.staff().edges().isEmpty()
                && m.staff().edges().get(0).node() != null
                ? m.staff().edges().get(0).node().name().full()
                : null;
        Integer year = m.startDate() != null ? m.startDate().year() : null;
        String cover = m.coverImage() != null ? m.coverImage().large() : null;
        return new CatalogResult("MANGA", title, author, year, cover, m.description(), null, null,
                null, releaseDate(m.startDate()), "anilist", String.valueOf(m.id()));
    }

    private static LocalDate releaseDate(AniListClient.FuzzyDate d) {
        if (d == null || d.year() == null || d.month() == null || d.day() == null) {
            return null;
        }
        try {
            return LocalDate.of(d.year(), d.month(), d.day());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
