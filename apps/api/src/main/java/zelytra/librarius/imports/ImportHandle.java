package zelytra.librarius.imports;

import java.util.regex.Pattern;

/**
 * A provider's pseudo, as a scrape receives it (#585). It is concatenated into the provider's
 * URL and written to the logs, so it may hold only what a pseudo holds on Booknode or
 * Mangacollec: letters, digits, dot, dash and underscore — no slash, query, fragment, space or
 * line break that would steer the request to another path or forge a log line.
 */
public final class ImportHandle {

    private static final Pattern PSEUDO = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    private ImportHandle() {
    }

    /** Whether {@code handle}, once trimmed, is a pseudo; {@code ".."} alone is not. */
    public static boolean isValid(String handle) {
        if (handle == null) {
            return false;
        }
        String trimmed = handle.strip();
        return PSEUDO.matcher(trimmed).matches() && !trimmed.matches("\\.+");
    }
}
