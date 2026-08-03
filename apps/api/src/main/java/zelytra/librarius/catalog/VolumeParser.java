package zelytra.librarius.catalog;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls a volume number and its series title out of a catalogue title.
 *
 * <p>Book catalogues spell the volume in the title itself — "Astérix - Tome 1",
 * "Naruto, Vol. 5", "Lanfeust de Troy T5", "One Piece #12" — while the series is whatever
 * precedes that marker. AniList, by contrast, names the series alone and carries no volume, so
 * a title with no marker parses to nothing and the entry stays a standalone work.
 *
 * <p>The match is deliberately conservative: only the explicit markers {@code tome}, {@code vol},
 * {@code volume}, a lone {@code t}, or {@code #} count, each immediately followed by the number.
 * A bare trailing number ("Fahrenheit 451", "Catch 22") is never read as a volume.
 */
public final class VolumeParser {

    /**
     * A volume marker followed by its number. The lookbehind keeps the marker word off the tail
     * of a longer word ("revolver" is not "vol", the final t of "Fahrenheit" is not "t"), and the
     * two capture groups are the number for the word form and for the {@code #} form.
     */
    private static final Pattern MARKER = Pattern.compile(
            "(?i)(?<!\\p{L})(?:tomes?|volumes?|vol|t)\\.?\\s*(\\d{1,4})\\b|#\\s*(\\d{1,4})\\b"
                    // The BnF ISBD form "Série. N, sous-titre": a number after the title's period,
                    // followed by a comma. The comma (and the 3-digit cap) keep "Catch. 22" and
                    // "Foundation. 1951, …" from reading as volumes.
                    + "|\\.\\s+(\\d{1,3})(?=\\s*,)");

    private static final Pattern TRAILING_SEPARATORS = Pattern.compile("[\\s,;:.()\\-—]+$");

    private VolumeParser() {
    }

    /** The series title and volume number read off a title, either half {@code null} when absent. */
    public record Parsed(String seriesTitle, Integer volumeNumber) {
        private static final Parsed NONE = new Parsed(null, null);
    }

    public static Parsed parse(String title) {
        if (title == null || title.isBlank()) {
            return Parsed.NONE;
        }
        Matcher matcher = MARKER.matcher(title);
        if (!matcher.find()) {
            return Parsed.NONE;
        }
        String digits = matcher.group(1) != null ? matcher.group(1)
                : matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
        int volume;
        try {
            volume = Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return Parsed.NONE;
        }
        String series = TRAILING_SEPARATORS.matcher(title.substring(0, matcher.start()))
                .replaceAll("").trim();
        // A title that leads with the marker ("Tome 1", "Volume 3: …") names no series.
        return series.isEmpty() ? new Parsed(null, volume) : new Parsed(series, volume);
    }
}
