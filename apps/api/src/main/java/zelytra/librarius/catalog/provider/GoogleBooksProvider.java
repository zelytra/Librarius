package zelytra.librarius.catalog.provider;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import zelytra.librarius.catalog.CatalogProvider;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.domain.Kind;

import java.util.List;
import java.util.Optional;

/** Fournisseur de catalogue livre adossé à Google Books. */
@ApplicationScoped
public class GoogleBooksProvider implements CatalogProvider {

    @Inject
    @RestClient
    GoogleBooksClient client;

    @ConfigProperty(name = "librarius.googlebooks.api-key")
    Optional<String> apiKey;

    @Override
    public Kind kind() {
        return Kind.BOOK;
    }

    @Override
    public List<CatalogResult> search(String query, int limit) {
        try {
            String key = apiKey.filter(k -> !k.isBlank()).orElse(null);
            GoogleBooksClient.Volumes res = client.search(query, Math.min(limit, 40), "fr", key);
            if (res == null || res.items() == null) {
                return List.of();
            }
            return res.items().stream().map(this::toResult).toList();
        } catch (Exception e) {
            Log.warnf("Recherche Google Books échouée : %s", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<CatalogResult> upcoming(int limit) {
        // Google Books n'expose pas de calendrier fiable de sorties à venir.
        return List.of();
    }

    private CatalogResult toResult(GoogleBooksClient.Item item) {
        GoogleBooksClient.VolumeInfo v = item.volumeInfo();
        if (v == null) {
            return new CatalogResult("BOOK", null, null, null, null, null, null, null, null, null,
                    "googlebooks", item.id());
        }
        String authors = v.authors() == null ? null : String.join(", ", v.authors());
        String cover = v.imageLinks() != null ? v.imageLinks().thumbnail() : null;
        return new CatalogResult("BOOK", v.title(), authors, parseYear(v.publishedDate()), cover,
                v.description(), isbn13(v), v.publisher(), v.language(), null, "googlebooks",
                item.id());
    }

    private static String isbn13(GoogleBooksClient.VolumeInfo v) {
        if (v.industryIdentifiers() == null) {
            return null;
        }
        return v.industryIdentifiers().stream()
                .filter(id -> "ISBN_13".equals(id.type()))
                .map(GoogleBooksClient.IndustryId::identifier)
                .findFirst()
                .orElse(null);
    }

    private static Integer parseYear(String publishedDate) {
        if (publishedDate == null || publishedDate.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(publishedDate.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
