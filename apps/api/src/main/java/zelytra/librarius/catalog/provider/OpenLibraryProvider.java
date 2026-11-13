package zelytra.librarius.catalog.provider;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import zelytra.librarius.catalog.CatalogProvider;
import zelytra.librarius.catalog.CatalogQuery;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.catalog.VolumeParser;
import zelytra.librarius.domain.Kind;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Book catalog provider backed by Open Library (no API key needed). */
@ApplicationScoped
public class OpenLibraryProvider implements CatalogProvider {

    private static final String FIELDS =
            "key,title,author_name,first_publish_year,cover_i,edition_count,isbn,publisher,language,"
                    + "number_of_pages_median,editions,editions.key,editions.title,editions.language,"
                    + "editions.cover_i,editions.isbn,editions.publisher,editions.publish_date";

    /**
     * Asks Open Library to pick, for each work, the French edition when one exists: the answer
     * is then "Le Nom du Vent" with its own cover instead of the English original.
     */
    private static final String PREFERRED_LANG = "fr";

    /**
     * Open Library indexes languages as MARC codes ({@code fre}, {@code eng}), while a client
     * naturally holds the ISO 639-1 code of the language it displays. Only the languages the
     * screen offers are mapped; anything else is passed through, which covers a caller that
     * already speaks MARC.
     */
    private static final Map<String, String> MARC_LANGUAGES = Map.of(
            "fr", "fre", "en", "eng", "ja", "jpn", "de", "ger", "es", "spa", "it", "ita");

    @Inject
    @RestClient
    OpenLibraryClient client;

    /** Absolute deadline of one call, whatever Open Library does with the socket. */
    @ConfigProperty(name = "librarius.catalog.provider.call-timeout", defaultValue = "12S")
    Duration callTimeout;

    @Override
    public String name() {
        return "openlibrary";
    }

    @Override
    public Kind kind() {
        return Kind.BOOK;
    }

    @Override
    public List<CatalogResult> search(CatalogQuery query, int limit) {
        String q = solrQuery(query);
        if (q.isEmpty()) {
            return List.of();
        }
        return fetch(() -> client.search(q, Math.min(limit, 40), FIELDS, null, PREFERRED_LANG),
                "search");
    }

    @Override
    public List<CatalogResult> upcoming(int limit) {
        return List.of();
    }

    @Override
    public boolean searchesByPrefix() {
        return true;
    }

    /**
     * An author's works, the most published first. Open Library's search answers works, not
     * editions, so each line is one book; ordered by edition count, the books an author is known
     * for lead instead of whatever matched the name best — "Dune" before a pamphlet.
     */
    @Override
    public List<CatalogResult> worksOfAuthor(String authorName, int limit) {
        if (authorName == null || authorName.isBlank()) {
            return List.of();
        }
        String q = solrQuery(new CatalogQuery(null, authorName, null, null, null, null));
        return fetch(() -> client.search(q, Math.min(limit, 40), FIELDS, "editions", PREFERRED_LANG),
                "author works");
    }

    /**
     * The other editions of a work, from {@code /works/{ref}/editions.json}. Each entry is one
     * printing — its own ISBN, publisher, language and cover — so the detail screen surfaces
     * the printings, and their covers with them, that no user of this instance ever entered.
     */
    @Override
    public List<CatalogResult> editionsOf(String workRef, int limit) {
        if (workRef == null || workRef.isBlank()) {
            return List.of();
        }
        try {
            OpenLibraryClient.EditionsResponse res =
                    client.editions(workRef.trim(), Math.min(limit, 40)).await().atMost(callTimeout);
            if (res == null || res.entries() == null) {
                return List.of();
            }
            return res.entries().stream()
                    .map(OpenLibraryProvider::toEdition)
                    .filter(Objects::nonNull)
                    .limit(limit)
                    .toList();
        } catch (Exception e) {
            Log.warnf("Open Library editions failed for %s: %s", workRef, e.getMessage());
            return List.of();
        }
    }

    /**
     * Runs one search call, and tries once more when it fails. Open Library regularly answers
     * an empty or non-JSON body under load, which surfaces here as an exception or a response
     * with no {@code docs}; a second attempt usually gets through, whereas giving up would show
     * the user "no result" for a book that exists. A parsed response with zero docs is a real
     * empty answer and is returned as is. Each attempt keeps its own {@code callTimeout}.
     */
    private List<CatalogResult> fetch(Supplier<Uni<OpenLibraryClient.SearchResponse>> call, String what) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                OpenLibraryClient.SearchResponse res = call.get().await().atMost(callTimeout);
                if (res != null && res.docs() != null) {
                    return res.docs().stream().map(OpenLibraryProvider::toResult).toList();
                }
                Log.warnf("Open Library %s answered no docs (attempt %d)", what, attempt);
            } catch (Exception e) {
                Log.warnf("Open Library %s failed (attempt %d): %s", what, attempt, e.getMessage());
            }
        }
        return List.of();
    }

    /**
     * Renders the criteria as the Solr query {@code /search.json} expects: free text stays
     * bare, every advanced criterion becomes a fielded term. Open Library therefore filters
     * on all of them itself — narrowing the answers here would cost a page of results to
     * throw most of it away.
     */
    static String solrQuery(CatalogQuery query) {
        StringBuilder q = new StringBuilder();
        if (query.text() != null) {
            q.append(query.prefixLastWord() ? prefixTitle(query.text()) : query.text());
        }
        append(q, "author", query.author());
        append(q, "publisher", query.publisher());
        append(q, "isbn", query.isbn());
        if (query.year() != null) {
            append(q, "first_publish_year", query.year().toString());
        }
        if (query.language() != null) {
            String language = query.language().toLowerCase();
            append(q, "language", MARC_LANGUAGES.getOrDefault(language, language));
        }
        return q.toString().trim();
    }

    /**
     * "le nom du ven" as {@code title:(le AND nom AND du AND ven*)}: every word on the title, the
     * last one truncated. A wildcard term is not analysed by Solr, hence the lower case; the
     * words are reduced to their letters and digits so no character the user typed can reach
     * the query syntax.
     */
    private static String prefixTitle(String text) {
        List<String> words = java.util.Arrays.stream(text.toLowerCase(java.util.Locale.ROOT)
                        .split("[^\\p{L}\\p{N}]+"))
                .filter(word -> !word.isEmpty())
                .toList();
        if (words.isEmpty()) {
            return "";
        }
        return "title:(" + String.join(" AND ", words) + "*)";
    }

    /**
     * Appends {@code field:"value"} to the query. The quotes keep a multi-word value in one
     * term, and the ones the user typed are dropped rather than escaped: a stray quote would
     * otherwise unbalance the query and Open Library would answer nothing at all.
     */
    private static void append(StringBuilder q, String field, String value) {
        if (value == null) {
            return;
        }
        String cleaned = value.replace("\"", " ").replace("\\", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) {
            return;
        }
        if (!q.isEmpty()) {
            q.append(' ');
        }
        q.append(field).append(":\"").append(cleaned).append('"');
    }

    private static CatalogResult toResult(OpenLibraryClient.Doc d) {
        String authors = d.authorName() == null ? null : String.join(", ", d.authorName());
        // The edition Open Library picked for lang=fr, when there is one. Each field falls back
        // to the work's own value independently: an edition record is often sparse.
        OpenLibraryClient.EditionDoc ed = d.editions() == null || d.editions().docs() == null
                || d.editions().docs().isEmpty() ? null : d.editions().docs().get(0);
        boolean editionTitle = ed != null && ed.title() != null && !ed.title().isBlank();
        String title = editionTitle ? ed.title() : d.title();
        // The language follows the title: a French title must not be labelled with the work's
        // first language, which is whatever Open Library lists first (often not French).
        String language = firstOf(editionTitle ? ed.language() : null);
        if (language == null) {
            language = firstOf(d.language());
        }
        // Never an /b/isbn/ URL here: Open Library 404s on most of them. A cover id is certain.
        Long coverId = ed != null && ed.coverId() != null ? ed.coverId() : d.coverId();
        String cover = coverId != null
                ? "https://covers.openlibrary.org/b/id/" + coverId + "-M.jpg"
                : null;
        String isbn13 = isbn13Of(ed == null ? null : ed.isbn());
        if (isbn13 == null) {
            isbn13 = isbn13Of(d.isbn());
        }
        String publisher = firstOf(ed == null ? null : ed.publisher());
        if (publisher == null) {
            publisher = firstOf(d.publisher());
        }
        VolumeParser.Parsed volume = VolumeParser.parse(title);
        // The work key ("/works/OL45804W") is what /works/{ref}/editions.json is keyed on: without
        // it a book picked off a search recorded no reference, and its detail screen could never
        // list the other printings Open Library holds.
        String ref = workKey(d.key());
        return new CatalogResult("BOOK", title, authors, d.firstPublishYear(), cover, null,
                isbn13, publisher, language, null, volume.seriesTitle(), volume.volumeNumber(),
                d.numberOfPagesMedian(), ref == null ? null : "openlibrary", ref)
                .withPopularity(d.editionCount());
    }

    private static String firstOf(List<String> values) {
        return values == null ? null
                : values.stream().filter(s -> s != null && !s.isBlank()).findFirst().orElse(null);
    }

    private static String isbn13Of(List<String> isbns) {
        return isbns == null ? null
                : isbns.stream().filter(s -> s != null && s.length() == 13).findFirst().orElse(null);
    }

    /**
     * Maps one edition record. A record with no ISBN and no cover carries nothing the section
     * can show or deduplicate on, so it is dropped rather than listed as an empty row.
     */
    private static CatalogResult toEdition(OpenLibraryClient.EditionEntry e) {
        String isbn13 = e.isbn13() == null ? null
                : e.isbn13().stream().filter(s -> s != null && s.length() == 13).findFirst().orElse(null);
        Long coverId = e.covers() == null ? null
                : e.covers().stream().filter(id -> id != null && id > 0).findFirst().orElse(null);
        String cover = coverId != null
                ? "https://covers.openlibrary.org/b/id/" + coverId + "-M.jpg"
                : null;
        if (isbn13 == null && cover == null) {
            return null;
        }
        String publisher = e.publishers() == null || e.publishers().isEmpty() ? null : e.publishers().get(0);
        String language = marcLanguage(e.languages());
        String ref = editionKey(e.key());
        return new CatalogResult("BOOK", null, null, null, cover, null, isbn13, publisher,
                language, null, "openlibrary", ref);
    }

    /** The MARC code of the first language, read out of a {@code /languages/fre} key. */
    private static String marcLanguage(List<OpenLibraryClient.LanguageRef> languages) {
        if (languages == null || languages.isEmpty() || languages.get(0) == null) {
            return null;
        }
        String key = languages.get(0).key();
        if (key == null) {
            return null;
        }
        int slash = key.lastIndexOf('/');
        String code = (slash >= 0 ? key.substring(slash + 1) : key).trim();
        return code.isEmpty() ? null : code;
    }

    /** The work id out of a {@code /works/OL…W} key, or null for anything else. */
    static String workKey(String key) {
        if (key == null || !key.startsWith("/works/")) {
            return null;
        }
        String id = key.substring("/works/".length()).trim();
        return id.matches("OL\\d+W") ? id : null;
    }

    /** The edition id out of a {@code /books/OL…M} key, the part that identifies the record. */
    private static String editionKey(String key) {
        if (key == null) {
            return null;
        }
        int slash = key.lastIndexOf('/');
        String id = (slash >= 0 ? key.substring(slash + 1) : key).trim();
        return id.isEmpty() ? null : id;
    }
}
