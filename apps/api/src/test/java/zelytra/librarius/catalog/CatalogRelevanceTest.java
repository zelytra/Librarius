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

    /**
     * AniList's series for the manga queries, in the order AniList's search match puts them:
     * for "demon slayer" it answered the Kimetsu Academy spin-off first.
     */
    private static final Map<String, String[][]> ANILIST = Map.of(
            "one piece", new String[][] {{"One Piece", "ONE PIECE", "Eiichiro Oda", "500000", "OP"}},
            "blue lock", new String[][] {{"Blue Lock", "Blue Lock", "Muneyuki Kaneshiro, Yusuke Nomura", "90000", ""}},
            "demon slayer", new String[][] {
                    {"Demon Slayer: Kimetsu Academy", "Kimetsu Gakuen!", "Natsuki Hokami", "9000", ""},
                    {"Demon Slayer: Kimetsu no Yaiba", "Kimetsu no Yaiba", "Koyoharu Gotouge", "300000",
                            "Les Rôdeurs de la nuit"}});

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
        String why = describe(page);

        CatalogResult first = page.get(0);
        // A novel keeps its own title, whatever cycle the BnF files it in.
        assertEquals("le nom du vent", fold(first.title()), why);
        assertEquals("Chronique du tueur de roi", first.seriesTitle(), why);
        assertBooksKeepTheirTitles(page);
        assertTrue(credits(first, "rothfuss"), why);
        assertNotNull(first.coverUrl(), why);
        assertEquals(Integer.valueOf(76), first.popularity(), why);
        // Six BnF editions and the Open Library work: one result.
        assertEquals(1, page.stream().filter(r -> fold(r.title()).contains("le nom du vent")).count(), why);
        // The page found the novel: the 18th-century notices that merely share "au nom du" with
        // the query are padding, and go.
        for (CatalogResult r : page) {
            for (String noise : List.of("Arrest", "Rapport", "Au nom du")) {
                assertFalse(r.title().contains(noise), why);
            }
        }
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
        // Hugo's novel, never merged with the comic adaptations the BnF also titles "Les misérables".
        assertEquals("BOOK", page.get(0).kind(), why);
        assertEquals(Integer.valueOf(526), page.get(0).popularity(), why);
        assertTrue(page.stream().anyMatch(r -> "COMIC".equals(r.kind()) && credits(r, "chaiko")), why);
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
        assertBooksKeepTheirTitles(page);
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

        // The BnF's Pocket edition is volume 1 of "Le cycle de Dune", and still reads "Dune".
        assertEquals("Dune", page.get(0).title(), why);
        assertEquals(Integer.valueOf(155), page.get(0).popularity(), why);
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
        List<CatalogResult> page = page("one-piece", "one piece");
        assertMangaSeries(page, "One Piece");
        // Open Library credits these volumes in kanji only; the run lends them the BnF's credit.
        for (CatalogResult r : page.subList(1, 12)) {
            String title = fold(r.title());
            assertTrue(title.startsWith("one piece tome " + r.volumeNumber()), why(page));
            assertEquals("Eiichirō Oda", r.authors(), why(page));
        }
    }

    @Test
    void onePieceTomeOne() {
        List<CatalogResult> page = page("one-piece", "one piece tome 1");
        String why = describe(page);

        assertEquals(Integer.valueOf(1), page.get(0).volumeNumber(), why);
        assertEquals("anilist", page.get(1).provider(), why);
        assertEquals(1, page.stream().filter(r -> Integer.valueOf(1).equals(r.volumeNumber())
                && fold(r.seriesTitle()).equals("one piece")).count(), why);
    }

    private static String why(List<CatalogResult> page) {
        return describe(page);
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
        // AniList answered its Kimetsu Academy spin-off first: the main series leads all the same.
        List<CatalogResult> page = page("demon-slayer", "demon slayer");
        assertMangaSeries(page, "Demon Slayer: Kimetsu no Yaiba");
        // The "Carnet des pourfendeurs" the BnF numbers as volume 2 stays apart from the real one.
        assertEquals("Demon Slayer T02", page.get(1).title(), describe(page));
    }

    /**
     * No book reads "Series - Tome N : Title", and no publisher collection ("Folio junior" 899)
     * passes for a series.
     */
    private static void assertBooksKeepTheirTitles(List<CatalogResult> page) {
        for (CatalogResult r : page) {
            if ("BOOK".equals(r.kind())) {
                assertFalse(r.title().contains(" - Tome "), r.title());
                String text = fold(r.title() + ' ' + r.seriesTitle());
                for (String collection : List.of("folio", "poche", "pocket", "profil", "j ai lu")) {
                    assertFalse((' ' + text + ' ').contains(' ' + collection + ' '), r.title() + " / " + r.seriesTitle());
                }
            }
        }
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
