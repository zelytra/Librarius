package zelytra.librarius.opinion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import zelytra.librarius.domain.Author;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.Work;
import zelytra.librarius.web.ApiDtos.ExternalAuthorDto;
import zelytra.librarius.web.ApiDtos.ExternalRatingDto;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What the rest of the world thinks of a title or an author (#453), to give a book's page
 * something to show before this application has readers of its own.
 *
 * <p>Only open sources, each credited and linked: Open Library's readers (their average
 * rating, how many read it and want to), AniList's community score for a manga, and the
 * opening of an author's French Wikipedia article (CC BY-SA). Nothing is scraped from a site
 * that forbids it, and nothing is stored: answers are cached for half a day, in memory.
 *
 * <p>A source is only asked about a title it can be matched to without guessing — an Open
 * Library work key or an ISBN, an AniList id or an exact title — so a homonym's rating never
 * lands on the wrong book.
 */
@ApplicationScoped
public class ExternalOpinionService {

    static final String OPEN_LIBRARY = "https://openlibrary.org";
    static final String ANILIST = "https://graphql.anilist.co";
    static final String WIKIPEDIA = "https://fr.wikipedia.org/api/rest_v1/page/summary/";

    private static final String ANILIST_BY_ID = """
            query ($id: Int) { Media(id: $id, type: MANGA) { id averageScore popularity siteUrl } }""";
    private static final String ANILIST_BY_TITLE = """
            query ($q: String) { Media(search: $q, type: MANGA) {
              id averageScore popularity siteUrl title { romaji english native } synonyms } }""";

    @Inject
    ExternalJson external;

    @Inject
    EntityManager em;

    private final ObjectMapper json = new ObjectMapper();

    /** The ratings other communities gave this title; empty when none can be matched. */
    @CacheResult(cacheName = "external-opinions")
    @Transactional
    public List<ExternalRatingDto> ofWork(UUID workId) {
        Work work = em.find(Work.class, workId);
        if (work == null) {
            return List.of();
        }
        List<ExternalRatingDto> ratings = new ArrayList<>();
        openLibrary(work).ifPresent(ratings::add);
        if (work.kind == Kind.MANGA) {
            aniList(work).ifPresent(ratings::add);
        }
        return ratings;
    }

    /** The opening of the author's Wikipedia article, or empty. */
    @CacheResult(cacheName = "external-authors")
    @Transactional
    public Optional<ExternalAuthorDto> ofAuthor(UUID authorId) {
        Author author = em.find(Author.class, authorId);
        if (author == null || author.name == null || author.name.isBlank()) {
            return Optional.empty();
        }
        String page = URLEncoder.encode(author.name.trim().replace(' ', '_'), StandardCharsets.UTF_8)
                .replace("+", "_");
        return external.get(WIKIPEDIA + page)
                // A disambiguation page names several people: none of them is surely this one.
                .filter(node -> "standard".equals(node.path("type").asText()))
                .filter(node -> !node.path("extract").asText("").isBlank())
                .map(node -> new ExternalAuthorDto("Wikipédia", node.path("extract").asText(),
                        node.path("description").asText(null),
                        node.path("content_urls").path("desktop").path("page").asText(null),
                        "CC BY-SA 4.0"));
    }

    Optional<ExternalRatingDto> openLibrary(Work work) {
        String query;
        if ("openlibrary".equals(work.provider) && work.providerRef != null) {
            query = "key:/works/" + work.providerRef;
        } else {
            String isbn = em.createQuery("select e.isbn13 from Edition e where e.work.id = :w and e.isbn13 is not null",
                            String.class).setParameter("w", work.id).setMaxResults(1)
                    .getResultStream().findFirst().orElse(null);
            if (isbn == null) {
                return Optional.empty();
            }
            query = "isbn:" + isbn;
        }
        String url = OPEN_LIBRARY + "/search.json?limit=1&fields=key,ratings_average,ratings_count,"
                + "want_to_read_count,already_read_count&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        return external.get(url)
                .map(node -> node.path("docs").path(0))
                .filter(doc -> doc.has("key"))
                .flatMap(doc -> {
                    Double average = doc.hasNonNull("ratings_average") ? round(doc.path("ratings_average").asDouble()) : null;
                    Integer count = doc.hasNonNull("ratings_count") ? doc.path("ratings_count").asInt() : null;
                    Integer read = doc.hasNonNull("already_read_count") ? doc.path("already_read_count").asInt() : null;
                    Integer wanted = doc.hasNonNull("want_to_read_count") ? doc.path("want_to_read_count").asInt() : null;
                    if (average == null && read == null && wanted == null) {
                        return Optional.empty();
                    }
                    return Optional.of(new ExternalRatingDto("Open Library", average, count, read, wanted,
                            OPEN_LIBRARY + doc.path("key").asText()));
                });
    }

    Optional<ExternalRatingDto> aniList(Work work) {
        String body;
        String searched = work.seriesTitle != null ? work.seriesTitle : work.title;
        boolean byId = "anilist".equals(work.provider) && work.providerRef != null
                && work.providerRef.matches("\\d{1,9}");
        try {
            if (byId) {
                body = json.writeValueAsString(Map.of("query", ANILIST_BY_ID,
                        "variables", Map.of("id", Integer.parseInt(work.providerRef))));
            } else if (searched != null) {
                body = json.writeValueAsString(Map.of("query", ANILIST_BY_TITLE, "variables", Map.of("q", searched)));
            } else {
                return Optional.empty();
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return external.post(ANILIST, body)
                .map(node -> node.path("data").path("Media"))
                .filter(media -> media.has("id"))
                .filter(media -> byId || namesTitle(media, searched))
                .filter(media -> media.hasNonNull("averageScore"))
                // AniList scores out of 100; the page speaks out of five.
                .map(media -> new ExternalRatingDto("AniList", round(media.path("averageScore").asDouble() / 20),
                        media.hasNonNull("popularity") ? media.path("popularity").asInt() : null, null, null,
                        media.path("siteUrl").asText(null)));
    }

    /** Whether AniList's answer is the searched title itself, not merely its closest match. */
    static boolean namesTitle(JsonNode media, String searched) {
        String wanted = fold(searched);
        List<String> names = new ArrayList<>();
        JsonNode title = media.path("title");
        title.fields().forEachRemaining(entry -> names.add(entry.getValue().asText(null)));
        media.path("synonyms").forEach(s -> names.add(s.asText(null)));
        return names.stream().anyMatch(name -> name != null && fold(name).equals(wanted));
    }

    private static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
