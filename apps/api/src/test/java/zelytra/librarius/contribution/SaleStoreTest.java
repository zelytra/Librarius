package zelytra.librarius.contribution;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The closed list of stores a sale link may point at (#619), recognised by host. */
class SaleStoreTest {

    @Test
    void recognisesEveryStoreOnItsOwnHostAndItsSubdomains() {
        assertEquals(Optional.of(SaleStore.AMAZON), SaleStore.of("https://www.amazon.fr/dp/2070612880"));
        assertEquals(Optional.of(SaleStore.AMAZON), SaleStore.of("https://amazon.fr/dp/2070612880"));
        assertEquals(Optional.of(SaleStore.FNAC), SaleStore.of("https://livre.fnac.com/a123/x"));
        assertEquals(Optional.of(SaleStore.CULTURA), SaleStore.of("https://www.cultura.com/p-x.html"));
        assertEquals(Optional.of(SaleStore.DECITRE), SaleStore.of("https://www.decitre.fr/livres/x.html"));
        assertEquals(Optional.of(SaleStore.LECLERC), SaleStore.of("https://www.e.leclerc/fp/x"));
        assertEquals(Optional.of(SaleStore.FURET_DU_NORD), SaleStore.of("https://www.furet.com/livres/x.html"));
        assertEquals(Optional.of(SaleStore.GIBERT), SaleStore.of("https://www.gibert.com/x.html"));
        assertEquals(Optional.of(SaleStore.RAKUTEN), SaleStore.of("https://fr.shopping.rakuten.com/offer/buy/1"));
        assertEquals(Optional.of(SaleStore.FNAC), SaleStore.of("https://WWW.FNAC.COM/a1"));
    }

    /** #628: a link copied from an ad carries tracking parameters with characters a URI refuses. */
    private static final String FNAC_AD = "https://www.fnac.com/a22917644/Becky-Chambers-Histoires-de-moine-et-de-robot"
            + "?oref=00000000-0000-0000-0000-000000000000&storecode=2201&Origin=SEA_GOOGLE_PLA_BOOKS"
            + "&esl-k=sem-google|nx|c|m|k|p|t|dc|a20111491090|g20111491090&gclsrc=aw.ds&gad_source=1"
            + "&gad_campaignid=19663887777&gclid=CjwKCAiA5Y_YBhBbEiwAK5Dl7XQ8XEU8qempIAJdAc1isYiocFItTpP21rxQYiqBx2Ucz0qdN19P9xoCQpIQAvD_BwE";

    @Test
    void aLinkIsCleanedToThePageAddress() {
        assertEquals(Optional.of("https://www.fnac.com/a22917644/Becky-Chambers-Histoires-de-moine-et-de-robot"),
                SaleStore.clean(FNAC_AD));
        assertEquals(Optional.of(SaleStore.FNAC), SaleStore.of(FNAC_AD));
        assertEquals(Optional.of("https://www.amazon.fr/dp/2070612880"), SaleStore.clean(" https://WWW.Amazon.fr/dp/2070612880#reviews "));
        assertEquals(Optional.of("https://www.decitre.fr/"), SaleStore.clean("https://www.decitre.fr?x=1"));
        // A tracking parameter cannot carry an unknown host past the check.
        assertEquals(Optional.empty(), SaleStore.clean("https://evil.test/?u=https://www.fnac.com/a1"));
        assertEquals(Optional.empty(), SaleStore.clean("https://evil.test#www.fnac.com"));
    }

    @Test
    void refusesAnythingElse() {
        for (String url : new String[] {
                "http://www.fnac.com/a1", "https://www.fnac.com.evil.test/a1", "https://evilfnac.com/a1",
                "https://example.com", "ftp://www.fnac.com/a1", "https://user@www.fnac.com/a1",
                "https://www.fnac.com:8443/a1", "nonsense", "", "https://", "https://rakuten.com/x"}) {
            assertEquals(Optional.empty(), SaleStore.of(url), url);
        }
        assertEquals(Optional.empty(), SaleStore.of(null));
    }
}
