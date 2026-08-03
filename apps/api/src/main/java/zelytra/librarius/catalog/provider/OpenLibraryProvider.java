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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Book catalog provider backed by Open Library (no API key needed). */
@ApplicationScoped
public class OpenLibraryProvider implements CatalogProvider {

    private static final String FIELDS =
            "title,author_name,first_publish_year,cover_i,isbn,publisher,language,"
                    + "number_of_pages_median";

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
        try {
            OpenLibraryClient.SearchResponse res =
                    client.search(q, Math.min(limit, 40), FIELDS).await().atMost(callTimeout);
            if (res == null || res.docs() == null) {
                return List.of();
            }
            return res.docs().stream().map(OpenLibraryProvider::toResult).toList();
        } catch (Exception e) {
            Log.warnf("Open Library search failed: %s", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<CatalogResult> upcoming(int limit) {
        return List.of();
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
     * Renders the criteria as the Solr query {@code /search.json} expects: free text stays
     * bare, every advanced criterion becomes a fielded term. Open Library therefore filters
     * on all of them itself — narrowing the answers here would cost a page of results to
     * throw most of it away.
     */
    private static String solrQuery(CatalogQuery query) {
        StringBuilder q = new StringBuilder();
        if (query.text() != null) {
            q.append(query.text());
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
        String cover = d.coverId() != null
                ? "https://covers.openlibrary.org/b/id/" + d.coverId() + "-M.jpg"
                : null;
        String isbn13 = d.isbn() == null ? null
                : d.isbn().stream().filter(s -> s != null && s.length() == 13).findFirst().orElse(null);
        String publisher = d.publisher() == null || d.publisher().isEmpty() ? null : d.publisher().get(0);
        String language = d.language() == null || d.language().isEmpty() ? null : d.language().get(0);
        VolumeParser.Parsed volume = VolumeParser.parse(d.title());
        return new CatalogResult("BOOK", d.title(), authors, d.firstPublishYear(), cover, null,
                isbn13, publisher, language, null, volume.seriesTitle(), volume.volumeNumber(),
                d.numberOfPagesMedian(), "openlibrary", null);
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
