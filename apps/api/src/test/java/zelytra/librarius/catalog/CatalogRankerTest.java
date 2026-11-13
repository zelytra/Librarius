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

        // "The Name of the Wind" matches nothing of the query: once the novel is found, it goes.
        assertEquals(1, results.size(), titles(results).toString());
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
    void aComicAndAMangaRecordOfOneVolumeAreOneManga() {
        List<CatalogResult> results = rank("one piece 1",
                r("One Piece - Tome 1", "Eiichirō Oda").series("One Piece", 1).kind("COMIC"),
                r("ONE PIECE Tome 1", "Eiichirō Oda").series("ONE PIECE", 1).kind("MANGA"));

        assertEquals(1, results.size());
        assertEquals("MANGA", results.get(0).kind());
    }

    @Test
    void neverMergesANovelWithItsComicAdaptation() {
        List<CatalogResult> results = rank("les miserables",
                r("Les misérables", "Victor Hugo").french().cover().from("openlibrary", "OL1063588W").popular(526),
                r("Les misérables", "Victor Hugo").french().isbn("9782080712325"),
                r("Les misérables", "Victor Hugo, Kim Jong-hyun").french().kind("COMIC").cover());

        assertEquals(List.of("BOOK", "COMIC"), results.stream().map(CatalogResult::kind).toList());
        assertEquals(Integer.valueOf(526), results.get(0).popularity());
    }

    @Test
    void filesAnOpenLibraryBookWithTheComicWhenNoNovelIsOnThePage() {
        // Open Library calls every album a book: its "Blacksad" is the BnF's comic.
        List<CatalogResult> results = rank("blacksad",
                r("Blacksad", "Juan Díaz Canales").from("openlibrary", "OL1W").cover(),
                r("Blacksad", "Juan Díaz Canales").french().kind("COMIC"));

        assertEquals(1, results.size());
        assertEquals("COMIC", results.get(0).kind());
    }

    @Test
    void keepsTheSpinOffVolumeOfAnotherArtistApart() {
        List<CatalogResult> results = rank("blue lock",
                r("Blue Lock - Tome 8", "Muneyuki Kaneshiro, Yūsuke Nomura").series("Blue Lock", 8).kind("MANGA"),
                r("Blue lock - Tome 8", "Kōta Sannomiya").series("Blue lock", 8).kind("MANGA"),
                r("Blue Lock 8", "金城宗幸").from("openlibrary", "OL8W").series("Blue Lock", 8));

        // The kanji credit cannot be compared, so it joins; the other artist's volume does not.
        assertEquals(2, results.size(), titles(results).toString());
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
    void demotesBooksQuotingOrStudyingTheTitle() {
        List<CatalogResult> results = rank("tintin au tibet",
                r("Le Douglas DC-3 de \"Tintin au Tibet\"", "Yves Horeau").french().cover(),
                r("Étudier « Tintin au Tibet »", "Jean-Marie Apostolidès").french().cover(),
                r("Une lecture de Tintin au Tibet", "Pierre Fresnault-Deruelle").french().cover(),
                r("Astérix le Gaulois Gde Collection", "René Goscinny").french().cover(),
                r("Tintin au Tibet", "Hergé").french().kind("COMIC"));

        assertEquals("Tintin au Tibet", results.get(0).title(), titles(results).toString());
    }

    @Test
    void aGuideByTheAuthorOfTheWorkIsTheWorkItself() {
        List<CatalogResult> results = rank("voyageur galactique",
                r("Le Guide du voyageur galactique", "Douglas Adams").french().cover().popular(60),
                r("Lire le Guide du voyageur galactique", "Jean Lecteur").french().cover());

        assertEquals("Douglas Adams", results.get(0).authors());
        assertEquals(2, results.size());
        // "Guide" belongs to Adams's title: only the reading by somebody else is a study.
        assertTrue(rank("voyageur galactique",
                r("Le Guide du voyageur galactique", "Douglas Adams").french()).size() == 1);
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
    void aMangaVolumeMakesTheOthersOfItsSeriesMangas() {
        List<CatalogResult> results = rank("l'attaque des titans",
                r("L'attaque des titans - Tome 4", "Hajime Isayama").series("L'attaque des titans", 4),
                r("L'attaque des titans - Tome 3", "Hajime Isayama").series("L'attaque des titans", 3).kind("MANGA"),
                r("L'attaque des titans T05", "諫山創").from("openlibrary", "OL5W").series("L'attaque des titans", 5));

        assertEquals(List.of("MANGA", "MANGA", "MANGA"), results.stream().map(CatalogResult::kind).toList());
        // The kanji-only credit shows the Latin one the rest of the run carries.
        assertEquals("Hajime Isayama", results.get(2).authors());
    }

    @Test
    void aComicVolumeOfSomebodyElseLeavesTheNovelsAlone() {
        List<CatalogResult> results = rank("les miserables",
                r("Les misérables - Tome 2", "Victor Hugo").series("Les misérables", 2).french(),
                r("Fantine", "Maxe L'Hermenier").series("Les misérables", 1).kind("COMIC").french());

        assertTrue(results.stream().anyMatch(r -> "BOOK".equals(r.kind())), results.toString());
    }

    @Test
    void recognisesAnAniListSeriesByItsSynonyms() {
        List<CatalogResult> candidates = List.of(
                r("Attack on Titan", "Hajime Isayama").from("anilist", "53390").kind("MANGA").build(),
                r("L'attaque des titans - Tome 1", "Hajime Isayama").series("L'attaque des titans", 1).build());

        List<CatalogResult> results = CatalogRanker.rank("l'attaque des titans", candidates, false,
                r -> "anilist".equals(r.provider()) ? List.of("Shingeki no Kyojin", "L'Attaque des Titans") : List.of())
                .results();

        assertTrue(results.stream().allMatch(r -> "MANGA".equals(r.kind())), results.toString());
        assertEquals("anilist", results.get(0).provider());
    }

    @Test
    void namesAVolumeTitledByItsPartUnderItsSeries() {
        List<CatalogResult> results = rank("one piece tome 1",
                r("Romance dawn", "Eiichirō Oda").series("One Piece", 1).kind("MANGA").french().cover(),
                r("One Piece, tome 1", "尾田栄一郎").from("openlibrary", "OL8215661W").popular(18));

        assertEquals(1, results.size(), titles(results).toString());
        assertEquals("One Piece - Tome 1 : Romance dawn", results.get(0).title());
        assertEquals("MANGA", results.get(0).kind());
    }

    // --- trimming -------------------------------------------------------------------------

    @Test
    void dropsThePaddingOfAPageThatFoundTheTitle() {
        List<CatalogResult> results = rank("changer l'eau des fleurs",
                r("Changer l'eau des fleurs", "Valérie Perrin").french().cover().popular(40),
                r("Les contes de Perrault", "Charles Perrault").french().cover(),
                r("Une lumière au bout du tunnel", "Anonyme").french().cover(),
                r("Fleurs : changer l'eau, couper les tiges, et tout ce que l'on sait des bouquets de saison",
                        "A. Jardinier").french(),
                r("Changer l'eau des fleurs", "Valérie Perrin").language("swe").cover());

        // The Swedish edition the BnF lists apart is the same author's work: the French one is here.
        assertEquals(List.of("Changer l'eau des fleurs"), titles(results));
        assertEquals("fre", results.get(0).language());
    }

    @Test
    void keepsEverythingWhenNothingMatchesTheTitle() {
        List<CatalogResult> results = rank("saga vaughan",
                r("Secret Surrender", "Vivian Vaughan"),
                r("Viking archaeology in Iceland", "Davide Zori"));

        assertEquals(2, results.size());
    }

    @Test
    void readsAnAuthorTypedAfterAOneWordTitle() {
        List<CatalogResult> results = rank("saga vaughan",
                r("Secret Surrender", "Vivian Vaughan"),
                r("Viking archaeology in Iceland", "Davide Zori"),
                r("Saga", "Brian K. Vaughan, Fiona Staples").kind("COMIC").french());

        assertEquals(List.of("Saga"), titles(results));
    }

    @Test
    void sinksAnAuthorlessResultATierAndDropsItWithoutACoverAmongOthers() {
        List<CatalogResult> results = rank("dune",
                r("La Dune", null).french(),
                r("Dune", "Frank Herbert").french().cover(),
                r("Le Messie de Dune", "Frank Herbert").french().cover(),
                r("Les enfants de Dune", "Frank Herbert").french().cover(),
                r("Dune - Tome 1", "Frank Herbert").series("Dune", 1),
                r("Dune infos", "Collectif").french(),
                r("La dune et la plage", null).french().cover());

        List<String> titles = titles(results);
        assertFalse(titles.contains("La Dune"), titles.toString());
        // Authorless with a cover stays, below the phrase matches that credit somebody.
        assertTrue(titles.indexOf("La dune et la plage") > titles.indexOf("Le Messie de Dune"), titles.toString());
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

    // --- third pass: collections, cross-check guard, junk runs, part titles, heads, strangers --

    @Test
    void aPublisherCollectionIsNoSeries() {
        assertTrue(CatalogRanker.isCollection("BOOK", "Folio junior", 899));
        assertTrue(CatalogRanker.isCollection("BOOK", "Profil littérature", 13));
        assertTrue(CatalogRanker.isCollection("BOOK", "Le Livre de poche", 3));
        assertTrue(CatalogRanker.isCollection("BOOK", "Études littéraires", 39));
        assertFalse(CatalogRanker.isCollection("BOOK", "Chronique du tueur de roi", 1));
        assertFalse(CatalogRanker.isCollection("MANGA", "One Piece", 112));

        CatalogResult cleaned = CatalogRanker.cleanUp(
                r("Harry Potter à l'école des sorciers", "J. K. Rowling").series("Folio junior", 899).build());
        assertEquals(null, cleaned.seriesTitle());
        assertEquals(null, cleaned.volumeNumber());
        // Open Library's "One Piece, Vol. 38" is a book only until the kinds are settled.
        assertEquals(Integer.valueOf(38), CatalogRanker.cleanUp(
                r("One Piece, Vol. 38", "Eiichirō Oda").series("One Piece", 38).build()).volumeNumber());
    }

    @Test
    void aBookKeepsItsOwnTitleAndItsSeriesAsData() {
        List<CatalogResult> results = rank("le nom du vent",
                r("Le nom du vent", "Patrick Rothfuss").series("Chronique du tueur de roi", 1).french().cover(),
                r("Harry Potter à l'école des sorciers", "J. K. Rowling").series("Folio junior", 899).french());

        assertEquals("Le nom du vent", results.get(0).title());
        assertEquals("Chronique du tueur de roi", results.get(0).seriesTitle());
        assertEquals(Integer.valueOf(1), results.get(0).volumeNumber());
    }

    @Test
    void aNovelIsNotAMangaBecauseAnAniListSynonymReadsLikeItsTitle() {
        List<CatalogResult> candidates = List.of(
                r("My Little Prince", "Kjorelo").from("anilist", "1001").kind("MANGA").build(),
                r("Le petit prince", "Antoine de Saint-Exupéry").french().cover().popular(300).build(),
                r("Le petit prince - Tome 2", "Antoine de Saint-Exupéry").series("Le petit prince", 2).french().build());

        List<CatalogResult> results = CatalogRanker.rank("le petit prince", candidates, false,
                r -> "anilist".equals(r.provider()) ? List.of("Le Petit Prince") : List.of()).results();

        CatalogResult novel = results.stream().filter(r -> r.title().equals("Le petit prince")).findFirst().orElseThrow();
        assertEquals("BOOK", novel.kind());
        assertTrue(results.stream().filter(r -> !"anilist".equals(r.provider()))
                .allMatch(r -> "BOOK".equals(r.kind())), results.toString());
        assertEquals("Le petit prince", results.get(0).title());
    }

    @Test
    void aVolumeCreditedInKanjiOrToNobodyJoinsTheAniListSeries() {
        List<CatalogResult> results = rank("blue lock",
                r("Blue Lock", "Muneyuki Kaneshiro").from("anilist", "137").kind("MANGA"),
                r("Blue Lock - Tome 2", null).series("Blue Lock", 2).cover(),
                r("Blue Lock - Tome 3", "金城宗幸").series("Blue Lock", 3).cover());

        assertTrue(results.stream().allMatch(r -> "MANGA".equals(r.kind())), results.toString());
    }

    @Test
    void trimsTheVolumesOfASeriesTheQueryIsNotAbout() {
        List<CatalogResult> results = rank("le nom du vent",
                r("Le nom du vent", "Patrick Rothfuss").french().cover(),
                r("Rapport fait, au nom de la Commission, sur les Iles sous le vent de Tahiti", "Sénat")
                        .series("Recueils de pièces imprimées concernant les colonies", 3).french(),
                r("Rapport fait au nom du comité de salut public, sur les Iles-du-Vent", null)
                        .series("Recueils de pièces imprimées concernant les colonies", 4).french(),
                r("Recueils de pièces imprimées concernant les colonies [Bulletin]", null).french());

        assertEquals(List.of("Le nom du vent"), titles(results));
    }

    @Test
    void groupsTheAlbumsOfOneVolumeAndOpenLibrarysEntryOfIt() {
        List<CatalogResult> results = rank("tintin au tibet",
                r("Tintin au Tibet", "Hergé").from("openlibrary", "OL1W").cover().popular(40),
                r("Les aventures de Tintin - Tome 20 : Tintin au Tibet", "Hergé")
                        .series("Les aventures de Tintin", 20).kind("COMIC").french(),
                r("Les aventures de Tintín - Tome 20 : Tintín au Tibet", "Hergé")
                        .series("Les aventures de Tintín", 20).french().isbn("9782203001206"));

        assertEquals(1, results.size(), results.toString());
        assertEquals("COMIC", results.get(0).kind());
        assertEquals(Integer.valueOf(40), results.get(0).popularity());
    }

    @Test
    void aCoverlessSeriesRecordDoesNotOutrankTheCoveredVolumesAndTakesTheirKind() {
        List<CatalogResult> results = rank("l'assassin royal",
                r("L'assassin royal", "Jean-Charles Gaudin, Laurent Sieurac").french(),
                r("L'apprentissage de l'assassin", "Robin Hobb").series("L'assassin royal", 1).french().cover(),
                r("L'assassin du roi", "Robin Hobb").series("L'assassin royal", 2).french().cover(),
                r("Le prince bâtard", "Jean-Charles Gaudin, Laurent Sieurac").series("L'assassin royal", 1)
                        .kind("COMIC").french());

        assertEquals("Robin Hobb", results.get(0).authors(), titles(results).toString());
        CatalogResult head = results.stream().filter(r -> r.title().equals("L'assassin royal")).findFirst().orElseThrow();
        assertEquals("COMIC", head.kind());
        // Hobb's novels keep their own titles; the comic's volume reads under its series.
        assertTrue(titles(results).contains("L'apprentissage de l'assassin"), titles(results).toString());
        assertTrue(titles(results).contains("L'assassin royal - Tome 1 : Le prince bâtard"), titles(results).toString());
    }

    @Test
    void aNovelCreditedAloneNeverTakesTheKindOfItsComicAdaptation() {
        List<CatalogResult> results = rank("les miserables",
                r("Les misérables", "Victor Hugo").french().cover(),
                r("Les misérables - Tome 1", "Victor Hugo, Looky").series("Les misérables", 1).kind("COMIC").french());

        assertEquals("BOOK", results.stream().filter(r -> r.title().equals("Les misérables")).findFirst()
                .orElseThrow().kind());
    }

    @Test
    void putsAVolumeCreditedToAStrangerAfterTheRestOfTheRun() {
        List<CatalogResult> results = rank("jujutsu kaisen 12",
                r("Jujutsu Kaisen 12", "Ryomen Sukune").from("openlibrary", "OL12W").cover().language("eng"),
                r("Jujutsu Kaisen", "Gege Akutami").from("anilist", "113138").kind("MANGA"),
                r("Jujutsu kaisen - Tome 12", "Gege Akutami").series("Jujutsu kaisen", 12).kind("MANGA").french(),
                r("Jujutsu kaisen - Tome 11", "Gege Akutami").series("Jujutsu kaisen", 11).kind("MANGA").french());

        assertEquals(List.of("Jujutsu kaisen - Tome 12", "Jujutsu Kaisen", "Jujutsu kaisen - Tome 11",
                "Jujutsu Kaisen 12"), titles(results));
    }
}
