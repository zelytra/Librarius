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
        return new ImportTitle(title, parsed.seriesTitle(),
                parsed.seriesTitle() != null ? parsed.volumeNumber() : null);
    }
}
