package zelytra.librarius.imports;

import zelytra.librarius.catalog.VolumeParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An imported title split into the title to store and the series it belongs to.
 *
 * <p>Two shapes are recognised. Goodreads appends the series in brackets — "The Way of Kings
 * (The Stormlight Archive, #1)" — and the title stays the part before them. Every other source
 * writes the volume into the title — "One Piece Tome 3", "Naruto T.12", "Frieren Vol. 2" —
 * which {@link VolumeParser} already reads for the catalog: the title is kept whole there, as
 * the reader typed it, and only the series and the volume are derived from it.
 */
record ImportTitle(String title, String seriesTitle, Integer volumeNumber) {

    /** "Title (Series, #3)" or "Title (Series #3)"; a decimal "#2.5" novella is no volume. */
    private static final Pattern GOODREADS_SERIES =
            Pattern.compile("^(.+?)\\s*\\(([^()]+?),?\\s*#(\\d{1,4})\\)\\s*$");

    /** Any trailing Goodreads series bracket, whatever its numbering. */
    private static final Pattern GOODREADS_ANY = Pattern.compile("\\([^()]*#[^()]*\\)\\s*$");

    /**
     * The other words Booknode numbers a run with, after the series and its comma: "La
     * Passe-miroir, Livre 1 : …", "À la place du cœur, Saison 3", "Aurora Squad, Épisode 2".
     * Only in that shape — the number ends the title or opens its subtitle — so "Le Livre des
     * heures" is no volume. Import-only: a catalog title is not written this way.
     */
    private static final Pattern OTHER_VOLUME_WORD = Pattern.compile(
            "^(.+?),\\s*(?iu:livre|saison|partie|[ée]pisode)\\s+(\\d{1,3})(?=\\s*(?:[:,]|$))");

    /** "Série, Tome 1.5 : …": a novella of the run, numbered between two of its volumes. */
    private static final Pattern DECIMAL_VOLUME = Pattern.compile(
            "^(.+?)[\\s,;:\\-—]+(?iu:tomes?|volumes?|vol\\.?)\\s*\\d{1,4}[.,]\\d+");

    static ImportTitle parse(String raw) {
        String title = raw == null ? null : raw.trim();
        if (title == null || title.isEmpty()) {
            return new ImportTitle(raw, null, null);
        }
        Matcher goodreads = GOODREADS_SERIES.matcher(title);
        if (goodreads.matches()) {
            return new ImportTitle(goodreads.group(1).trim(), goodreads.group(2).trim(),
                    Integer.valueOf(goodreads.group(3)));
        }
        if (GOODREADS_ANY.matcher(title).find()) {
            // A Goodreads series bracket the shape above refused — "#2.5", "#1-3": a novella or an
            // omnibus, numbered between or across volumes. It is no single volume, and reading
            // its "#2" as one would file it over the real second book.
            return new ImportTitle(title, null, null);
        }
        VolumeParser.Parsed parsed = VolumeParser.parse(title);
        if (parsed.seriesTitle() != null) {
            return new ImportTitle(title, parsed.seriesTitle(), parsed.volumeNumber());
        }
        Matcher decimal = DECIMAL_VOLUME.matcher(title);
        if (decimal.find()) {
            // In the series, without a number: it belongs to the run, and takes no volume's place.
            return new ImportTitle(title, decimal.group(1).trim(), null);
        }
        Matcher word = OTHER_VOLUME_WORD.matcher(title);
        if (word.find()) {
            return new ImportTitle(title, word.group(1).trim(), Integer.valueOf(word.group(2)));
        }
        return new ImportTitle(title, null, null);
    }
}
