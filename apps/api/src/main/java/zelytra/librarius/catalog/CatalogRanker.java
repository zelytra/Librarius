package zelytra.librarius.catalog;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Orders the merged answers of a free-text catalog search the way a reader expects them: the
 * work they typed first, each work once, the volumes of a series in order.
 *
 * <p>The steps, in order:
 * <ol>
 *   <li><b>Clean-up</b> of provider artefacts ({@link #cleanUp}): "L'étranger Par Albert
 *       Camus" loses the credit glued to its title, "L' Etranger" its space, "LES MISERABLES"
 *       its capitals, "J. K Rowling" gains its missing dot.</li>
 *   <li><b>Kinds</b> ({@link #settleKinds}): what AniList answered as a manga series — by its
 *       title or one of its synonyms — makes the volumes of that series mangas, and inside a
 *       series a manga (else comic) volume makes the others so, but only those sharing a
 *       credited person ({@link #mayBecome}): a novel is not a manga by its title.</li>
 *   <li><b>Grouping</b>: the editions of one work — same kind family (a novel never merges with
 *       its comic adaptation), and same ISBN, or same series and volume with a credited person
 *       in common, or same folded title and first author — collapse into one result, the
 *       French one with a cover first, filled in from the others. A comic or manga volume the
 *       catalogue titles by its part ("Romance dawn") is shown under its series, "One piece -
 *       Tome 1 : Romance dawn"; a book keeps its own title, and a publisher collection ("Folio
 *       junior" 899) is no series at all. A kanji-only credit takes the Latin one of its
 *       series.</li>
 *   <li><b>Score</b>: each result gets a <em>level</em>, then a <em>secondary score</em>
 *       inside the level (see below).</li>
 *   <li><b>Trim</b> ({@link #trimmed}): a page that found the title loses its padding.</li>
 *   <li><b>Volumes in order</b>: the volumes of one series are emitted together, in ascending
 *       order, from the position of the best-ranked of them, the series itself first; when
 *       the query names a volume, that volume leads.</li>
 * </ol>
 *
 * <p>The <b>level</b> is twice the title tier — exact title (5) &gt; title starting with the
 * query, or "query + volume" (4) &gt; title containing the query phrase (3) &gt; title
 * holding every query word (2) &gt; some of them (1) &gt; none (0) — then adjusted:
 * <ul>
 *   <li>an <b>author</b> named in the query counts: "l'étranger camus" (a credited author
 *       plus the rest of the title) is as good as an exact title, and a query that is wholly
 *       a person's name ("victor hugo") ranks the works <em>by</em> that person level with an
 *       exact title;</li>
 *   <li>a <b>very popular</b> work (Open Library's edition count, see {@link #LIFT}) is
 *       lifted one tier, where it then competes on popularity: "Harry Potter à l'école des
 *       sorciers" (400 editions) is level with, and above, an exact "Harry Potter" box set;
 *       it never climbs two tiers;</li>
 *   <li>a <b>derivative</b> — a study guide, a colouring book, a spin-off magazine, an
 *       exhibition catalogue, a box set, a book <em>about</em> the best-ranked work's author
 *       — drops a tier and a half, below every genuine match of its tier and of the tier
 *       under it ({@link #isDerivative});</li>
 *   <li>a title match <b>by somebody else</b> than the best-ranked work's author ("Dune" by
 *       Brian Herbert, "Les misérables" adapted by Chaïko) drops one tier;</li>
 *   <li>a result with <b>no author</b> at all drops one tier.</li>
 * </ul>
 *
 * <p>The <b>secondary score</b> orders a level: popularity on a log scale (400 editions
 * against 100 matter less than 5 against 1; a BnF phrase match with a cover counts as
 * moderately popular, a group of editions counts its size), French first (other languages
 * lower, an unknown one neutral), a cover, a complete record. The providers' round-robin order
 * breaks what is left.
 */
final class CatalogRanker {

    static final int NONE = 0;
    static final int PARTIAL = 1;
    static final int ALL_WORDS = 2;
    static final int PHRASE = 3;
    static final int PREFIX = 4;
    static final int EXACT = 5;

    /** Editions (Open Library's count, or the size of a group) from which a work is lifted a tier. */
    static final int LIFT = 50;

    /** What a BnF phrase match with a cover is worth as popularity: a real book in print. */
    private static final int COVERED_PHRASE = 8;

    /** The ranked results, and whether none of them matched the query's title convincingly. */
    record Ranking(List<CatalogResult> results, boolean weak) {
    }

    private CatalogRanker() {
    }

    // --- entry point ----------------------------------------------------------------------

    /**
     * Ranks the merged candidates (in the providers' round-robin order) against the text.
     *
     * @param prefixAware whether the last query word also matches a title word it starts
     *                    ("ven" finds "vent") — only for the retry after a weak whole-word answer
     */
    static Ranking rank(String text, List<CatalogResult> candidates, boolean prefixAware) {
        return rank(text, candidates, prefixAware, r -> List.of());
    }

    /**
     * @param alternativeTitles the other names a provider knows a result by — AniList's
     *                          synonyms, by which "L'Attaque des Titans" is Shingeki no Kyojin
     */
    static Ranking rank(String text, List<CatalogResult> candidates, boolean prefixAware,
            Function<CatalogResult, List<String>> alternativeTitles) {
        Query query = Query.of(text, prefixAware);
        List<CatalogResult> cleaned = candidates.stream().map(CatalogRanker::cleanUp).toList();
        Map<String, String> aliases = aniListSeries(cleaned, alternativeTitles);
        Set<String> knownSeries = knownSeries(cleaned, aliases);
        List<Entry> entries = group(settleKinds(cleaned, knownSeries, aliases), knownSeries, aliases);
        lendLatinCredits(entries);

        for (Entry entry : entries) {
            entry.scoreTitle(query, aliases);
        }
        boolean weak = entries.stream().noneMatch(e -> e.tier >= PREFIX);
        // A coverless record titled after a series whose volumes, with covers, are on the page
        // competes at their tier, where the cover decides: Gaudin's comic "L'assassin royal" is
        // not to sit above Robin Hobb's volumes on its exact title alone.
        Set<String> covered = new HashSet<>();
        entries.stream().filter(e -> e.volume != null && e.seriesKey != null && has(e.result.coverUrl()))
                .forEach(e -> covered.add(e.seriesKey));
        for (Entry entry : entries) {
            if (entry.tier == EXACT && entry.volume == null && !isAniList(entry.result)
                    && !has(entry.result.coverUrl()) && covered.contains(groupTitle(entry.result.title()))) {
                entry.tier = PREFIX;
            }
        }
        List<String> person = personNamed(query, entries);
        for (Entry entry : entries) {
            entry.scoreLevel(query, person);
        }
        entries.sort(ORDER);

        // The best-ranked work's author is the one the reader is after: a book about them, or
        // somebody else's book of the same title, is not.
        List<List<String>> canonical = person != null ? List.of(person) : canonicalAuthor(entries);
        if (canonical != null) {
            for (Entry entry : entries) {
                entry.judgeAgainst(canonical, query);
            }
            entries.sort(ORDER);
        }
        List<CatalogResult> ordered = inVolumeOrder(entries, query, aliases, canonical).stream()
                .map(e -> e.displayed().result)
                .toList();
        return new Ranking(ordered, weak);
    }

    /**
     * Whether a weak answer is worth a second round with the last word as a prefix: a plain
     * keyword search (an advanced one says exactly what it wants) of two words at least, the
     * last of them three letters or more — "le nom du ven", not "one piece 1".
     */
    static boolean prefixRetryApplies(CatalogQuery query) {
        if (query.text() == null || query.author() != null || query.year() != null
                || query.language() != null || query.publisher() != null || query.isbn() != null) {
            return false;
        }
        String[] words = fold(query.text()).split(" ");
        String last = words[words.length - 1];
        return words.length >= 2 && last.length() >= 3 && !isNumber(last);
    }

    private static final Comparator<Entry> ORDER = Comparator
            .comparingInt((Entry e) -> -e.level)
            .thenComparingDouble(e -> -e.secondary)
            .thenComparingInt(e -> e.order);

    // --- the query ------------------------------------------------------------------------

    /** The query as matched: folded words, leading article and volume markers dropped. */
    private record Query(List<String> words, Integer volume, boolean prefixAware) {

        static Query of(String text, boolean prefixAware) {
            List<String> words = tokens(text);
            Integer volume = VolumeParser.parse(text).volumeNumber();
            if (volume == null && words.size() >= 2 && isNumber(words.get(words.size() - 1))) {
                // "jujutsu kaisen 12": a reader types the number bare.
                volume = Integer.valueOf(words.get(words.size() - 1));
            }
            return new Query(words, volume, prefixAware);
        }

        boolean isEmpty() {
            return words.isEmpty();
        }

        boolean names(String marker) {
            return contains(String.join(" ", words), marker);
        }
    }

    // --- display clean-up -----------------------------------------------------------------

    /** "L' Etranger": a space left after the apostrophe of an elision. */
    private static final Pattern ELISION_SPACE = Pattern.compile("(?<=\\p{L})(['’])\\s+(?=\\p{L})");

    /** "L'étranger Par Albert Camus", "Dune by Frank Herbert": a credit glued to the title. */
    private static final Pattern CREDIT_SUFFIX = Pattern.compile("(?i)\\s+(?:par|by)\\s+([^,;:()]+)$");

    /** A lone capital followed by a space: the "K" of "J. K Rowling". */
    private static final Pattern BARE_INITIAL = Pattern.compile("(?<![\\p{L}.'’-])(\\p{Lu})(?=\\s)");

    /**
     * Minimal, safe clean-up of what a reader sees. Nothing is guessed: the credit suffix only
     * goes when it is the credited author, capitals only when the whole title is shouted, and
     * an author string only gets its whitespace and its initials' dots.
     */
    static CatalogResult cleanUp(CatalogResult r) {
        String title = r.title();
        if (title != null) {
            title = ELISION_SPACE.matcher(spaced(title)).replaceAll("$1");
            Matcher credit = CREDIT_SUFFIX.matcher(title);
            if (credit.find() && credits(names(r.authors()), tokens(credit.group(1)))) {
                title = title.substring(0, credit.start()).trim();
            }
            if (isShouted(title)) {
                title = title.substring(0, 1).toUpperCase(Locale.ROOT)
                        + title.substring(1).toLowerCase(Locale.ROOT);
            }
        }
        String authors = r.authors() == null ? null
                : BARE_INITIAL.matcher(spaced(r.authors())).replaceAll("$1.");
        String series = r.seriesTitle();
        Integer volume = r.volumeNumber();
        if (title != null && series != null && !fold(title).startsWith(fold(series))
                && isCollection(r.kind(), series, volume)) {
            series = null;
            volume = null;
        }
        if (java.util.Objects.equals(title, r.title()) && java.util.Objects.equals(authors, r.authors())
                && java.util.Objects.equals(series, r.seriesTitle())) {
            return r;
        }
        return new CatalogResult(r.kind(), title, authors, r.year(), r.coverUrl(), r.synopsis(),
                r.isbn13(), r.publisher(), r.language(), r.releaseDate(), series,
                volume, r.pageCount(), r.provider(), r.providerRef(), r.popularity());
    }

    /**
     * The publisher collections the BnF records as a numbered series: "Folio junior" 899 is
     * where Gallimard printed Harry Potter, not a series it belongs to. Folded, matched at the
     * start of the series title.
     */
    private static final List<String> COLLECTIONS = List.of("folio", "poche", "le livre de poche",
            "livre de poche", "pocket", "j ai lu", "points", "10 18", "babel", "profil",
            "l oeuvre au clair", "un livre une oeuvre", "bibliotheque", "que sais je", "bouquins",
            "omnibus", "librio", "gf", "classiques", "petits classiques", "bibliocollege",
            "etonnants classiques");

    /**
     * Whether a book's series is a publisher collection rather than a series: a known
     * collection, or a number no novel cycle reaches. Then it neither names, nor groups, nor
     * orders the book. A comic or a manga keeps its series whatever its number, and so does a
     * title that leads with its series ("One Piece, Vol. 38", which Open Library files as a book).
     */
    static boolean isCollection(String kind, String series, Integer volume) {
        if (!"BOOK".equals(kind) || series == null) {
            return false;
        }
        if (volume != null && volume > 30) {
            return true;
        }
        String folded = fold(series);
        return COLLECTIONS.stream().anyMatch(c -> folded.equals(c) || folded.startsWith(c + ' '));
    }

    private static String spaced(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC).replaceAll("\\s+", " ").trim();
    }

    /** All capitals, more than three letters: "LES MISERABLES", not "BD" nor "One Piece". */
    private static boolean isShouted(String title) {
        long letters = title.codePoints().filter(Character::isLetter).count();
        return letters > 3 && title.codePoints().anyMatch(Character::isUpperCase)
                && title.codePoints().noneMatch(Character::isLowerCase);
    }

    // --- grouping -------------------------------------------------------------------------

    /** A parenthesised statement that only says which language edition this is. */
    private static final Pattern EDITION_STATEMENT = Pattern.compile(
            "(?i)\\s*\\((?:french|english|spanish) edition\\)|\\s*\\([ée]dition fran[cç]aise\\)");

    /** The BnF's "Series - Tome N : Part": the part, the album's own title. */
    private static final Pattern PART_TITLE = Pattern.compile("^.+ - Tome \\d+ : (.+)$");

    /** A title that already says which volume it is: "Fantine - Tome I". */
    private static final Pattern VOLUME_WORD = Pattern.compile("(?iu)(?<!\\p{L})(?:tome|vol\\.?|volume)(?!\\p{L})");

    /** "Blue Lock 1", "Demon Slayer 03", "Blue Lock nº 05": a bare number after a known series. */
    private static final Pattern BARE_VOLUME = Pattern.compile(
            "^(.*\\p{L})\\s+(?:n[º°o]\\.?\\s*)?0*(\\d{1,3})$");

    /**
     * Collapses the editions of one work. Two results are one work when they are of the same
     * kind — a novel never merges with its comic adaptation — and share an ISBN, or a series
     * and a volume with a credited person in common (or a credit that cannot be compared:
     * kanji, none), or a folded title and a first author. AniList answers series, not editions,
     * and is never merged into a book record.
     *
     * <p>Open Library files everything as a book, manga and comics included, so its book is no
     * claim: its results are attached last, to the book group of the same work when there is
     * one, else to the comic or manga group, else to each other.
     */
    private static List<Entry> group(List<CatalogResult> results, Set<String> knownSeries,
            Map<String, String> aliases) {
        Grouping grouping = new Grouping(aliases);
        for (boolean classified : new boolean[] {true, false}) {
            for (int i = 0; i < results.size(); i++) {
                CatalogResult r = results.get(i);
                if (unclassified(r) != classified) {
                    grouping.add(i, r, seriesVolume(r, knownSeries));
                }
            }
        }
        for (Entry entry : grouping.groups) {
            entry.merge(aliases);
        }
        return grouping.groups;
    }

    /** An Open Library book: the provider has no notion of comics or manga. */
    private static boolean unclassified(CatalogResult r) {
        return "openlibrary".equals(r.provider()) && "BOOK".equals(r.kind());
    }

    /** The kind family a group is made of: books apart from comics and manga, which merge together. */
    private static String family(CatalogResult r) {
        if (isAniList(r)) {
            return "series";
        }
        if (unclassified(r)) {
            return "unclassified";
        }
        return "BOOK".equals(r.kind()) ? "book" : "drawn";
    }

    private static final class Grouping {
        final List<Entry> groups = new ArrayList<>();
        final Map<String, Entry> byKey = new HashMap<>();
        final Map<String, List<Entry>> byVolume = new HashMap<>();
        final Map<String, String> aliases;

        Grouping(Map<String, String> aliases) {
            this.aliases = aliases;
        }

        void add(int order, CatalogResult r, SeriesVolume sv) {
            Entry found = null;
            List<String> families = unclassified(r)
                    ? List.of("book", "drawn", "unclassified") : List.of(family(r));
            for (String f : families) {
                found = find(f, r, sv);
                if (found != null) {
                    break;
                }
            }
            if (found == null) {
                found = new Entry(order, family(r));
                groups.add(found);
            }
            found.order = Math.min(found.order, order);
            found.members.add(new Member(r, sv));
            for (String key : keys(found.family, r)) {
                byKey.putIfAbsent(key, found);
            }
            if (sv != null && !merchandise(r)) {
                List<Entry> volume = byVolume.computeIfAbsent(volumeKey(found.family, sv, aliases), k -> new ArrayList<>());
                if (!volume.contains(found)) {
                    volume.add(found);
                }
            }
        }

        private Entry find(String f, CatalogResult r, SeriesVolume sv) {
            for (String key : keys(f, r)) {
                Entry found = byKey.get(key);
                if (found != null) {
                    return found;
                }
            }
            if (sv == null || isAniList(r) || merchandise(r)) {
                return null;
            }
            for (Entry candidate : byVolume.getOrDefault(volumeKey(f, sv, aliases), List.of())) {
                if (candidate.members.stream().anyMatch(m -> comparableCredits(m.result().authors(), r.authors()))) {
                    return candidate;
                }
            }
            return null;
        }
    }

    /**
     * A record numbered in a series that is not one of its volumes: the BnF files the "Carnet
     * des pourfendeurs" as volume 2 of Demon Slayer, and it must not swallow the real one.
     */
    private static boolean merchandise(CatalogResult r) {
        String title = fold(r.title()) + ' ' + fold(r.authors());
        return Stream.of(ACTIVITY, SPIN_OFF, BOX_SET).flatMap(List::stream).anyMatch(m -> contains(title, m));
    }

    private static List<String> keys(String f, CatalogResult r) {
        if (isAniList(r)) {
            return List.of("anilist:" + r.providerRef());
        }
        List<String> keys = new ArrayList<>(3);
        keys.add(f + "|work:" + groupTitle(r.title()) + '|' + authorKey(r.authors()));
        // "Les aventures de Tintin - Tome 20 : Tintin au Tibet" is also "Tintin au Tibet", the
        // title Open Library knows the album by.
        Matcher part = PART_TITLE.matcher(r.title() == null ? "" : r.title());
        if (part.matches()) {
            keys.add(f + "|work:" + groupTitle(part.group(1)) + '|' + authorKey(r.authors()));
        }
        if (r.isbn13() != null && r.isbn13().matches("\\d{13}")) {
            keys.add(f + "|isbn:" + r.isbn13());
        }
        return keys;
    }

    /** The series by its AniList name when it has one: "Demon Slayer - Kimetsu No Yaiba" is "Demon slayer". */
    private static String volumeKey(String f, SeriesVolume sv, Map<String, String> aliases) {
        String series = fold(sv.series());
        return f + "|volume:" + aliases.getOrDefault(series, series) + '#' + sv.volume();
    }

    /**
     * Whether two credit lines may be the same volume's: they share a person, or one of them
     * cannot be compared — none at all, or written in another script ("尾田栄一郎"). The BnF
     * credits the Blue Lock spin-off to its own artist, and it is not the main series' volume.
     */
    private static boolean comparableCredits(String a, String b) {
        if (!latin(a) || !latin(b)) {
            return true;
        }
        return creditsAny(names(a), names(b));
    }

    /** The title as grouped: folded, its language-edition statement dropped. */
    static String groupTitle(String title) {
        return title == null ? "" : fold(EDITION_STATEMENT.matcher(title).replaceAll(""));
    }

    /** The first credited name, folded and squeezed: "J. K Rowling", "J.K. Rowling" → "jkrowling". */
    static String authorKey(String authors) {
        List<List<String>> names = names(authors);
        return names.isEmpty() ? "" : String.join("", names.get(0));
    }

    /**
     * The series and the volume a result belongs to, and whether its title is nothing more
     * than that ("Blue Lock - Tome 31", "One Piece, tome 1") rather than a work of its own that
     * happens to be numbered in a series ("Fantine", volume 1 of Les Misérables).
     */
    private record SeriesVolume(String series, int volume, boolean titleIsVolume) {
    }

    private static SeriesVolume seriesVolume(CatalogResult r, Set<String> knownSeries) {
        String title = r.title() == null ? "" : r.title();
        boolean recorded = r.seriesTitle() != null && !r.seriesTitle().isBlank() && r.volumeNumber() != null;
        if (recorded && fold(title).startsWith(fold(r.seriesTitle()))) {
            return new SeriesVolume(r.seriesTitle(), r.volumeNumber(), true);
        }
        VolumeParser.Parsed parsed = VolumeParser.parse(title);
        if (parsed.seriesTitle() != null && parsed.volumeNumber() != null) {
            return new SeriesVolume(parsed.seriesTitle(), parsed.volumeNumber(), true);
        }
        // A bare number is never a volume on its own ("Fahrenheit 451"), only after the name of
        // a series another result of the page is a volume of ("Blue Lock 1" next to the BnF's
        // "Blue Lock - Tome 31").
        Matcher bare = BARE_VOLUME.matcher(title);
        if (bare.matches() && knownSeries.contains(fold(bare.group(1)))) {
            return new SeriesVolume(bare.group(1), Integer.parseInt(bare.group(2)), true);
        }
        return recorded ? new SeriesVolume(r.seriesTitle(), r.volumeNumber(), false) : null;
    }

    private static Set<String> knownSeries(List<CatalogResult> results, Map<String, String> aliases) {
        Set<String> known = new HashSet<>(aliases.keySet());
        for (CatalogResult r : results) {
            if (r.seriesTitle() != null && r.volumeNumber() != null) {
                known.add(fold(r.seriesTitle()));
            }
        }
        known.remove("");
        return known;
    }

    /**
     * The series AniList answered, by every folded name a book catalogue may give it: its
     * title, its other titles and synonyms ("L'Attaque des Titans" for Shingeki no Kyojin), and
     * the part of its title before a colon ("Demon Slayer" for "Demon Slayer: Kimetsu no
     * Yaiba"), which is how French editions title it. Maps each name to the series' own. A name
     * two series share goes to the one AniList answered first, the main series ("Kimetsu no
     * Yaiba" before its "Kimetsu Academy" spin-off, see {@code AniListProvider.mainSeriesFirst}).
     */
    private static Map<String, String> aniListSeries(List<CatalogResult> results,
            Function<CatalogResult, List<String>> alternativeTitles) {
        // AniList's own order: the provider puts the main series before its spin-offs.
        List<CatalogResult> series = results.stream()
                .filter(r -> isAniList(r) && r.title() != null)
                .toList();
        Map<String, String> aliases = new LinkedHashMap<>();
        for (CatalogResult r : series) {
            String own = fold(r.title());
            aliases.putIfAbsent(own, own);
            List<String> names = new ArrayList<>(alternativeTitles.apply(r));
            int colon = r.title().indexOf(':');
            if (colon > 0) {
                names.add(r.title().substring(0, colon));
            }
            for (String name : names) {
                String folded = fold(name);
                if (folded.length() >= 3) {
                    aliases.putIfAbsent(folded, own);
                }
            }
        }
        aliases.remove("");
        return aliases;
    }

    /**
     * Whether a result may take the kind of a series credited {@code credits}: it credits one of
     * those people (romanisations folded), or it is a numbered volume credited in another
     * script or not at all, or it is already a comic. "Le petit prince" by Saint-Exupéry is not
     * the manga "My Little Prince" because one of AniList's synonyms reads like it.
     */
    private static boolean mayBecome(CatalogResult r, String credits, boolean volume) {
        if ("COMIC".equals(r.kind())) {
            return true;
        }
        if (!latin(r.authors())) {
            return volume;
        }
        return latin(credits) && creditsAny(names(credits), names(r.authors()));
    }

    /**
     * Settles the kinds before grouping, so the editions of one manga volume are all of one
     * family. What AniList answered as a manga series makes every result filed under that
     * series a manga; and inside one series, a volume catalogued as a manga (else a comic)
     * makes the others so. Either way only under {@link #mayBecome}: a comic "Fantine" by Maxe
     * L'Hermenier does not make Hugo's volumes comics. A manga is never downgraded.
     */
    private static List<CatalogResult> settleKinds(List<CatalogResult> results, Set<String> knownSeries,
            Map<String, String> aliases) {
        Map<String, String> aniListCredits = new HashMap<>();
        for (CatalogResult r : results) {
            if (isAniList(r) && r.title() != null) {
                aniListCredits.putIfAbsent(fold(r.title()), r.authors());
            }
        }
        List<CatalogResult> settled = new ArrayList<>(results.size());
        Map<String, List<CatalogResult>> drawn = new HashMap<>();
        List<String> keys = new ArrayList<>(results.size());
        for (CatalogResult r : results) {
            CatalogResult result = r;
            SeriesVolume sv = isAniList(r) ? null : seriesVolume(r, knownSeries);
            String series = sv == null ? null : fold(sv.series());
            String matched = series != null && aliases.containsKey(series) ? aliases.get(series)
                    : aliases.get(groupTitle(r.title()));
            if (!isAniList(r) && !"MANGA".equals(r.kind()) && matched != null
                    && mayBecome(r, aniListCredits.get(matched), sv != null)) {
                result = r.withKind("MANGA");
            }
            String key = series == null ? null : aliases.getOrDefault(series, series);
            keys.add(key);
            settled.add(result);
            if (key != null && !"BOOK".equals(result.kind())) {
                drawn.computeIfAbsent(key, k -> new ArrayList<>()).add(result);
            }
        }
        for (int i = 0; i < settled.size(); i++) {
            CatalogResult r = settled.get(i);
            String key = keys.get(i);
            if (key == null || "MANGA".equals(r.kind())) {
                continue;
            }
            String kind = r.kind();
            for (CatalogResult other : drawn.getOrDefault(key, List.of())) {
                if (other != r && mayBecome(r, other.authors(), true)) {
                    kind = strongerKind(kind, other.kind());
                }
            }
            if (!kind.equals(r.kind())) {
                settled.set(i, r.withKind(kind));
            }
        }
        // The series' own record ("L'assassin royal" by Gaudin and Sieurac) takes the kind of its
        // volumes when they credit every person it does, two at least: a comic is credited to a
        // writer and an artist, while Hugo's novel, credited to Hugo alone, never becomes the
        // comic adaptation that also names him.
        for (int i = 0; i < settled.size(); i++) {
            CatalogResult r = settled.get(i);
            List<List<String>> credited = names(r.authors());
            List<CatalogResult> volumes = keys.get(i) == null ? drawn.get(groupTitle(r.title())) : null;
            if (volumes == null || !"BOOK".equals(r.kind()) || isAniList(r) || credited.size() < 2
                    || !latin(r.authors())) {
                continue;
            }
            for (CatalogResult volume : volumes) {
                List<List<String>> theirs = names(volume.authors());
                if (credited.stream().allMatch(name -> credits(theirs, name))) {
                    settled.set(i, r.withKind(volume.kind()));
                    break;
                }
            }
        }
        return settled;
    }

    private record Member(CatalogResult result, SeriesVolume sv) {
    }

    /** Survivor of a group: French, then with a cover, then the most complete, then Open Library's work. */
    private static final Comparator<CatalogResult> SURVIVOR = Comparator
            .comparing((CatalogResult r) -> !isFrench(r.language()))
            .thenComparing(r -> !has(r.coverUrl()))
            .thenComparingInt(r -> -completeness(r))
            .thenComparing(r -> !"openlibrary".equals(r.provider()));

    private static int completeness(CatalogResult r) {
        return (has(r.isbn13()) ? 1 : 0) + (r.pageCount() != null ? 1 : 0) + (has(r.synopsis()) ? 1 : 0);
    }

    // --- derivatives ----------------------------------------------------------------------

    /**
     * Study guides and readings: a book <em>about</em> the work the reader typed. Only counted
     * when the title also matches the query — "Guide" alone is no study guide.
     */
    private static final List<String> STUDY = List.of("etude", "etudes", "analyse", "commentaire",
            "profil", "fiche de lecture", "resume", "lecture de", "comprendre");

    /**
     * Words that make a study guide only when somebody else than the best-ranked work's author
     * wrote it: "Le Guide du voyageur galactique" is Douglas Adams's novel, not a guide to it.
     */
    private static final List<String> STUDY_BY_OTHERS = List.of("guide", "lire", "dossier");

    /** "Étudier « Tintin au Tibet »", "Étude sur J. K. Rowling…", "Une lecture de…". */
    private static final Pattern STUDY_OPENING = Pattern.compile("^(?:etudier|etude sur|une lecture de) ");

    /** A segment of a title between quotation marks: the work the book is about. */
    private static final Pattern QUOTED = Pattern.compile("[«\"“”]\\s*([^«»\"“”]+?)\\s*[»\"“”]");

    /**
     * Colouring and activity books, calendars and merchandise: printed matter that borrows a
     * franchise's name. Self-published colouring books credit themselves "sara coloring", so the
     * credit line is read too.
     */
    private static final List<String> ACTIVITY = List.of("coloring", "colouring", "coloriage",
            "agenda", "calendrier", "calendar", "carnet", "cahier d activites", "tarot", "poster",
            "posters", "sticker", "stickers", "autocollants", "puzzle", "puzzles", "quiz");

    /** Spin-offs a reader asks for by name: the magazine, the anime comics, "One Piece Party". */
    private static final List<String> SPIN_OFF = List.of("magazine", "anime comics", "party");

    /** Exhibition catalogues: the BnF holds one for every anniversary of every writer. */
    private static final List<String> EXHIBITION = List.of("exposition", "catalogue");

    /** Box sets of books the page already lists one by one. */
    private static final List<String> BOX_SET = List.of("coffret", "box set", "collection set",
            "books collection", "gde collection", "grande collection");

    /** Whether a result is derived from what was asked rather than being it. Unless the query names the marker. */
    private static boolean isDerivative(CatalogResult r, int tier, Query query) {
        String title = fold(r.title());
        if (tier >= ALL_WORDS && (anyMarker(title, STUDY, query) || STUDY_OPENING.matcher(title).find()
                || quotesTheQuery(r.title(), query))) {
            return true;
        }
        return anyMarker(title + ' ' + fold(r.authors()), ACTIVITY, query)
                || anyMarker(title, SPIN_OFF, query)
                || anyMarker(title, EXHIBITION, query)
                || anyMarker(title, BOX_SET, query);
    }

    /**
     * Whether the title holds the query between quotation marks next to words of its own: the
     * Douglas DC-3 of "Tintin au Tibet" is a book about the album, not the album.
     */
    private static boolean quotesTheQuery(String title, Query query) {
        if (title == null || query.isEmpty()) {
            return false;
        }
        Matcher quoted = QUOTED.matcher(title);
        while (quoted.find()) {
            List<String> inside = tokens(quoted.group(1));
            String outside = fold(title.substring(0, quoted.start()) + ' ' + title.substring(quoted.end()));
            if (!outside.isEmpty() && String.join(" ", inside).contains(String.join(" ", query.words()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyMarker(String folded, List<String> markers, Query query) {
        for (String marker : markers) {
            if (contains(folded, marker) && !query.names(marker)) {
                return true;
            }
        }
        return false;
    }

    // --- authors --------------------------------------------------------------------------

    /**
     * Whether the query names a person rather than a title: two words at least, wholly the name
     * of one person credited on results that are not titled after them — "victor hugo", not
     * "one piece" (a colouring book credited to "Óne Píece" is titled after its subject).
     *
     * @return that person's name as folded words, or {@code null}
     */
    private static List<String> personNamed(Query query, List<Entry> entries) {
        if (query.words().size() < 2) {
            return null;
        }
        int works = 0;
        for (Entry entry : entries) {
            if (entry.titleTier < PHRASE && oneCreditNames(entry.result.authors(), query.words())) {
                works++;
            }
        }
        return works >= 2 ? query.words() : null;
    }

    /** The credited names of the best-ranked genuine work, when they are in Latin letters. */
    private static List<List<String>> canonicalAuthor(List<Entry> entries) {
        for (Entry entry : entries) {
            if (entry.derivative || entry.level <= 0) {
                continue;
            }
            List<List<String>> names = names(entry.result.authors());
            return names.isEmpty() || !latin(entry.result.authors()) ? null : names;
        }
        return null;
    }

    /** Whether one of the credited names, on its own, holds every word of the query. */
    static boolean oneCreditNames(String authors, List<String> words) {
        if (words.isEmpty()) {
            return false;
        }
        for (List<String> name : names(authors)) {
            if (Set.copyOf(name).containsAll(words)) {
                return true;
            }
        }
        return false;
    }

    /** The credited names, each as its folded words. */
    static List<List<String>> names(String authors) {
        List<List<String>> names = new ArrayList<>();
        if (authors == null || authors.isBlank()) {
            return names;
        }
        for (String name : authors.split("\\s*[,;&]\\s*|\\s+(?:et|and)\\s+")) {
            List<String> words = List.of(fold(name).split(" "));
            if (!words.get(0).isEmpty()) {
                names.add(words);
            }
        }
        return names;
    }

    /**
     * Whether {@code name} is one of the credited persons: the same surname, and the same first
     * initial when both carry a given name — "J. K. Rowling" is "J.K. Rowling", "Brian Herbert"
     * is not "Frank Herbert".
     */
    private static boolean credits(List<List<String>> credited, List<String> name) {
        if (name.isEmpty() || name.get(0).isEmpty()) {
            return false;
        }
        for (List<String> other : credited) {
            if (samePerson(other, name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean samePerson(List<String> a, List<String> b) {
        if (!romanised(a.get(a.size() - 1)).equals(romanised(b.get(b.size() - 1)))) {
            return false;
        }
        return a.size() == 1 || b.size() == 1 || a.get(0).charAt(0) == b.get(0).charAt(0);
    }

    /**
     * A Japanese name as any romanisation writes it: the long vowels AniList spells out
     * ("Gotouge", "Endou") folded onto the short ones the French editions print ("Gotōge"
     * already folds to "gotoge", "Endō" to "endo").
     */
    private static String romanised(String folded) {
        return folded.replace("ou", "o").replace("oo", "o").replace("uu", "u");
    }

    private static boolean creditsAny(List<List<String>> credited, List<List<String>> wanted) {
        for (List<String> name : wanted) {
            if (credits(credited, name)) {
                return true;
            }
        }
        return false;
    }

    // --- volumes --------------------------------------------------------------------------

    /**
     * Emits the volumes of each series together and in order. A series is its volumes (same
     * folded series, a volume number) and, when the page holds it, the series itself: AniList's
     * entry, or a book result titled after the series. The run starts where its best-ranked
     * member stood, AniList's series first and its volumes right behind it; a book titled after
     * the series is not moved, and anchors the run only when the query names a volume. Then
     * that volume leads, then the series, then the rest of the run.
     */
    private static List<Entry> inVolumeOrder(List<Entry> all, Query query, Map<String, String> aliases,
            List<List<String>> canonical) {
        List<Entry> ranked = all;
        Map<String, Run> runs = new LinkedHashMap<>();
        for (Entry entry : ranked) {
            if (!entry.derivative && entry.volume != null && entry.seriesKey != null) {
                runs.computeIfAbsent(entry.seriesKey, k -> new Run()).volumes.add(entry);
            }
        }
        for (Map.Entry<String, Run> e : runs.entrySet()) {
            e.getValue().head = seriesEntry(ranked, e.getKey(), aliases);
        }
        Map<Entry, Run> byMember = new HashMap<>();
        for (Run run : runs.values()) {
            run.dropStrangers();
            if (run.volumes.isEmpty() || run.volumes.size() + (run.head == null ? 0 : 1) < 2) {
                continue;
            }
            run.volumes.sort(Comparator.comparingInt(v -> v.volume));
            run.strangersLast();
            run.volumes.forEach(v -> byMember.put(v, run));
            if (run.head != null && (query.volume() != null || isAniList(run.head.result))) {
                byMember.put(run.head, run);
            }
        }
        // A run the query is about keeps every volume, whatever its own title; a run of some
        // other series (the BnF's "Recueils de pièces imprimées concernant les colonies") is
        // trimmed like anything else.
        Set<Entry> inRuns = new HashSet<>();
        Query series = new Query(query.words().stream()
                .filter(w -> query.volume() == null || !w.equals(String.valueOf(query.volume()))).toList(),
                null, query.prefixAware());
        for (Map.Entry<Entry, Run> e : byMember.entrySet()) {
            Run run = e.getValue();
            boolean asked = run.head != null && isAniList(run.head.result)
                    || tier(series, tokens(run.volumes.get(0).seriesTitle)) >= ALL_WORDS;
            if (asked) {
                inRuns.add(e.getKey());
                if (run.head != null) {
                    inRuns.add(run.head);
                }
            }
        }
        ranked = trimmed(ranked, query, canonical, inRuns);

        List<Entry> out = new ArrayList<>(ranked.size());
        Set<Entry> emitted = new HashSet<>();
        for (Entry entry : ranked) {
            if (emitted.contains(entry)) {
                continue;
            }
            Run run = byMember.get(entry);
            if (run == null) {
                out.add(entry);
                emitted.add(entry);
                continue;
            }
            List<Entry> sequence = new ArrayList<>();
            if (query.volume() != null) {
                run.volumes.stream().filter(v -> v.volume.equals(query.volume()) && !run.strangers.contains(v))
                        .forEach(sequence::add);
                if (run.head != null) {
                    sequence.add(run.head);
                }
                run.volumes.stream().filter(v -> !v.volume.equals(query.volume()) || run.strangers.contains(v))
                        .forEach(sequence::add);
            } else {
                if (run.head != null && isAniList(run.head.result)) {
                    // AniList's entry is the series by definition and leads its volumes; a book
                    // titled after the series stays where it ranked ("Le cycle de Dune", a
                    // coverless record, is not to climb above "Dune").
                    sequence.add(run.head);
                }
                sequence.addAll(run.volumes);
            }
            for (Entry member : sequence) {
                if (emitted.add(member)) {
                    out.add(member.adoptKind(run.head));
                }
            }
        }
        return out;
    }

    /**
     * Drops the padding of a page that found what was asked. When a genuine result matches the
     * title (exact, prefix, phrase, or an author with the rest of the title), what matches less
     * than every query word goes, and so does an all-words match much longer than the query: "Au
     * nom du peuple français" is no answer to "le nom du vent". A foreign edition of the
     * best-ranked author's work goes when a French one is on the page, and a record with neither
     * an author nor a cover when five others have one. The volumes of a series run stay, whatever
     * their own title.
     */
    private static List<Entry> trimmed(List<Entry> ranked, Query query, List<List<String>> canonical,
            Set<Entry> inRuns) {
        boolean strong = ranked.stream().anyMatch(e -> !e.derivative && e.tier >= PHRASE);
        boolean frenchByCanonical = canonical != null && ranked.stream().anyMatch(e -> !e.derivative
                && e.tier >= PHRASE && isFrench(e.result.language())
                && creditsAny(names(e.result.authors()), canonical));
        int concise = 2 * content(query.words()).size() + 1;
        List<Entry> kept = new ArrayList<>(ranked.size());
        List<String> subject = content(query.words());
        for (Entry e : ranked) {
            boolean drop = false;
            if (strong && !subject.isEmpty() && !shares(e, subject)) {
                // Not one subject word of the query in its own title nor in its series.
                drop = true;
            } else if (strong && !inRuns.contains(e)) {
                drop = e.tier < ALL_WORDS || (e.tier == ALL_WORDS && e.titleLength > concise)
                        || (frenchByCanonical && e.volume == null && e.tier < EXACT
                                && !isFrench(e.result.language()) && !isUnknown(e.result.language())
                                && creditsAny(names(e.result.authors()), canonical));
            }
            if (!drop) {
                kept.add(e);
            }
        }
        long identifiable = kept.stream().filter(e -> has(e.result.authors()) || has(e.result.coverUrl())).count();
        if (identifiable >= 5) {
            kept.removeIf(e -> !isAniList(e.result) && !has(e.result.authors()) && !has(e.result.coverUrl()));
        }
        return kept;
    }

    private static boolean shares(Entry e, List<String> subject) {
        List<String> words = new ArrayList<>(tokens(e.partTitle != null ? e.partTitle : e.result.title()));
        words.addAll(tokens(e.result.title()));
        if (e.seriesTitle != null) {
            words.addAll(tokens(e.seriesTitle));
        }
        if (isAniList(e.result) || e.tier > e.titleTier) {
            // AniList's series, and a work matched by its credit ("Hernani" for "victor hugo").
            return true;
        }
        return subject.stream().anyMatch(words::contains);
    }

    /**
     * Inside one series, a volume Open Library credits in kanji only ("尾田栄一郎") shows the
     * Latin-script credit the other volumes carry — the most common one.
     */
    private static void lendLatinCredits(List<Entry> entries) {
        Map<String, Map<String, Integer>> credits = new HashMap<>();
        for (Entry e : entries) {
            if (e.seriesKey != null && latin(e.result.authors())) {
                credits.computeIfAbsent(e.seriesKey, k -> new HashMap<>()).merge(e.result.authors(), 1, Integer::sum);
            }
        }
        for (Entry e : entries) {
            Map<String, Integer> latinCredits = e.seriesKey == null ? null : credits.get(e.seriesKey);
            if (latinCredits == null || !has(e.result.authors()) || latin(e.result.authors())) {
                continue;
            }
            String lent = latinCredits.entrySet().stream().max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElseThrow();
            CatalogResult r = e.result;
            e.result = new CatalogResult(r.kind(), r.title(), lent, r.year(), r.coverUrl(), r.synopsis(),
                    r.isbn13(), r.publisher(), r.language(), r.releaseDate(), r.seriesTitle(),
                    r.volumeNumber(), r.pageCount(), r.provider(), r.providerRef(), r.popularity());
        }
    }

    /** The series itself: AniList's entry when it answered it, else the best-ranked result titled after it. */
    private static Entry seriesEntry(List<Entry> ranked, String key, Map<String, String> aliases) {
        for (Entry entry : ranked) {
            if (isAniList(entry.result) && key.equals(aliases.get(fold(entry.result.title())))) {
                return entry;
            }
        }
        for (Entry entry : ranked) {
            if (!entry.derivative && entry.volume == null && !isAniList(entry.result)
                    && key.equals(aliases.getOrDefault(groupTitle(entry.result.title()),
                            groupTitle(entry.result.title())))) {
                return entry;
            }
        }
        return null;
    }

    private static final class Run {
        Entry head;
        final List<Entry> volumes = new ArrayList<>();
        final Set<Entry> strangers = new HashSet<>();

        /**
         * Leaves out of the run the volumes credited to nobody the series is credited to — the
         * comic "Fantine" by Maxe L'Hermenier is volume 1 of a "Les misérables", not of Hugo's.
         * Names in another script cannot be compared and are kept.
         */
        /**
         * Puts last the volumes credited — in Latin letters — to nobody the series is credited
         * to: neither AniList's staff nor the credit most of the run carries. Open Library holds
         * an unofficial "Jujutsu Kaisen 12" credited to a character of the manga.
         */
        void strangersLast() {
            Map<String, Integer> counts = new HashMap<>();
            volumes.stream().map(v -> v.result.authors()).filter(CatalogRanker::latin)
                    .forEach(a -> counts.merge(a, 1, Integer::sum));
            List<List<String>> reference = new ArrayList<>();
            counts.entrySet().stream().max(Map.Entry.comparingByValue())
                    .ifPresent(e -> reference.addAll(names(e.getKey())));
            if (head != null && latin(head.result.authors())) {
                reference.addAll(names(head.result.authors()));
            }
            if (reference.isEmpty()) {
                return;
            }
            volumes.stream()
                    .filter(v -> latin(v.result.authors()) && !creditsAny(reference, names(v.result.authors())))
                    .forEach(strangers::add);
            volumes.removeAll(strangers);
            volumes.addAll(strangers);
        }

        void dropStrangers() {
            // AniList romanises its own way ("Koyoharu Gotouge" for the BnF's "Gotōge"): a manga
            // series keeps every volume the catalogues file under its name.
            if (head == null || isAniList(head.result) || !latin(head.result.authors())) {
                return;
            }
            List<List<String>> credited = names(head.result.authors());
            volumes.removeIf(v -> latin(v.result.authors())
                    && !creditsAny(credited, names(v.result.authors())));
        }
    }

    // --- one result -----------------------------------------------------------------------

    /** One work of the page: the editions grouped into it, and how it scores. */
    private static final class Entry {

        int order;
        final String family;
        final List<Member> members = new ArrayList<>();
        CatalogResult result;
        /** The survivor's own title, before it is shown as "Series - Tome N : Title". */
        String partTitle;
        SeriesVolume sv;
        /** Subject words in the closest-matching title: how much longer than the query it is. */
        int titleLength;
        String seriesKey;
        Integer volume;
        String seriesTitle;
        int evidence;
        boolean bnfCovered;
        int titleTier;
        int tier;
        double dice;
        boolean derivative;
        int level;
        double secondary;

        Entry(int order, String family) {
            this.order = order;
            this.family = family;
        }

        /** Picks the survivor and fills what it lacks from the editions collapsed into it. */
        void merge(Map<String, String> aliases) {
            List<CatalogResult> all = members.stream().map(Member::result).toList();
            CatalogResult best = all.stream().min(SURVIVOR).orElseThrow();
            SeriesVolume sv = members.stream().filter(m -> m.result() == best).findFirst()
                    .map(Member::sv).orElse(null);
            if (sv == null) {
                sv = members.stream().map(Member::sv).filter(java.util.Objects::nonNull).findFirst().orElse(null);
            }
            String kind = best.kind();
            String cover = best.coverUrl();
            String synopsis = best.synopsis();
            String isbn = best.isbn13();
            String publisher = best.publisher();
            String authors = best.authors();
            Integer year = best.year();
            Integer pages = best.pageCount();
            String series = best.seriesTitle();
            Integer volumeNumber = best.volumeNumber();
            Integer popularity = best.popularity();
            boolean bnfCovered = false;
            for (CatalogResult r : all) {
                kind = strongerKind(kind, r.kind());
                cover = has(cover) ? cover : r.coverUrl();
                synopsis = has(synopsis) ? synopsis : r.synopsis();
                isbn = has(isbn) ? isbn : r.isbn13();
                publisher = has(publisher) ? publisher : r.publisher();
                authors = readableAuthors(authors, r.authors());
                year = year != null ? year : r.year();
                if (r.pageCount() != null && (pages == null || r.pageCount() > pages)) {
                    pages = r.pageCount();
                }
                if (series == null && r.seriesTitle() != null && r.volumeNumber() != null) {
                    series = r.seriesTitle();
                    volumeNumber = r.volumeNumber();
                }
                if (r.popularity() != null && (popularity == null || r.popularity() > popularity)) {
                    popularity = r.popularity();
                }
                bnfCovered |= "bnf".equals(r.provider()) && has(r.coverUrl());
            }
            if (series == null && sv != null && sv.titleIsVolume()) {
                // "Blue Lock 1": the volume read off the title, carried so the reader adding it
                // files it under its series.
                series = sv.series();
                volumeNumber = sv.volume();
            }
            partTitle = best.title();
            String title = best.title();
            this.sv = sv;
            result = new CatalogResult(kind, title, authors, year, cover, synopsis, isbn, publisher,
                    best.language(), best.releaseDate(), series, volumeNumber, pages, best.provider(),
                    best.providerRef(), popularity);
            // How widely the work is published: Open Library's edition count, plus the other
            // editions of it this very page holds.
            evidence = (popularity == null ? 0 : popularity) + members.size() - 1;
            this.bnfCovered = bnfCovered;
            if (sv != null) {
                volume = sv.volume();
                seriesTitle = sv.series();
                String folded = fold(sv.series());
                seriesKey = aliases.getOrDefault(folded, folded);
            }
        }

        /**
         * The title shown. A comic or manga volume the catalogue titles by its part ("Romance
         * dawn") reads under its series, "One piece - Tome 1 : Romance dawn", and two wordings of
         * one volume ("One Piece. 1, À l'aube…", "ONE PIECE Tome 1") read as the one the reader
         * typed. A book always keeps its own title: "Le nom du vent", not "Chronique du tueur de
         * roi - Tome 1 : Le nom du vent".
         */
        Entry displayed() {
            String title = result.title();
            if (sv == null || title == null || isAniList(result) || "BOOK".equals(result.kind())) {
                return this;
            }
            if (!fold(title).contains(fold(sv.series())) && !VOLUME_WORD.matcher(title).find()) {
                title = sv.series() + " - Tome " + sv.volume() + " : " + title;
            } else if (members.size() > 1 && sv.titleIsVolume()
                    && !fold(title).startsWith(fold(sv.series() + " tome " + sv.volume()))) {
                title = sv.series() + " - Tome " + sv.volume();
            }
            if (!title.equals(result.title())) {
                CatalogResult r = result;
                result = new CatalogResult(r.kind(), title, r.authors(), r.year(), r.coverUrl(), r.synopsis(),
                        r.isbn13(), r.publisher(), r.language(), r.releaseDate(), r.seriesTitle(),
                        r.volumeNumber(), r.pageCount(), r.provider(), r.providerRef(), r.popularity());
            }
            return this;
        }

        /** A volume of a series catalogued as a comic or a manga is one too, when it credits the same people. */
        Entry adoptKind(Entry head) {
            if (head == null || head == this || "BOOK".equals(head.result.kind())
                    || strongerKind(result.kind(), head.result.kind()).equals(result.kind())) {
                return this;
            }
            List<List<String>> credited = names(head.result.authors());
            if (credited.isEmpty() || creditsAny(credited, names(result.authors()))) {
                result = result.withKind(strongerKind(result.kind(), head.result.kind()));
            }
            return this;
        }

        void scoreTitle(Query query, Map<String, String> aliases) {
            if (query.isEmpty()) {
                return;
            }
            // The title as the catalogue gave it: "Sous le vent du monde - Tome 2 : Le nom perdu du
            // soleil" must not match "le nom du vent" across its series and its part title.
            List<String> title = tokens(partTitle != null ? partTitle : result.title());
            titleTier = tier(query, title);
            dice = dice(query.words(), title);
            titleLength = content(title).size();
            if (seriesTitle != null && volume != null) {
                // "Fantine" is volume 1 of Les Misérables; "Blue Lock T05" is "blue lock 5".
                List<String> asVolume = tokens(seriesTitle + ' ' + volume);
                titleTier = Math.max(titleTier, tier(query, asVolume));
                dice = Math.max(dice, dice(query.words(), asVolume));
            }
            if (isAniList(result)) {
                for (Map.Entry<String, String> alias : aliases.entrySet()) {
                    if (alias.getValue().equals(fold(result.title()))) {
                        titleTier = Math.max(titleTier, tier(query, tokens(alias.getKey())));
                    }
                }
            }
            tier = Math.max(titleTier, authorTier(query, title));
        }

        /**
         * "l'étranger camus": the words naming a credited author — their surname at least — are
         * set aside and the rest is matched against the title, an exact match counting as an
         * exact title.
         */
        private int authorTier(Query query, List<String> title) {
            int best = NONE;
            for (List<String> name : names(result.authors())) {
                if (!query.words().contains(name.get(name.size() - 1))) {
                    continue;
                }
                List<String> rest = query.words().stream().filter(w -> !name.contains(w)).toList();
                if (rest.isEmpty() || rest.size() == query.words().size()) {
                    continue;
                }
                int t = tier(new Query(rest, query.volume(), query.prefixAware()), title);
                best = Math.max(best, t >= PREFIX ? EXACT : t >= ALL_WORDS ? PREFIX : NONE);
            }
            return best;
        }

        void scoreLevel(Query query, List<String> person) {
            if (oneCreditNames(result.authors(), query.words())) {
                // A work by the person typed: level with an exact title when the query is wholly
                // their name; a mere all-words match otherwise ("damasio", or a name that is
                // also a title, like "one piece").
                tier = Math.max(tier, person != null ? EXACT : ALL_WORDS);
            }
            level = 2 * tier;
            if (evidence >= LIFT && tier >= PARTIAL && tier < EXACT) {
                level += 2;
            }
            derivative = tier > NONE && isDerivative(result, tier, query);
            if (derivative) {
                level -= 3;
            }
            if (!has(result.authors()) && !isAniList(result)) {
                // Nobody credited: a record a reader can hardly tell apart, a full tier down.
                level -= 2;
            }
            int popular = evidence;
            if (bnfCovered && titleTier >= PHRASE) {
                popular = Math.max(popular, COVERED_PHRASE);
            }
            secondary = Math.log1p(popular)
                    + (isFrench(result.language()) ? 4 : isUnknown(result.language()) ? 0 : -4)
                    + (has(result.coverUrl()) ? 4 : 0)
                    + (has(result.isbn13()) ? 0.5 : 0) + (result.pageCount() != null ? 0.25 : 0)
                    + (has(result.synopsis()) ? 0.25 : 0)
                    + (tier == PARTIAL ? 6 * dice : 0);
        }

        /**
         * Measures the result against the best-ranked work's author: a book whose title carries
         * that author's name without crediting them is about them; a title match credited to
         * somebody else entirely is another book of the same name.
         */
        void judgeAgainst(List<List<String>> canonical, Query query) {
            if (derivative || tier == NONE) {
                return;
            }
            List<List<String>> credited = names(result.authors());
            if (creditsAny(credited, canonical)) {
                return;
            }
            List<String> first = canonical.get(0);
            String surname = first.get(first.size() - 1);
            boolean studied = tier >= ALL_WORDS && anyMarker(fold(result.title()), STUDY_BY_OTHERS, query);
            if (studied || surname.length() >= 3 && tokens(result.title()).contains(surname)) {
                derivative = true;
                level -= 3;
            } else if (tier >= PREFIX && volume == null && !credited.isEmpty() && latin(result.authors())) {
                level -= 2;
            }
        }
    }

    // --- matching -------------------------------------------------------------------------

    /** The words of a title or query: folded, the volume markers and a leading article dropped. */
    static List<String> tokens(String text) {
        List<String> words = new ArrayList<>();
        for (String word : fold(text).split(" ")) {
            if (word.isEmpty() || VOLUME_MARKERS.contains(word)) {
                continue;
            }
            words.add(isNumber(word) ? String.valueOf(Integer.parseInt(word.length() > 9 ? word.substring(0, 9) : word)) : word);
        }
        if (words.size() > 1 && ARTICLES.contains(words.get(0))) {
            words.remove(0);
        }
        return words;
    }

    /** Volume markers carry no meaning of their own: "tome 1", "vol. 1" and ". 1" are one volume. */
    private static final Set<String> VOLUME_MARKERS = Set.of("tome", "tomes", "t", "vol", "volume");

    private static final Set<String> ARTICLES = Set.of("le", "la", "les", "l", "un", "une", "des",
            "the", "a", "an");

    /** Words that carry no subject: a title sharing only these with the query shares nothing. */
    private static final Set<String> STOP_WORDS = Set.of("le", "la", "les", "l", "un", "une", "des",
            "du", "de", "d", "au", "aux", "et", "en", "a", "sur", "par", "pour", "the", "of", "and",
            "an", "to", "in");

    /**
     * The title tier of {@code title} for the query. Words are compared whole — "one piece 1"
     * must not find its "1" inside "113" — except, in a prefix-aware ranking, the last query
     * word, which may start a title word. A title much longer than the query that merely
     * contains its words is only a partial match: a 1723 decree mentioning "la vente" "au nom
     * du roy" is not a match for "le nom du vent".
     */
    private static int tier(Query query, List<String> title) {
        List<String> q = query.words();
        if (q.isEmpty() || title.isEmpty()) {
            return NONE;
        }
        if (title.size() >= q.size() && matchesAt(query, title, 0)) {
            return title.size() == q.size() ? EXACT : PREFIX;
        }
        boolean concise = content(title).size() <= 3 * Math.max(1, content(q).size()) + 2;
        for (int start = 1; start + q.size() <= title.size(); start++) {
            if (matchesAt(query, title, start)) {
                return concise ? PHRASE : PARTIAL;
            }
        }
        long present = 0;
        long contentPresent = 0;
        for (int i = 0; i < q.size(); i++) {
            int index = i;
            if (title.stream().anyMatch(t -> matches(query, index, t))) {
                present++;
                if (!STOP_WORDS.contains(q.get(i))) {
                    contentPresent++;
                }
            }
        }
        if (present == q.size()) {
            return concise ? ALL_WORDS : PARTIAL;
        }
        return contentPresent > 0 ? PARTIAL : NONE;
    }

    private static boolean matchesAt(Query query, List<String> title, int start) {
        for (int i = 0; i < query.words().size(); i++) {
            if (!matches(query, i, title.get(start + i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(Query query, int index, String titleWord) {
        String word = query.words().get(index);
        if (word.equals(titleWord)) {
            return true;
        }
        return query.prefixAware() && index == query.words().size() - 1 && word.length() >= 3
                && !isNumber(word) && titleWord.startsWith(word);
    }

    /** How much of the query and of the title the shared subject words cover (Dice coefficient). */
    private static double dice(List<String> query, List<String> title) {
        List<String> q = content(query);
        List<String> t = content(title);
        if (q.isEmpty() || t.isEmpty()) {
            return 0;
        }
        long shared = q.stream().filter(t::contains).count();
        return 2.0 * shared / (q.size() + t.size());
    }

    private static List<String> content(List<String> words) {
        return words.stream().filter(w -> !STOP_WORDS.contains(w)).toList();
    }

    // --- small helpers --------------------------------------------------------------------

    /**
     * Folds a label to its bare letters and digits: accents stripped, lower case, apostrophes,
     * hyphens and every other punctuation reduced to single spaces. Letters of any script are
     * kept, so a name written in kanji still folds to something.
     */
    static String fold(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String expanded = value.replace("Œ", "oe").replace("œ", "oe")
                .replace("Æ", "ae").replace("æ", "ae").replace("ß", "ss");
        return Normalizer.normalize(expanded, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }

    private static boolean contains(String folded, String marker) {
        return (' ' + folded + ' ').contains(' ' + marker + ' ');
    }

    private static boolean isNumber(String word) {
        return !word.isEmpty() && word.chars().allMatch(Character::isDigit);
    }

    private static boolean isAniList(CatalogResult r) {
        return "anilist".equals(r.provider());
    }

    private static boolean has(String value) {
        return value != null && !value.isBlank();
    }

    static boolean isFrench(String language) {
        return language != null && Set.of("fre", "fr", "fra").contains(language.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean isUnknown(String language) {
        return language == null || Set.of("", "und", "xxx", "mul", "zxx").contains(language.trim().toLowerCase(Locale.ROOT));
    }

    /** MANGA over COMIC over BOOK: every manga is also catalogued as a comic, never the reverse. */
    private static String strongerKind(String a, String b) {
        return rankOf(b) > rankOf(a) ? b : a;
    }

    private static int rankOf(String kind) {
        return "MANGA".equals(kind) ? 2 : "COMIC".equals(kind) ? 1 : 0;
    }

    private static boolean latin(String value) {
        return value != null && value.codePoints().anyMatch(c -> Character.isLetter(c)
                && Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN);
    }

    /**
     * The incumbent's credit line, unless it is written in a script the reader cannot type and
     * the newcomer's is in Latin letters: Open Library credits a manga to "尾田栄一郎" where the
     * BnF writes "Eiichirō Oda".
     */
    static String readableAuthors(String kept, String other) {
        String chosen = has(kept) ? kept : other;
        if (chosen != null && has(other) && !latin(chosen) && latin(other)) {
            return other;
        }
        return chosen;
    }
}
