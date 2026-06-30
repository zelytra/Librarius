package zelytra.librarius.catalog.provider;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import zelytra.librarius.catalog.CatalogProvider;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.domain.Kind;

import java.util.List;

/** Fournisseur de catalogue livre adossé à Open Library (sans clé API). */
@ApplicationScoped
public class OpenLibraryProvider implements CatalogProvider {

    private static final String FIELDS =
            "title,author_name,first_publish_year,cover_i,isbn,publisher,language";

    @Inject
    @RestClient
    OpenLibraryClient client;

    @Override
    public Kind kind() {
        return Kind.BOOK;
    }

    @Override
    public List<CatalogResult> search(String query, int limit) {
        try {
            OpenLibraryClient.SearchResponse res = client.search(query, Math.min(limit, 40), FIELDS);
            if (res == null || res.docs() == null) {
                return List.of();
            }
            return res.docs().stream().map(OpenLibraryProvider::toResult).toList();
        } catch (Exception e) {
            Log.warnf("Recherche Open Library échouée : %s", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<CatalogResult> upcoming(int limit) {
        return List.of();
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
