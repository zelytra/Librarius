package zelytra.librarius.catalog.provider;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import zelytra.librarius.catalog.provider.BnfProvider.ListPrice;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The publisher's price read from UNIMARC 010$d (#421). The fixture is a real BnF answer for
 * ISBN 9782723492478, whose record says {@code 14,95 EUR}.
 */
class BnfPriceTest {

    private static String fixture() throws Exception {
        try (InputStream in = BnfPriceTest.class.getResourceAsStream("/catalog/bnf/price-isbn.xml")) {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theRecordedPriceIsRead() throws Exception {
        Optional<ListPrice> price = BnfProvider.priceIn(fixture(), "9782723492478");
        assertTrue(price.isPresent());
        assertEquals(new BigDecimal("14.95"), price.get().amount());
        assertEquals("EUR", price.get().currency());
    }

    @Test
    void everyWayTheBnfWritesAPriceIsUnderstood() {
        assertEquals(new BigDecimal("14.95"), BnfProvider.parsePrice("14,95 EUR").amount());
        assertEquals(new BigDecimal("6.90"), BnfProvider.parsePrice("6.90 EUR").amount());
        assertEquals(new BigDecimal("15.00"), BnfProvider.parsePrice("Br. : 15 €").amount());
        assertEquals(new BigDecimal("29.90"), BnfProvider.parsePrice("29,90 euros").amount());
        assertNull(BnfProvider.parsePrice("Gratuit"));
        assertNull(BnfProvider.parsePrice("12 USD"));
        assertNull(BnfProvider.parsePrice(null));
    }

    @Test
    void aLookupGoesThroughTheIsbnIndexAndNeverThrows() throws Exception {
        String xml = fixture();
        BnfProvider provider = new BnfProvider();
        StringBuilder asked = new StringBuilder();
        provider.client = (version, operation, query, schema, max) -> {
            asked.append(query);
            return Uni.createFrom().item(xml);
        };
        provider.callTimeout = Duration.ofSeconds(2);
        assertEquals(new BigDecimal("14.95"), provider.priceByIsbn("9782723492478").orElseThrow().amount());
        assertEquals("bib.isbn all \"9782723492478\"", asked.toString());

        provider.client = (version, operation, query, schema, max) ->
                Uni.createFrom().failure(new RuntimeException("down"));
        assertTrue(provider.priceByIsbn("9782723492478").isEmpty());
    }

    /** #429: the BnF counts the volumes of a series it records. */
    @Test
    void countsTheVolumesOfASeriesFromItsRecords() throws Exception {
        String xml;
        try (InputStream in = BnfPriceTest.class.getResourceAsStream("/catalog/bnf/blacksad.all.xml")) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        java.util.List<zelytra.librarius.catalog.CatalogResult> parsed = BnfProvider.parse(xml);
        String series = parsed.stream().map(zelytra.librarius.catalog.CatalogResult::seriesTitle)
                .filter(java.util.Objects::nonNull).findFirst().orElseThrow();
        int highest = parsed.stream()
                .filter(r -> series.equals(r.seriesTitle()) && r.volumeNumber() != null)
                .mapToInt(zelytra.librarius.catalog.CatalogResult::volumeNumber).max().orElseThrow();

        BnfProvider provider = new BnfProvider();
        provider.client = (version, operation, query, schema, max) -> Uni.createFrom().item(xml);
        provider.callTimeout = Duration.ofSeconds(2);

        assertEquals(java.util.OptionalInt.of(highest), provider.seriesVolumes(series));
        assertTrue(provider.seriesVolumes("Une série que la BnF ignore").isEmpty());
    }
}
