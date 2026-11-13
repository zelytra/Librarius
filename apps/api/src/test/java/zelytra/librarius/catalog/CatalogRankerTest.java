package zelytra.librarius.catalog;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each step of the catalog ranking on hand-made results (#408); the real pages are in CatalogRelevanceTest. */
class CatalogRankerTest {

    /** A result built field by field, so each test states only what matters to it. */
    private static final class R {
        String kind = "BOOK";
        String title;
        String authors;
        String cover;
        String synopsis;
        String isbn;
        String language;
        String series;
        Integer volume;
        Integer pages;
        String provider = "bnf";
        String ref = "ref";
        Integer popularity;

        R(String title, String authors) {
            this.title = title;
            this.authors = authors;
        }

        R kind(String value) { kind = value; return this; }
        R cover() { cover = "/api/covers/bnf/cb1"; return this; }
        R isbn(String value) { isbn = value; return this; }
        R french() { language = "fre"; return this; }
        R language(String value) { language = value; return this; }
        R series(String name, int number) { series = name; volume = number; return this; }
        R pages(int value) { pages = value; return this; }
        R synopsis(String value) { synopsis = value; return this; }
        R from(String value, String id) { provider = value; ref = id; return this; }
        R popular(int value) { popularity = value; return this; }

        CatalogResult build() {
            return new CatalogResult(kind, title, authors, null, cover, synopsis, isbn, null, language,
                    null, series, volume, pages, provider, ref, popularity);
        }
    }

    private static R r(String title, String authors) {
        return new R(title, authors);
    }

    private static List<CatalogResult> rank(String text, R... results) {
        List<CatalogResult> built = new ArrayList<>();
        for (R result : results) {
            built.add(result.build());
        }
        return CatalogRanker.rank(text, built, false).results();
    }

    private static List<String> titles(List<CatalogResult> results) {
        return results.stream().map(CatalogResult::title).toList();
    }

    // --- grouping -------------------------------------------------------------------------

    @Test
    void collapsesTheEditionsOfOneWorkOntoTheFrenchOneWithACover() {
        List<CatalogResult> results = rank("le nom du vent",
                r("The Name of the Wind", "Patrick Rothfuss").language("eng").cover().from("openlibrary", "OL1W").popular(76),
                r("Le nom du vent", "Patrick Rothfuss").french().isbn("9782352940000").pages(662),
                r("Le Nom du Vent", "Patrick Rothfuss").french().cover().from("openlibrary", "OL8479867W").popular(76),
                r("Le nom du vent", "Patrick Rothfuss").french().synopsis("Kvothe…"));

        assertEquals(2, results.size(), titles(results).toString());
        CatalogResult novel = results.get(0);
        assertEquals("Le Nom du Vent", novel.title());
        assertEquals("OL8479867W", novel.providerRef());
        // What the survivor lacked, the editions it absorbed had.
        assertEquals(Integer.valueOf(662), novel.pageCount());
        assertEquals("9782352940000", novel.isbn13());
        assertEquals("Kvothe…", novel.synopsis());
        assertEquals(Integer.valueOf(76), novel.popularity());
    }

    @Test
    void groupsAuthorsWhateverTheirInitialsLookLike() {
        List<CatalogResult> results = rank("harry potter",
                r("Harry Potter", "J. K Rowling"),
                r("Harry Potter", "J.K. Rowling"),
                r("Harry Potter", "J. K. Rowling"));

        assertEquals(1, results.size());
    }

    @Test
    void keepsSpecialAndHouseEditionsApart() {
        List<CatalogResult> results = rank("harry potter a l ecole des sorciers",
                r("Harry Potter à l'école des sorciers", "J. K. Rowling").french(),
                r("Harry Potter à l'école des sorciers : Serdaigle", "J. K. Rowling").french(),
                r("Harry Potter à l'école des sorciers (Éd. du 20e anniversaire)", "J. K. Rowling").french(),
                r("Harry Potter à l'école des sorciers (French Edition)", "J. K. Rowling").french());

        // The plain language-edition statement is the same book; a house or anniversary edition is not.
        assertEquals(3, results.size(), titles(results).toString());
    }

    @Test
    void groupsTwoEditionsSharingAnIsbn() {
        List<CatalogResult> results = rank("horde",
                r("La horde du contrevent", "Alain Damasio").isbn("9782070342266"),
                r("La Horde du Contrevent : roman", "Damasio").isbn("9782070342266"));

        assertEquals(1, results.size());
    }

    @Test
    void aGroupIsAMangaWhenAnyOfItsEditionsIs() {
        List<CatalogResult> results = rank("one piece 1",
                r("One Piece. 1", "Eiichirō Oda").series("One Piece", 1),
                r("One Piece - Tome 1", "Eiichirō Oda").series("One Piece", 1).kind("COMIC"),
                r("ONE PIECE Tome 1", "Eiichirō Oda").series("ONE PIECE", 1).kind("MANGA"));

        assertEquals(1, results.size());
        assertEquals("MANGA", results.get(0).kind());
    }

    // --- volumes --------------------------------------------------------------------------

    @Test
    void emitsTheVolumesInOrderBehindTheSeries() {
        List<CatalogResult> results = rank("blue lock",
                r("Blue Lock - Tome 3", "Yūsuke Nomura").series("Blue Lock", 3).cover().french(),
                r("Blue Lock - Tome 1", "Yūsuke Nomura").series("Blue Lock", 1),
                r("Blue Lock", "Muneyuki Kaneshiro").from("anilist", "137").kind("MANGA"),
                r("Blue Lock - Tome 2", "Yūsuke Nomura").series("Blue Lock", 2));

        assertEquals(List.of("Blue Lock", "Blue Lock - Tome 1", "Blue Lock - Tome 2", "Blue Lock - Tome 3"),
                titles(results));
    }

    @Test
    void putsTheAskedVolumeFirstThenTheSeriesThenTheOthers() {
        List<CatalogResult> results = rank("blue lock 2",
                r("Blue Lock - Tome 3", "Yūsuke Nomura").series("Blue Lock", 3),
                r("Blue Lock", "Muneyuki Kaneshiro").from("anilist", "137").kind("MANGA"),
                r("Blue Lock - Tome 1", "Yūsuke Nomura").series("Blue Lock", 1),
                r("Blue Lock - Tome 2", "Yūsuke Nomura").series("Blue Lock", 2));

        assertEquals(List.of("Blue Lock - Tome 2", "Blue Lock", "Blue Lock - Tome 1", "Blue Lock - Tome 3"),
                titles(results));
    }

    @Test
    void readsABareNumberAfterAKnownSeriesAsAVolume() {
        List<CatalogResult> results = rank("blue lock",
                r("Blue Lock 2", "Muneyuki Kaneshiro").from("openlibrary", "OL2W"),
                r("Blue Lock - Tome 31", "Yūsuke Nomura").series("Blue Lock", 31),
                r("Blue Lock 1", "Muneyuki Kaneshiro").from("openlibrary", "OL1W"));

        assertEquals(List.of("Blue Lock 1", "Blue Lock 2", "Blue Lock - Tome 31"), titles(results));
        assertEquals(Integer.valueOf(1), results.get(0).volumeNumber());
        assertEquals("Blue Lock", results.get(0).seriesTitle());
    }

    @Test
    void neverReadsABareNumberAsAVolumeOfNoSeries() {
        List<CatalogResult> results = rank("fahrenheit",
                r("Fahrenheit 451", "Ray Bradbury"));

        assertEquals(null, results.get(0).volumeNumber());
    }

    // --- derivatives ----------------------------------------------------------------------

    @Test
    void demotesColouringBooksAndAgendasBelowTheGenuineMatches() {
        List<CatalogResult> results = rank("demon slayer",
                r("Demon Slayer", "sara coloring").cover().french(),
                r("Demon Slayer Coloring Book", "Anime Fans").cover().french(),
                r("Demon Slayer : agenda scolaire", "Panini").cover().french(),
                r("Demon Slayer, l'histoire du manga", "Koyoharu Gotōge"));

        assertEquals("Demon Slayer, l'histoire du manga", results.get(0).title(), titles(results).toString());
    }

    @Test
    void demotesTheStudyGuidesOfTheBestRankedWork() {
        List<CatalogResult> results = rank("l'étranger camus",
                r("L' Etranger, Camus", "Pierre Louis Rey").french(),
                r("L'étranger : fiche de lecture", "Collectif").french().cover(),
                r("Albert Camus et L'étranger", "Pierre-Georges Castex").french().cover(),
                r("L'étranger Par Albert Camus", "Albert Camus").french().cover().popular(470));

        assertEquals("L'étranger", results.get(0).title());
        assertEquals("Albert Camus", results.get(0).authors());
    }

    @Test
    void demotesSpinOffsUnlessTheQueryNamesThem() {
        R party = r("One piece party - Tome 1", "Ei Andō").series("One piece party", 1).cover().french();
        R series = r("One Piece - Tome 1", "Eiichirō Oda").series("One Piece", 1);

        assertEquals("One Piece - Tome 1", rank("one piece", party, series).get(0).title());
        assertEquals("One piece party - Tome 1", rank("one piece party", party, series).get(0).title());
    }

    @Test
    void demotesBoxSetsAndExhibitionCatalogues() {
        List<CatalogResult> results = rank("harry potter",
                r("Coffret Harry Potter", "J. K. Rowling").cover().french(),
                r("Harry Potter, exposition", "BnF").cover().french(),
                r("Harry Potter et la Chambre des secrets", "J. K. Rowling"));

        assertEquals("Harry Potter et la Chambre des secrets", results.get(0).title());
    }

    // --- authors and popularity -----------------------------------------------------------

    @Test
    void ranksTheWorksOfThePersonTypedAboveTheBooksAboutThem() {
        List<CatalogResult> results = rank("victor hugo",
                r("Victor Hugo", "Graham Robb").cover().language("eng").popular(10),
                r("Cahiers Victor Hugo", null),
                r("Hernani", "Victor Hugo").french().cover().popular(116),
                r("Les misérables", "Victor Hugo").french().cover().popular(526));

        assertEquals(List.of("Les misérables", "Hernani"), titles(results).subList(0, 2));
    }

    @Test
    void readsAnAuthorTypedAfterTheTitleAsAnExactMatch() {
        List<CatalogResult> results = rank("la nuit des temps barjavel",
                r("La nuit des temps et autres récits", "Anonyme").french().cover(),
                r("La nuit des temps", "René Barjavel").french());

        assertEquals("René Barjavel", results.get(0).authors());
    }

    @Test
    void liftsAVeryPopularWorkLevelWithAnExactTitle() {
        List<CatalogResult> results = rank("harry potter",
                r("Harry Potter", "Joan Moloney").cover().language("eng").popular(1),
                r("Harry Potter à l'école des sorciers", "J. K. Rowling").french().cover().popular(400));

        assertEquals("Harry Potter à l'école des sorciers", results.get(0).title());
    }

    @Test
    void neverLiftsAPopularWorkPastAnExactTitleTwoTiersAbove() {
        List<CatalogResult> results = rank("dune",
                r("Mémoires de la dune et de la plage", "X. Auteur").french().cover().popular(400),
                r("Dune", "Frank Herbert").french());

        assertEquals("Dune", results.get(0).title());
    }

    @Test
    void putsFrenchThenCoveredEditionsFirstInsideATier() {
        List<CatalogResult> results = rank("dune",
                r("Dune", "Brian Herbert").language("eng"),
                r("Dune", "Valentine Oncins").language("cat").cover(),
                r("Dune", "Frank Herbert").french().cover());

        assertEquals("Frank Herbert", results.get(0).authors());
    }

    @Test
    void sinksAResultWithoutAnyAuthorInsideItsTier() {
        List<CatalogResult> results = rank("le seigneur des anneaux",
                r("Le seigneur des anneaux", null).language("und").cover(),
                r("Le Seigneur des anneaux", "J. R. R. Tolkien").french());

        assertEquals("J. R. R. Tolkien", results.get(0).authors());
    }

    // --- kinds ----------------------------------------------------------------------------

    @Test
    void filesTheVolumesOfAnAniListSeriesAsMangas() {
        List<CatalogResult> results = rank("demon slayer",
                r("Demon Slayer: Kimetsu no Yaiba", "Koyoharu Gotouge").from("anilist", "87216").kind("MANGA"),
                r("Demon slayer - Tome 4", "Koyoharu Gotōge").series("Demon slayer", 4),
                r("Demon Slayer 03", "吾峠呼世晴").from("openlibrary", "OL3W"),
                r("Demon Slayer T02", "吾峠呼世晴").series("Demon Slayer", 2).kind("COMIC"));

        assertTrue(results.stream().allMatch(r -> "MANGA".equals(r.kind())), results.toString());
        assertEquals(List.of(2, 3, 4), results.stream().skip(1).map(CatalogResult::volumeNumber).toList());
    }

    @Test
    void leavesKindsAloneWithoutAnAniListSeries() {
        List<CatalogResult> results = rank("demon slayer",
                r("Demon slayer - Tome 4", "Koyoharu Gotōge").series("Demon slayer", 4),
                r("Demon slayer - Tome 3", "Koyoharu Gotōge").series("Demon slayer", 3).kind("MANGA"));

        assertEquals(List.of("MANGA", "BOOK"), results.stream().map(CatalogResult::kind).toList());
    }

    // --- display clean-up -----------------------------------------------------------------

    @Test
    void cutsTheCreditOffATitleOnlyWhenItIsTheCreditedAuthor() {
        assertEquals("L'étranger", CatalogRanker.cleanUp(r("L'étranger Par Albert Camus", "Albert Camus").build()).title());
        assertEquals("Stand by Me", CatalogRanker.cleanUp(r("Stand by Me", "Stephen King").build()).title());
    }

    @Test
    void tidiesElisionsCapitalsWhitespaceAndInitials() {
        CatalogResult cleaned = CatalogRanker.cleanUp(r("L' Etranger", "J. K  Rowling").build());
        assertEquals("L'Etranger", cleaned.title());
        assertEquals("J. K. Rowling", cleaned.authors());

        assertEquals("Les miserables", CatalogRanker.cleanUp(r("LES MISERABLES", "Victor Hugo").build()).title());
        assertEquals("BD", CatalogRanker.cleanUp(r("BD", null).build()).title());
        assertEquals("ONE PIECE Tome 1", CatalogRanker.cleanUp(r("ONE PIECE Tome 1", null).build()).title());
        // A name with no stray initial is left exactly as the catalogue wrote it.
        assertEquals("Trans.Norman Denny Hugo",
                CatalogRanker.cleanUp(r("Les Miserables", "Trans.Norman Denny Hugo").build()).authors());
    }

    // --- prefix retry ---------------------------------------------------------------------

    @Test
    void anIncompleteLastWordMatchesOnlyInAPrefixAwareRanking() {
        List<CatalogResult> candidates = List.of(
                r("Rapport sur les ventes", "Comité").french().build(),
                r("Le nom du vent", "Patrick Rothfuss").french().build());

        CatalogRanker.Ranking strict = CatalogRanker.rank("le nom du ven", candidates, false);
        CatalogRanker.Ranking prefixed = CatalogRanker.rank("le nom du ven", candidates, true);

        assertTrue(strict.weak());
        assertFalse(prefixed.weak());
        assertEquals("Le nom du vent", prefixed.results().get(0).title());
    }

    @Test
    void retriesOnlyAPlainSearchWhoseLastWordCouldBeUnfinished() {
        assertTrue(CatalogRanker.prefixRetryApplies(CatalogQuery.of("le nom du ven")));
        assertFalse(CatalogRanker.prefixRetryApplies(CatalogQuery.of("dun")));
        assertFalse(CatalogRanker.prefixRetryApplies(CatalogQuery.of("le nom du v")));
        assertFalse(CatalogRanker.prefixRetryApplies(CatalogQuery.of("one piece 12")));
        assertFalse(CatalogRanker.prefixRetryApplies(
                new CatalogQuery("le nom du ven", "Rothfuss", null, null, null, null)));
    }
}
