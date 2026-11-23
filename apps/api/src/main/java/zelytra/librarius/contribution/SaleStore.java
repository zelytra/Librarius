package zelytra.librarius.contribution;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The stores a sale link may point at (#619), recognised by the host of the URL.
 *
 * <p>The list is closed on purpose: it is what the automatic check (#620) fetches, so a member
 * can never make the server request an arbitrary host (no SSRF), and it keeps the catalog free
 * of affiliate or spam links. A host matches its store's domain exactly or as a subdomain —
 * {@code livre.fnac.com} is Fnac, {@code fnac.com.evil.test} and {@code evilfnac.com} are not.
 */
public enum SaleStore {
    AMAZON("amazon.fr"),
    FNAC("fnac.com"),
    CULTURA("cultura.com"),
    DECITRE("decitre.fr"),
    LECLERC("e.leclerc"),
    FURET_DU_NORD("furet.com"),
    GIBERT("gibert.com"),
    RAKUTEN("fr.shopping.rakuten.com");

    private final List<String> domains;

    SaleStore(String... domains) {
        this.domains = List.of(domains);
    }

    /** The store {@code url} belongs to — empty unless it is a plain https URL on a known host. */
    public static Optional<SaleStore> of(String url) {
        return clean(url).map(SaleStore::storeOf);
    }

    /**
     * The page address alone, {@code https://host/path}, of a link on a known store (#628): the
     * query string and the fragment are dropped — they carry ad tracking, often with characters a
     * URI refuses, and never which book the page sells — and the host is lower-cased. Empty for
     * anything that is not a plain https URL on a known host.
     */
    public static Optional<String> clean(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        String page = url.trim();
        int cut = indexOfAny(page, '?', '#');
        if (cut >= 0) {
            page = page.substring(0, cut);
        }
        URI uri;
        try {
            uri = new URI(page);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                || uri.getPort() != -1 || uri.getHost() == null) {
            return Optional.empty();
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (storeOf("https://" + host) == null) {
            return Optional.empty();
        }
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return Optional.of("https://" + host + path);
    }

    /** The store of a cleaned URL's host, or null. */
    private static SaleStore storeOf(String cleaned) {
        String host = URI.create(cleaned).getHost();
        return Arrays.stream(values())
                .filter(store -> store.domains.stream().anyMatch(d -> host.equals(d) || host.endsWith("." + d)))
                .findFirst().orElse(null);
    }

    private static int indexOfAny(String value, char... chars) {
        int first = -1;
        for (char c : chars) {
            int at = value.indexOf(c);
            if (at >= 0 && (first < 0 || at < first)) {
                first = at;
            }
        }
        return first;
    }
}
