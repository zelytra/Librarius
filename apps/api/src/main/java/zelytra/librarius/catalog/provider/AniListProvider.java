package zelytra.librarius.catalog.provider;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import zelytra.librarius.catalog.CatalogProvider;
import zelytra.librarius.catalog.CatalogQuery;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.catalog.VolumeParser;
import zelytra.librarius.domain.Kind;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Manga catalog provider backed by AniList (GraphQL).
 *
 * <p>AniList describes works, not editions: it holds a title, a start date and the staff who
 * made it, and knows nothing of a publisher, a language or an ISBN. Those three criteria are
 * therefore ignored here rather than faked — the search still answers on the ones it does
 * index, and a book-only criterion simply does not narrow a manga search.
 */
@ApplicationScoped
public class AniListProvider implements CatalogProvider {

    private static final String FIELDS = """
            id title { romaji english } startDate { year month day }
            coverImage { large } description(asHtml: false) isAdult volumes
            staff(perPage: 1) { edges { role node { name { full } } } }
            """;

    /**
     * Page size used when results still have to be narrowed locally. The author search goes
     * through the staff's works, which cannot be filtered on a title or a year server-side,
     * so it asks for AniList's maximum and trims afterwards.
     */
    private static final int MAX_PAGE = 50;

    @Inject
    @RestClient
    AniListClient client;

    /** Absolute deadline of one call, whatever AniList does with the socket. */
    @ConfigProperty(name = "librarius.catalog.provider.call-timeout", defaultValue = "12S")
    Duration callTimeout;

    @Override
    public String name() {
        return "anilist";
    }

    @Override
    public Kind kind() {
        return Kind.MANGA;
    }

    @Override
    public List<CatalogResult> search(CatalogQuery query, int limit) {
        if (query.author() != null) {
            return byAuthor(query, limit);
        }
        if (query.text() == null && query.year() == null) {
            // Only criteria AniList does not index (publisher, language, ISBN): answering the
            // most popular mangas would look like a result and be one by accident.
            return List.of();
        }
        return byTitle(query, limit);
    }

    @Override
    public List<CatalogResult> upcoming(int limit) {
        String gql = "query ($n: Int) { Page(perPage: $n) { media("
                + "type: MANGA, status: NOT_YET_RELEASED, sort: START_DATE, isAdult: false) { "
                + FIELDS + " } } }";
        return fetch(gql, Map.of("n", limit), AniListProvider::mediaOfPage).stream()
                .map(this::toResult)
                .toList();
    }

    /**
     * The number of volumes AniList knows a manga runs to — the {@code volumes} field of the top
     * match for the title. Used off the request path to fill {@code series.total_volumes}; a run
     * still ongoing (or that AniList has not counted) reports {@code 0}, which is left as unknown.
     */
    @Override
    public java.util.OptionalInt seriesVolumes(String title) {
        if (title == null || title.isBlank()) {
            return java.util.OptionalInt.empty();
        }
        String gql = "query ($q: String) { Page(perPage: 1) { media("
                + "type: MANGA, format_not_in: [NOVEL], search: $q, sort: SEARCH_MATCH,"
                + " isAdult: false) { volumes } } }";
        List<AniListClient.Media> media = fetch(gql, Map.of("q", title), AniListProvider::mediaOfPage);
        Integer volumes = media.isEmpty() ? null : media.get(0).volumes();
        return volumes != null && volumes > 0
                ? java.util.OptionalInt.of(volumes)
                : java.util.OptionalInt.empty();
    }

    /**
     * Title search, with the year applied by AniList itself through the start-date window.
     *
     * <p>A null {@code search} is accepted and simply not applied, but a null
     * {@code startDate_greater} is rejected outright ("Illegal operator and value
     * combination"), so the window is only declared when a year is actually being filtered
     * on.
     */
    private List<CatalogResult> byTitle(CatalogQuery query, int limit) {
        boolean withYear = query.year() != null;
        String sort = query.text() != null ? "SEARCH_MATCH" : "POPULARITY_DESC";
        // format_not_in drops the light novels AniList files under type: MANGA — they polluted
        // a manga search with prose editions of the same franchise. Manga and one-shots stay.
        String gql = "query ($q: String, $n: Int"
                + (withYear ? ", $from: FuzzyDateInt, $to: FuzzyDateInt" : "")
                + ") { Page(perPage: $n) { media(type: MANGA, format_not_in: [NOVEL], search: $q, sort: "
                + sort
                + ", isAdult: false"
                + (withYear ? ", startDate_greater: $from, startDate_lesser: $to" : "")
                + ") { " + FIELDS + " } } }";

        Map<String, Object> variables = new HashMap<>();
        variables.put("q", query.text());
        variables.put("n", Math.min(limit, MAX_PAGE));
        if (withYear) {
            variables.put("from", query.year() * 10000 + 101);
            variables.put("to", query.year() * 10000 + 1231);
        }
        return fetch(gql, variables, AniListProvider::mediaOfPage).stream()
                .map(this::toResult)
                .toList();
    }

    /**
     * Author search: AniList does not index the author on a work, it links works to the staff
     * who made them. So the name is resolved to a person first, and their manga are what the
     * search returns — a title or a year narrows that list here, since a staff's works accept
     * no filter of their own. An unknown name answers 404, which {@link #run} reports as no
     * result.
     */
    private List<CatalogResult> byAuthor(CatalogQuery query, int limit) {
        boolean narrowed = query.text() != null || query.year() != null;
        String gql = "query ($a: String, $n: Int) { Staff(search: $a) { staffMedia("
                + "type: MANGA, sort: POPULARITY_DESC, perPage: $n) { nodes { " + FIELDS
                + " } } } }";
        Map<String, Object> variables = Map.of("a", query.author(),
                "n", narrowed ? MAX_PAGE : Math.min(limit, MAX_PAGE));

        return fetch(gql, variables, AniListProvider::mediaOfStaff).stream()
                // staffMedia carries no isAdult filter of its own, unlike the title search.
                .filter(m -> !Boolean.TRUE.equals(m.isAdult()))
                .filter(m -> matchesTitle(m, query.text()))
                .filter(m -> query.year() == null
                        || (m.startDate() != null && query.year().equals(m.startDate().year())))
                .map(this::toResult)
                .limit(limit)
                .toList();
    }

    private static boolean matchesTitle(AniListClient.Media media, String text) {
        if (text == null) {
            return true;
        }
        String needle = text.toLowerCase(Locale.ROOT);
        AniListClient.Title title = media.title();
        return title != null
                && ((title.romaji() != null && title.romaji().toLowerCase(Locale.ROOT).contains(needle))
                        || (title.english() != null
                                && title.english().toLowerCase(Locale.ROOT).contains(needle)));
    }

    /** Runs the query and extracts the media list the answer carries, or none on failure. */
    private List<AniListClient.Media> fetch(String gql, Map<String, Object> variables,
            Function<AniListClient.Data, List<AniListClient.Media>> extract) {
        try {
            AniListClient.GqlResponse res = client.query(new AniListClient.GqlRequest(gql, variables))
                    .await().atMost(callTimeout);
            if (res == null || res.data() == null) {
                return List.of();
            }
            List<AniListClient.Media> media = extract.apply(res.data());
            return media == null ? List.of() : media;
        } catch (Exception e) {
            Log.warnf("AniList search failed: %s", e.getMessage());
            return List.of();
        }
    }

    private static List<AniListClient.Media> mediaOfPage(AniListClient.Data data) {
        return data.page() == null ? null : data.page().media();
    }

    private static List<AniListClient.Media> mediaOfStaff(AniListClient.Data data) {
        return data.staff() == null || data.staff().staffMedia() == null
                ? null
                : data.staff().staffMedia().nodes();
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
        VolumeParser.Parsed volume = VolumeParser.parse(title);
        return new CatalogResult("MANGA", title, author, year, cover, m.description(), null, null,
                null, releaseDate(m.startDate()), volume.seriesTitle(), volume.volumeNumber(), null,
                "anilist", String.valueOf(m.id()));
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
