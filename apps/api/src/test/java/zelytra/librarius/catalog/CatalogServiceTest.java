package zelytra.librarius.catalog;

import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.Kind;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests routing and aggregation, without CDI or network (fake providers). */
class CatalogServiceTest {

    private static CatalogResult result(String kind, String title) {
        return result(kind, title, null);
    }

    private static CatalogResult result(String kind, String title, String author) {
        return new CatalogResult(kind, title, author, null, null, null, null, null, null, null,
                "fake", "ref");
    }

    private static CatalogResult result(String kind, String title, String author, String series,
            Integer volume) {
        return new CatalogResult(kind, title, author, null, null, null, null, null, null, null,
                series, volume, null, "fake", "ref");
    }

    private record FakeProvider(Kind kind, CatalogResult canned) implements CatalogProvider {
        @Override
        public String name() {
            return "fake-" + kind;
        }

        @Override
        public List<CatalogResult> search(CatalogQuery query, int limit) {
            return List.of(canned);
        }

        @Override
        public List<CatalogResult> upcoming(int limit) {
            return List.of(canned);
        }
    }

    /** Answers with as many distinct, tagged results as the caller asks for. */
    private record VerboseProvider(String name, Kind kind, int available)
            implements CatalogProvider {
        @Override
        public List<CatalogResult> search(CatalogQuery query, int limit) {
            return titled(Math.min(available, limit));
        }

        @Override
        public List<CatalogResult> upcoming(int limit) {
            return titled(Math.min(available, limit));
        }

        private List<CatalogResult> titled(int count) {
            List<CatalogResult> results = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                results.add(result("BOOK", name + "-title-" + i));
            }
            return results;
        }
    }

    /** Answers with a fixed, ordered list — lets a test control rank and content exactly. */
    private record ListProvider(Kind kind, List<CatalogResult> canned) implements CatalogProvider {
        @Override
        public String name() {
            return "list-" + kind;
        }

        @Override
        public List<CatalogResult> search(CatalogQuery query, int limit) {
            return canned.stream().limit(limit).toList();
        }

        @Override
        public List<CatalogResult> upcoming(int limit) {
            return canned;
        }

        @Override
        public List<CatalogResult> worksOfAuthor(String authorName, int limit) {
            return canned.stream().limit(limit).toList();
        }
    }

    /** Reports a fixed volume count, to check the series-volume routing. */
    private record VolumeProvider(Kind kind, int volumes) implements CatalogProvider {
        @Override
        public String name() {
            return "volumes-" + kind;
        }

        @Override
        public List<CatalogResult> search(CatalogQuery query, int limit) {
            return List.of();
        }

        @Override
        public List<CatalogResult> upcoming(int limit) {
            return List.of();
        }

        @Override
        public java.util.OptionalInt seriesVolumes(String title) {
            return java.util.OptionalInt.of(volumes);
        }
    }

    /** Keeps the criteria it was handed, to check they survive the trip to the provider. */
    private static final class RecordingProvider implements CatalogProvider {

        private CatalogQuery received;

        @Override
        public String name() {
            return "recording";
        }

        @Override
        public Kind kind() {
            return Kind.BOOK;
        }

        @Override
        public List<CatalogResult> search(CatalogQuery query, int limit) {
            received = query;
            return List.of();
        }

        @Override
        public List<CatalogResult> upcoming(int limit) {
            return List.of();
        }
    }

    /**
     * Pass-through cache: this test covers routing and merging, and wiring the real two-level
     * cache in would drag a database into a test that needs neither CDI nor network.
     */
    private static CatalogCache passThroughCache() {
        return new CatalogCache() {
            @Override
            public List<CatalogResult> get(Scope scope, String provider, String key,
                    Supplier<List<CatalogResult>> loader) {
                return loader.get();
            }
        };
    }

    @Test
    void routesToTheProviderMatchingTheKind() {
        CatalogService service = new CatalogService(List.of(
                new FakeProvider(Kind.BOOK, result("BOOK", "Fourth Wing")),
                new FakeProvider(Kind.MANGA, result("MANGA", "One Piece"))),
                passThroughCache());

        assertEquals("Fourth Wing",
                service.search(Set.of(Kind.BOOK), CatalogQuery.of("wing"), 10).get(0).title());
        assertEquals("One Piece",
                service.search(Set.of(Kind.MANGA), CatalogQuery.of("piece"), 10).get(0).title());
    }

    @Test
    void returnsEmptyWhenNoProviderForKind() {
        CatalogService service = new CatalogService(List.of(
                new FakeProvider(Kind.BOOK, result("BOOK", "Fourth Wing"))),
                passThroughCache());

        assertTrue(service.search(Set.of(Kind.MANGA), CatalogQuery.of("piece"), 10).isEmpty());
    }

    @Test
    void searchesEveryRegisteredKindWhenNoneIsNamed() {
        // No kind at all: one call must reach every registered provider, across mediums, and
        // return their answers merged into a single list.
        CatalogService service = new CatalogService(List.of(
                new FakeProvider(Kind.BOOK, result("BOOK", "Fourth Wing")),
                new FakeProvider(Kind.MANGA, result("MANGA", "One Piece"))),
                passThroughCache());

        List<String> titles = service.search(Set.of(), CatalogQuery.of("anything"), 10).stream()
                .map(CatalogResult::title).toList();

        assertTrue(titles.contains("Fourth Wing"), titles.toString());
        assertTrue(titles.contains("One Piece"), titles.toString());
    }

    @Test
    void narrowsToTheNamedKindsWhenSeveralAreGiven() {
        // Two of three mediums named: the third provider is left out entirely.
        CatalogService service = new CatalogService(List.of(
                new FakeProvider(Kind.BOOK, result("BOOK", "Fourth Wing")),
                new FakeProvider(Kind.MANGA, result("MANGA", "One Piece")),
                new FakeProvider(Kind.COMIC, result("COMIC", "Watchmen"))),
                passThroughCache());

        List<String> titles = service
                .search(Set.of(Kind.BOOK, Kind.MANGA), CatalogQuery.of("anything"), 10).stream()
                .map(CatalogResult::title).toList();

        assertEquals(Set.of("Fourth Wing", "One Piece"), new HashSet<>(titles));
    }

    @Test
    void aProviderBeingDownDegradesToTheOthersInACrossMediumSearch() {
        // No kind named, so every provider is reached; one returning nothing (down, or
        // off-topic) costs only its own results, the rest of the mediums still surface.
        CatalogService service = new CatalogService(List.of(
                new VerboseProvider("openlibrary", Kind.BOOK, 0),
                new FakeProvider(Kind.MANGA, result("MANGA", "One Piece"))),
                passThroughCache());

        List<String> titles = service.search(Set.of(), CatalogQuery.of("anything"), 10).stream()
                .map(CatalogResult::title).toList();

        assertEquals(List.of("One Piece"), titles);
    }

    @Test
    void aggregatesMultipleProvidersForSameKindAndDeduplicates() {
        // Two book providers returning the same title.
        CatalogService service = new CatalogService(List.of(
                new FakeProvider(Kind.BOOK, result("BOOK", "Fourth Wing")),
                new FakeProvider(Kind.BOOK, result("BOOK", "Fourth Wing"))),
                passThroughCache());

        // The duplicate (same title/author) is merged into a single entry.
        assertEquals(1, service.search(Set.of(Kind.BOOK), CatalogQuery.of("wing"), 10).size());
    }

    @Test
    void givesEachProviderAFairShareOfTheLimitWhenBothReturnAFullPage() {
        // Both providers could each fill the whole limit on their own; the faster/more
        // verbose one must not crowd the other out of the merged page.
        CatalogService service = new CatalogService(List.of(
                new VerboseProvider("openlibrary", Kind.BOOK, 20),
                new VerboseProvider("bnf", Kind.BOOK, 20)),
                passThroughCache());

        List<CatalogResult> results = service.search(Set.of(Kind.BOOK), CatalogQuery.of("wing"), 10);

        assertEquals(10, results.size());
        long fromOpenLibrary = results.stream()
                .filter(r -> r.title().startsWith("openlibrary")).count();
        long fromBnf = results.stream().filter(r -> r.title().startsWith("bnf")).count();
        assertEquals(5, fromOpenLibrary);
        assertEquals(5, fromBnf);
    }

    @Test
    void aSilentProviderLeavesTheWholeLimitToTheOther() {
        // A provider returning nothing (down, or off-topic) must not halve what the
        // other one is allowed to contribute.
        CatalogService service = new CatalogService(List.of(
                new VerboseProvider("openlibrary", Kind.BOOK, 0),
                new VerboseProvider("bnf", Kind.BOOK, 20)),
                passThroughCache());

        List<CatalogResult> results = service.search(Set.of(Kind.BOOK), CatalogQuery.of("wing"), 10);

        assertEquals(10, results.size());
        assertTrue(results.stream().allMatch(r -> r.title().startsWith("bnf")));
    }

    @Test
    void handsEveryAdvancedCriterionToTheProvider() {
        RecordingProvider provider = new RecordingProvider();
        CatalogService service = new CatalogService(List.of(provider), passThroughCache());
        CatalogQuery query = new CatalogQuery("dune", "herbert", 1965, "fr", "pocket", null);

        service.search(Set.of(Kind.BOOK), query, 10);

        // The service aggregates and caches; narrowing is the provider's job, and it can
        // only do it if the criteria reach it untouched.
        assertEquals(query, provider.received);
    }

    @Test
    void doesNotShareACacheEntryBetweenTwoDifferentCriteria() {
        Set<String> keys = new HashSet<>();
        CatalogCache recordingCache = new CatalogCache() {
            @Override
            public List<CatalogResult> get(Scope scope, String provider, String key,
                    Supplier<List<CatalogResult>> loader) {
                keys.add(key);
                return loader.get();
            }
        };
        CatalogService service = new CatalogService(
                List.of(new FakeProvider(Kind.BOOK, result("BOOK", "Dune"))), recordingCache);

        service.search(Set.of(Kind.BOOK), CatalogQuery.of("dune"), 10);
        service.search(Set.of(Kind.BOOK), new CatalogQuery("dune", "herbert", null, null, null, null), 10);

        // Same text, one extra criterion: the second search must not be served the first
        // one's answer.
        assertEquals(2, keys.size());
    }

    @Test
    void deduplicatesAcrossAccentAndPunctuationVariants() {
        // The same title and author from two catalogues, spelled with different accents and
        // punctuation, must merge into a single entry rather than read as a duplicate.
        CatalogService service = new CatalogService(List.of(
                new ListProvider(Kind.BOOK, List.of(
                        result("BOOK", "Astérix — Tome 1", "Goscinny"),
                        result("BOOK", "Asterix, tome 1", "Goscinny")))),
                passThroughCache());

        assertEquals(1, service.search(Set.of(Kind.BOOK), CatalogQuery.of("asterix"), 10).size());
    }

    @Test
    void ranksResultsMatchingTheQueryAboveOnesThatDoNot() {
        // A provider that returns in its own, non-relevance order: the genuine match must
        // still surface first once the merge re-ranks the page on the query.
        CatalogService service = new CatalogService(List.of(
                new ListProvider(Kind.BOOK, List.of(
                        result("BOOK", "An Unrelated Title"),
                        result("BOOK", "The Hobbit")))),
                passThroughCache());

        List<String> titles = service.search(Set.of(Kind.BOOK), CatalogQuery.of("hobbit"), 10)
                .stream().map(CatalogResult::title).toList();

        assertEquals("The Hobbit", titles.get(0));
    }

    @Test
    void collapsesEveryEditionOfOneSeriesVolumeIntoASingleResult() {
        // Three spellings of "One Piece, tome 1" — different subtitles and author romanisations —
        // but the same parsed series and volume: one result, under a canonical title.
        CatalogService service = new CatalogService(List.of(
                new ListProvider(Kind.BOOK, List.of(
                        result("MANGA", "One Piece - Édition originale, Tome 1", "Eiichiro Oda",
                                "One Piece", 1),
                        result("MANGA", "One Piece. 1, À l'aube d'une grande aventure",
                                "Oda, Eiichirō", "One Piece", 1),
                        result("MANGA", "One Piece Tome 1 : Romance Dawn", "Eiichirō Oda",
                                "One Piece", 1)))),
                passThroughCache());

        List<CatalogResult> results =
                service.search(Set.of(Kind.BOOK), CatalogQuery.of("one piece tome 1"), 10);

        assertEquals(1, results.size());
        assertEquals("One Piece - Tome 1", results.get(0).title());
    }

    @Test
    void seriesVolumesRoutesToTheProviderOfTheKindThatKnows() {
        CatalogService service = new CatalogService(List.of(
                new FakeProvider(Kind.BOOK, result("BOOK", "a book")),
                new VolumeProvider(Kind.MANGA, 20)),
                passThroughCache());

        assertEquals(20, service.seriesVolumes(Kind.MANGA, "One Piece").getAsInt());
        // A book catalogue reports no series total, so the kind with no such provider is empty.
        assertTrue(service.seriesVolumes(Kind.BOOK, "Any Book").isEmpty());
    }

    @Test
    void worksOfAuthorRoutesToTheProvidersOfTheGivenKinds() {
        CatalogService service = new CatalogService(List.of(
                new ListProvider(Kind.MANGA, List.of(
                        result("MANGA", "Naruto"), result("MANGA", "Boruto")))),
                passThroughCache());

        List<String> titles = service.worksOfAuthor(Set.of(Kind.MANGA), "Kishimoto", 10)
                .stream().map(CatalogResult::title).toList();

        assertEquals(List.of("Naruto", "Boruto"), titles);
    }

    /**
     * A book provider carries no dedicated author index, only the search one — Open Library and
     * the BnF as they stand — so the default worksOfAuthor routes the name through search. This
     * is what stops a book author's page from showing only the volumes already owned.
     */
    @Test
    void worksOfAuthorFallsBackToSearchingTheAuthorTerm() {
        RecordingProvider provider = new RecordingProvider();
        CatalogService service = new CatalogService(List.of(provider), passThroughCache());

        service.worksOfAuthor(Set.of(Kind.BOOK), "Rebecca Yarros", 10);

        // The author name reached the provider as an author term, not as free text.
        assertEquals("Rebecca Yarros", provider.received.author());
        assertNull(provider.received.text());
    }

    /** Answers its canned list after a fixed delay, to measure whether calls overlap. */
    private record SlowProvider(String name, Kind kind, long millis, List<CatalogResult> canned)
            implements CatalogProvider {
        @Override
        public List<CatalogResult> search(CatalogQuery query, int limit) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return canned;
        }

        @Override
        public List<CatalogResult> upcoming(int limit) {
            return List.of();
        }
    }

    @Test
    void callsTheProvidersAtOnceRatherThanOneAfterTheOther() {
        CatalogService service = new CatalogService(List.of(
                new SlowProvider("slow-a", Kind.BOOK, 600, List.of(result("BOOK", "A"))),
                new SlowProvider("slow-b", Kind.BOOK, 600, List.of(result("BOOK", "B"))),
                new SlowProvider("slow-c", Kind.MANGA, 600, List.of(result("MANGA", "C")))),
                passThroughCache());

        long start = System.nanoTime();
        List<CatalogResult> results = service.search(Set.of(), CatalogQuery.of("x"), 10);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertEquals(3, results.size());
        // Sequential calls would take 1.8 s; concurrent ones the slowest of them.
        assertTrue(elapsedMillis < 1500, "took " + elapsedMillis + " ms");
    }

    @Test
    void keepsTheProvidersRoundRobinOrderWhenTheyAnswerOutOfOrder() {
        CatalogService service = new CatalogService(List.of(
                new SlowProvider("first", Kind.BOOK, 300, List.of(result("BOOK", "Premier", "a"))),
                new SlowProvider("second", Kind.BOOK, 0, List.of(result("BOOK", "Second", "b")))),
                passThroughCache());

        List<CatalogResult> results = service.search(Set.of(Kind.BOOK), CatalogQuery.of("zzz"), 10);

        assertEquals(List.of("Premier", "Second"), results.stream().map(CatalogResult::title).toList());
    }

    @Test
    void ranksTheAskedVolumeAboveTheRestOfTheRun() {
        // "1" used to be found inside "113" and "112", so every volume tied with the one asked.
        CatalogService service = new CatalogService(List.of(new ListProvider(Kind.BOOK, List.of(
                result("BOOK", "One Piece. 113", "Oda", "One Piece", 113),
                result("BOOK", "One Piece. 112", "Oda", "One Piece", 112),
                result("BOOK", "One Piece. 1", "Oda", "One Piece", 1)))),
                passThroughCache());

        assertEquals("One Piece. 1",
                service.search(Set.of(Kind.BOOK), CatalogQuery.of("one piece 1"), 10).get(0).title());
        assertEquals("One Piece. 1",
                service.search(Set.of(Kind.BOOK), CatalogQuery.of("One Piece tome 1"), 10).get(0).title());
    }

    @Test
    void stillMatchesAWordBeingTyped() {
        CatalogService service = new CatalogService(List.of(new ListProvider(Kind.BOOK, List.of(
                result("BOOK", "Pierre et Jean"),
                result("BOOK", "One Piece")))),
                passThroughCache());

        assertEquals("One Piece",
                service.search(Set.of(Kind.BOOK), CatalogQuery.of("one pie"), 10).get(0).title());
    }

    @Test
    void givesACoverlessResultTheOpenLibraryCoverOfItsIsbn() {
        CatalogResult bnfOnly = new CatalogResult("BOOK", "La horde du contrevent", "Alain Damasio",
                2004, null, null, "9782070342266", "Gallimard", "fre", null, "bnf", "ark:/12148/x");
        CatalogResult covered = new CatalogResult("BOOK", "Dune", "Frank Herbert", 1965,
                "https://covers.openlibrary.org/b/id/1-M.jpg", null, "9782266116242", null, null,
                null, "openlibrary", "OL1W");
        CatalogResult noIsbn = result("BOOK", "Sans ISBN");

        assertEquals("https://covers.openlibrary.org/b/isbn/9782070342266-M.jpg?default=false",
                CatalogService.withIsbnCover(bnfOnly).coverUrl());
        assertEquals(covered.coverUrl(), CatalogService.withIsbnCover(covered).coverUrl());
        assertNull(CatalogService.withIsbnCover(noIsbn).coverUrl());
    }

    @Test
    void keepsTheLatinCreditWhenTwoCataloguesHoldTheSameVolume() {
        CatalogService service = new CatalogService(List.of(
                new ListProvider(Kind.BOOK, List.of(
                        result("BOOK", "ONE PIECE Tome 1", "尾田栄一郎", "ONE PIECE", 1))),
                new FakeProvider(Kind.BOOK, result("BOOK", "One Piece. 1", "Eiichirō Oda", "One Piece", 1))),
                passThroughCache());

        List<CatalogResult> results = service.search(Set.of(Kind.BOOK), CatalogQuery.of("one piece 1"), 10);

        assertEquals(1, results.size());
        assertEquals("Eiichirō Oda", results.get(0).authors());
    }

    @Test
    void ranksAWorkByTheAuthorTypedInTheSearchField() {
        CatalogService service = new CatalogService(List.of(new ListProvider(Kind.BOOK, List.of(
                result("BOOK", "Pétition aux membres du Corps législatif", "Jacinthe Dias"),
                result("BOOK", "Les Furtifs", "Alain Damasio")))),
                passThroughCache());

        assertEquals("Les Furtifs",
                service.search(Set.of(Kind.BOOK), CatalogQuery.of("damasio"), 10).get(0).title());
    }

    @Test
    void putsTheCompleteRecordFirstAmongEquallyRelevantOnes() {
        CatalogResult bare = result("BOOK", "Astérix le Gaulois", "Goscinny");
        CatalogResult complete = new CatalogResult("BOOK", "Astérix le Gaulois", "René Goscinny", 1961,
                "https://covers.openlibrary.org/b/id/1-M.jpg", null, "9782012101333", null, null,
                null, "fake", "ref2");
        CatalogService service = new CatalogService(List.of(new ListProvider(Kind.BOOK,
                List.of(bare, complete))), passThroughCache());

        assertEquals("René Goscinny",
                service.search(Set.of(Kind.BOOK), CatalogQuery.of("asterix"), 10).get(0).authors());
    }

    /** A catalogue of records rather than works — the BnF's shape. */
    private record RecordProvider(Kind kind, List<CatalogResult> canned) implements CatalogProvider {
        @Override
        public String name() {
            return "records-" + kind;
        }

        @Override
        public boolean listsWorks() {
            return false;
        }

        @Override
        public List<CatalogResult> search(CatalogQuery query, int limit) {
            return canned;
        }

        @Override
        public List<CatalogResult> upcoming(int limit) {
            return List.of();
        }

        @Override
        public List<CatalogResult> worksOfAuthor(String authorName, int limit) {
            return canned;
        }
    }

    private static List<CatalogResult> works(String prefix, int count) {
        List<CatalogResult> results = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            results.add(result("BOOK", prefix + " " + i, "Frank Herbert"));
        }
        return results;
    }

    @Test
    void drawsABibliographyFromTheCataloguesOfWorks() {
        CatalogService service = new CatalogService(List.of(
                new ListProvider(Kind.BOOK, works("Work", 6)),
                new RecordProvider(Kind.BOOK, works("Booster pack", 6))),
                passThroughCache());

        List<CatalogResult> bibliography = service.worksOfAuthor(Set.of(Kind.BOOK), "Frank Herbert", 40);

        assertEquals(6, bibliography.size());
        assertTrue(bibliography.stream().allMatch(r -> r.title().startsWith("Work")));
    }

    @Test
    void fillsAThinBibliographyFromTheRecordCatalogues() {
        CatalogService service = new CatalogService(List.of(
                new ListProvider(Kind.BOOK, works("Work", 2)),
                new RecordProvider(Kind.BOOK, works("Record", 3))),
                passThroughCache());

        List<CatalogResult> bibliography = service.worksOfAuthor(Set.of(Kind.BOOK), "Alain Damasio", 40);

        assertEquals(List.of("Work 0", "Work 1", "Record 0", "Record 1", "Record 2"),
                bibliography.stream().map(CatalogResult::title).toList());
    }

    @Test
    void asksEveryMediumForAnAuthorWhateverTheKindsOwned() {
        // An imported manga is filed as a book: its author's series live on the manga side.
        CatalogService service = new CatalogService(List.of(
                new ListProvider(Kind.BOOK, List.of()),
                new ListProvider(Kind.MANGA, works("Series", 5))),
                passThroughCache());

        assertEquals(5, service.worksOfAuthor(Set.of(Kind.BOOK), "Eiichiro Oda", 40).size());
    }
    /** Answers a weak page to the whole-word query and the novel to the prefixed one, recording both. */
    private static final class TruncatingProvider implements CatalogProvider {

        private final List<CatalogQuery> asked = new java.util.ArrayList<>();

        @Override
        public String name() {
            return "truncating";
        }

        @Override
        public Kind kind() {
            return Kind.BOOK;
        }

        @Override
        public boolean searchesByPrefix() {
            return true;
        }

        @Override
        public synchronized List<CatalogResult> search(CatalogQuery query, int limit) {
            asked.add(query);
            return query.prefixLastWord()
                    ? List.of(result("BOOK", "Le nom du vent", "Patrick Rothfuss"))
                    : List.of(result("BOOK", "Rapport sur les ventes", "Comité"));
        }

        @Override
        public List<CatalogResult> upcoming(int limit) {
            return List.of();
        }
    }

    /** Counts its searches, and cannot truncate a word. */
    private static final class CountingProvider implements CatalogProvider {

        private int searches;

        @Override
        public String name() {
            return "counting";
        }

        @Override
        public Kind kind() {
            return Kind.MANGA;
        }

        @Override
        public synchronized List<CatalogResult> search(CatalogQuery query, int limit) {
            searches++;
            return List.of();
        }

        @Override
        public List<CatalogResult> upcoming(int limit) {
            return List.of();
        }
    }

    @Test
    void retriesAWeakAnswerWithTheLastWordAsAPrefix() {
        TruncatingProvider truncating = new TruncatingProvider();
        CountingProvider other = new CountingProvider();
        List<String> keys = new java.util.ArrayList<>();
        CatalogCache recordingCache = new CatalogCache() {
            @Override
            public synchronized List<CatalogResult> get(Scope scope, String provider, String key,
                    Supplier<List<CatalogResult>> loader) {
                keys.add(key);
                return loader.get();
            }
        };
        CatalogService service = new CatalogService(List.of(truncating, other), recordingCache);

        List<CatalogResult> results = service.search(Set.of(), CatalogQuery.of("le nom du ven"), 10);

        assertEquals("Le nom du vent", results.get(0).title());
        assertEquals(List.of(false, true), truncating.asked.stream().map(CatalogQuery::prefixLastWord).toList());
        // Only the providers that can truncate are asked again, and through the cache.
        assertEquals(1, other.searches);
        assertEquals(3, keys.size());
        assertTrue(keys.stream().anyMatch(key -> key.contains("|prefix|")), keys.toString());
    }

    @Test
    void doesNotRetryAnAnswerThatAlreadyMatchesTheTitle() {
        TruncatingProvider truncating = new TruncatingProvider();
        CatalogService service = new CatalogService(List.of(truncating), passThroughCache());

        service.search(Set.of(Kind.BOOK), CatalogQuery.of("rapport sur les ventes"), 10);

        assertEquals(1, truncating.asked.size());
    }
}
