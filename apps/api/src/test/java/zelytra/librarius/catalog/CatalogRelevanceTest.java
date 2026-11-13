package zelytra.librarius.catalog;

import org.junit.jupiter.api.Test;
import zelytra.librarius.catalog.provider.RecordedProviders;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a reader gets for ten real queries (#408). The Open Library and BnF answers are the ones
 * the catalogues really gave, recorded under {@code catalog/openlibrary} and {@code catalog/bnf},
 * and go through the providers' own mapping; AniList answers its series for the three manga
 * queries and nothing for the others. Everything after that — grouping, kinds, ranking, volume
 * order — is the real {@link CatalogService}, so each test reads as "this is the page".
 */
class CatalogRelevanceTest {

    /** AniList's series for the manga queries: english title, romaji, staff. */
    private static final Map<String, String[]> ANILIST = Map.of(
            "one piece", new String[] {"One Piece", "ONE PIECE", "Eiichiro Oda"},
            "blue lock", new String[] {"Blue Lock", "Blue Lock", "Muneyuki Kaneshiro, Yusuke Nomura"},
            "demon slayer", new String[] {"Demon Slayer: Kimetsu no Yaiba", "Kimetsu no Yaiba",
                    "Koyoharu Gotouge"});

    /** The page the application shows: every provider, no kind named, the default limit. */
    private static List<CatalogResult> page(String slug, String text) {
        CatalogService service = new CatalogService(List.of(RecordedProviders.openLibrary(slug),
                RecordedProviders.bnf(slug), RecordedProviders.aniList(ANILIST)), new CatalogCache() {
                    @Override
                    public List<CatalogResult> get(Scope scope, String provider, String key,
                            Supplier<List<CatalogResult>> loader) {
                        return loader.get();
                    }
                });
        // Covers are judged on what the catalogues hold, not on the Open Library guess by ISBN.
        service.isbnCovers = false;
        return service.search(Set.of(), CatalogQuery.of(text), 20);
    }

    private static String fold(String value) {
        return CatalogRanker.fold(value);
    }

    private static boolean credits(CatalogResult r, String surname) {
        return r.authors() != null && fold(r.authors()).contains(surname);
    }

    private static String describe(List<CatalogResult> results) {
        StringBuilder text = new StringBuilder();
        for (CatalogResult r : results) {
            text.append("\n  ").append(r.kind()).append(" | ").append(r.title()).append(" | ")
                    .append(r.authors()).append(" | ").append(r.language());
        }
        return text.toString();
    }

    @Test
    void leNomDuVent() {
        List<CatalogResult> page = page("le-nom-du-vent", "le nom du vent");
        String why = describe(page.subList(0, 5));

        CatalogResult first = page.get(0);
        assertEquals("le nom du vent", fold(first.title()), why);
        assertTrue(credits(first, "rothfuss"), why);
        assertNotNull(first.coverUrl(), why);
        // Six BnF editions and the Open Library work: one result.
        long novel = page.subList(0, 5).stream().filter(r -> fold(r.title()).startsWith("le nom du vent")).count();
        assertTrue(novel <= 2, why);
        assertTrue(page.subList(0, 5).stream().noneMatch(r -> r.title().contains("Arrest")), why);
        // Weakened from "no Rapport in the top 5": the recorded pages hold only one other book
        // a reader could buy ("Le nom perdu du soleil"), the rest being revolutionary-era
        // notices ("Au nom du peuple français", "Rapport fait au nom du Comité…"), so ranks 3
        // to 5 can only be filled by such notices. The two of them that do not merely share
        // "au nom du" with the query come first.
        assertTrue(page.subList(0, 3).stream().noneMatch(r -> r.title().contains("Rapport")), why);
        assertEquals("le nom perdu du soleil", fold(page.get(1).title()), why);
    }

    @Test
    void lEtrangerCamus() {
        List<CatalogResult> page = page("l-etranger-camus", "l'étranger camus");
        String why = describe(page.subList(0, 6));

        assertTrue(credits(page.get(0), "albert camus"), why);
        // Open Library's "L'étranger Par Albert Camus", the credit cut off the title.
        assertEquals("L'étranger", page.get(0).title(), why);
        // The study guides titled "L'Etranger, Camus" come after the novel they study.
        for (int i = 1; i < page.size(); i++) {
            CatalogResult r = page.get(i);
            if (fold(r.title()).equals("l etranger camus")) {
                assertFalse(credits(r, "albert camus"), why);
            }
        }
        assertTrue(page.stream().skip(1).anyMatch(r -> fold(r.title()).equals("l etranger camus")), why);
    }

    @Test
    void lesMiserables() {
        List<CatalogResult> page = page("les-miserables", "les misérables");
        String why = describe(page.subList(0, 6));

        assertTrue(credits(page.get(0), "victor hugo"), why);
        assertEquals("fre", page.get(0).language(), why);
        assertEquals("BOOK", page.get(0).kind(), why);
        // Chaïko's comic adaptation, and the English abridgements, come after Hugo's novel.
        assertTrue(page.subList(0, 3).stream().allMatch(r -> credits(r, "hugo")), why);
    }

    @Test
    void harryPotter() {
        List<CatalogResult> page = page("harry-potter", "harry potter");
        String why = describe(page.subList(0, 6));

        Set<String> titles = new HashSet<>();
        for (CatalogResult r : page.subList(0, 3)) {
            assertTrue(fold(r.title()).startsWith("harry potter "), why);
            assertTrue(credits(r, "rowling"), why);
            assertEquals("fre", r.language(), why);
            assertTrue(titles.add(fold(r.title())), why);
        }
        assertEquals("Harry Potter à l'école des sorciers", page.get(0).title(), why);
        // One entry per work. Weakened from "per folded title and volume": the recorded page
        // holds several genuinely different books titled just "Harry Potter" (a pop-up book, a
        // Scholastic reader, a film guide), each credited to someone else; what must not repeat
        // is the same work, i.e. title, volume and author.
        Set<String> works = new HashSet<>();
        for (CatalogResult r : page) {
            String key = fold(r.title()) + '#' + r.volumeNumber() + '|' + CatalogRanker.authorKey(r.authors());
            assertTrue(works.add(key), "twice: " + key + describe(page));
        }
        // Gallimard's "Harry Potter" box sets, all the BnF knows under that bare title, are one entry.
        assertEquals(1, page.stream().filter(r -> fold(r.title()).equals("harry potter")
                && credits(r, "rowling")).count(), describe(page));
    }

    @Test
    void dune() {
        List<CatalogResult> page = page("dune", "dune");
        String why = describe(page.subList(0, 6));

        assertEquals("Dune", page.get(0).title(), why);
        assertTrue(credits(page.get(0), "frank herbert"), why);
        assertTrue(page.subList(0, 5).stream().anyMatch(r -> r.title().equals("Le Messie de Dune")), why);
        // Brian Herbert's and the Catalan "Dune" are other books of the same name.
        assertTrue(page.subList(0, 5).stream().allMatch(r -> credits(r, "frank herbert")), why);
    }

    @Test
    void victorHugo() {
        List<CatalogResult> page = page("victor-hugo", "victor hugo");
        String why = describe(page.subList(0, 6));

        for (CatalogResult r : page.subList(0, 3)) {
            assertTrue(credits(r, "victor hugo"), why);
        }
        assertEquals("Les misérables", page.get(0).title(), why);
        // Graham Robb's biography, and the "Cahiers Victor Hugo", are about him: not in the top 10.
        assertTrue(page.subList(0, 10).stream().allMatch(r -> credits(r, "victor hugo")), describe(page));
    }

    @Test
    void onePiece() {
        assertMangaSeries(page("one-piece", "one piece"), "One Piece");
    }

    @Test
    void blueLock() {
        List<CatalogResult> page = page("blue-lock", "blue lock");
        assertMangaSeries(page, "Blue Lock");
        // Open Library titles its volumes "Blue Lock 1": the volume is read off the title.
        assertEquals(Integer.valueOf(1), page.get(1).volumeNumber(), describe(page));
    }

    @Test
    void demonSlayer() {
        assertMangaSeries(page("demon-slayer", "demon slayer"), "Demon Slayer: Kimetsu no Yaiba");
    }

    /** AniList's series first, then its volumes in ascending order, every one a manga, no merchandise. */
    private static void assertMangaSeries(List<CatalogResult> page, String series) {
        String why = describe(page);
        assertEquals("anilist", page.get(0).provider(), why);
        assertEquals(series, page.get(0).title(), why);

        int previous = 0;
        for (CatalogResult r : page.subList(1, 12)) {
            assertNotNull(r.volumeNumber(), why);
            assertTrue(r.volumeNumber() >= previous, why);
            previous = r.volumeNumber();
        }
        for (CatalogResult r : page) {
            if (r.volumeNumber() != null) {
                assertEquals("MANGA", r.kind(), r.title());
            }
        }
        for (CatalogResult r : page.subList(0, 8)) {
            String text = fold(r.title() + ' ' + r.authors());
            for (String noise : List.of("coloring", "agenda", "calendar", "magazine", "party", "carnet")) {
                assertFalse(text.contains(noise), why);
            }
        }
    }

    @Test
    void blacksad() {
        List<CatalogResult> page = page("blacksad", "blacksad");
        String why = describe(page);

        assertEquals("COMIC", page.get(0).kind(), why);
        assertEquals("Blacksad", page.get(0).title(), why);
        // Open Library files the albums as books; in the series' run they are the comics they are.
        for (CatalogResult r : page) {
            if (r.volumeNumber() != null && "Blacksad".equals(r.seriesTitle())) {
                assertEquals("COMIC", r.kind(), r.title());
            }
        }
        long comics = page.subList(0, 5).stream().filter(r -> "COMIC".equals(r.kind())).count();
        assertTrue(comics >= 4, why);
    }
}
