package zelytra.librarius.imports;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lenient date parsing for imports: an ISO date, {@code dd/MM/yyyy}, or the French
 * "12 mars 2024" form Booknode renders. Anything it cannot read is a null date rather than a
 * failed import — a missing acquisition date is not worth losing a title over.
 */
final class ImportDates {

    private static final Map<String, Integer> FRENCH_MONTHS = Map.ofEntries(
            Map.entry("janvier", 1), Map.entry("février", 2), Map.entry("fevrier", 2),
            Map.entry("mars", 3), Map.entry("avril", 4), Map.entry("mai", 5), Map.entry("juin", 6),
            Map.entry("juillet", 7), Map.entry("août", 8), Map.entry("aout", 8),
            Map.entry("septembre", 9), Map.entry("octobre", 10), Map.entry("novembre", 11),
            Map.entry("décembre", 12), Map.entry("decembre", 12));

    private static final DateTimeFormatter[] NUMERIC = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("uuuu/MM/dd")};

    /** An ISO date sitting inside a larger string — Booknode's "2021-12-02 22:51:48" timestamp. */
    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    private ImportDates() {
    }

    static LocalDate parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        // A source that renders a full timestamp carries the date in an ISO run inside it;
        // take that before the whole-string formatters, which want a bare date.
        Matcher iso = ISO_DATE.matcher(s);
        if (iso.find()) {
            try {
                return LocalDate.parse(iso.group(1));
            } catch (RuntimeException ignored) {
                // A run of digits shaped like a date but out of range; fall through.
            }
        }
        for (DateTimeFormatter fmt : NUMERIC) {
            try {
                return LocalDate.parse(s, fmt);
            } catch (RuntimeException ignored) {
                // Not this shape; fall through to the next.
            }
        }
        return french(s);
    }

    private static LocalDate french(String s) {
        String[] parts = s.toLowerCase(Locale.FRENCH).replace(",", " ").trim().split("\\s+");
        if (parts.length < 3) {
            return null;
        }
        try {
            int day = Integer.parseInt(parts[0]);
            Integer month = FRENCH_MONTHS.get(parts[1]);
            int year = Integer.parseInt(parts[parts.length - 1]);
            return month == null ? null : LocalDate.of(year, month, day);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
