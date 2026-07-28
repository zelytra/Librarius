package zelytra.librarius.catalog.provider;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import zelytra.librarius.catalog.CatalogProvider;
import zelytra.librarius.catalog.CatalogQuery;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.domain.Kind;

import java.util.List;
import java.util.Map;

/** Book catalog provider backed by Open Library (no API key needed). */
@ApplicationScoped
public class OpenLibraryProvider implements CatalogProvider {

    private static final String FIELDS =
            "title,author_name,first_publish_year,cover_i,isbn,publisher,language";

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
            OpenLibraryClient.SearchResponse res = client.search(q, Math.min(limit, 40), FIELDS);
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
        return new CatalogResult("BOOK", d.title(), authors, d.firstPublishYear(), cover, null,
                isbn13, publisher, language, null, "openlibrary", null);
    }
}
