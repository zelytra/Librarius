package zelytra.librarius.catalog.provider;

/**
 * The User-Agent every catalog client sends. Open Library asks API consumers to identify
 * themselves and serves an unidentified client a stricter rate limit; the other catalogues
 * get the same courtesy. The contact is the project page, not anybody's address.
 */
final class CatalogUserAgent {

    static final String VALUE = "Librarius/1.0 (+https://github.com/zelytra/Librarius)";

    private CatalogUserAgent() {
    }
}
