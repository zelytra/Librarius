package zelytra.librarius.catalog;

import java.util.stream.Stream;

/**
 * Criteria of a catalog search.
 *
 * <p>Everything is optional and everything combines: a bare {@code text} is the keyword
 * search the screen has always offered, the rest is what the advanced form adds. Providers
 * honour what their own API exposes and ignore the rest — Open Library indexes all of it,
 * AniList knows nothing of a publisher or a language.
 *
 * <p>Blank values are folded to {@code null} at construction, so a provider only ever has to
 * test for {@code null}: an empty text field submitted by a browser and an absent one are
 * the same search, and would otherwise produce two cache entries for one answer.
 */
public record CatalogQuery(String text, String author, Integer year, String language,
        String publisher, String isbn) {

    public CatalogQuery {
        text = trimToNull(text);
        author = trimToNull(author);
        language = trimToNull(language);
        publisher = trimToNull(publisher);
        isbn = trimToNull(isbn);
    }

    /** Keyword-only search, the shape the plain search field produces. */
    public static CatalogQuery of(String text) {
        return new CatalogQuery(text, null, null, null, null, null);
    }

    /** No criterion at all: nothing to ask a provider, and nothing to charge to the quota. */
    public boolean isEmpty() {
        return Stream.of(text, author, language, publisher, isbn).allMatch(v -> v == null)
                && year == null;
    }

    /**
     * Canonical form of the criteria, used as the cache key. Two searches differing by a
     * single field must not share an entry, so every field takes part, separator included.
     */
    public String cacheKey() {
        return String.join("|", nullToEmpty(text), nullToEmpty(author),
                year == null ? "" : year.toString(),
                nullToEmpty(language), nullToEmpty(publisher), nullToEmpty(isbn));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
