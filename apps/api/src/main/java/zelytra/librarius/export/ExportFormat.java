package zelytra.librarius.export;

import java.util.Arrays;
import java.util.Optional;

/** The two shapes {@code GET /api/export} hands an account back in. */
public enum ExportFormat {

    /** Complete, and the only one {@code POST /api/import/json} takes back. */
    JSON("json", "application/json;charset=UTF-8"),

    /** The book list, in the vocabulary the other libraries use. */
    CSV("csv", "text/csv;charset=UTF-8");

    public final String extension;
    public final String contentType;

    ExportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    /**
     * Resolves the {@code format} query parameter, case-insensitively.
     *
     * <p>Parsed by hand rather than bound as an enum: JAX-RS resolves an enum parameter
     * through {@code valueOf}, so {@code format=csv} — the spelling the contract documents —
     * would fail before the resource is even entered.
     *
     * @return the matching format, {@link #JSON} when nothing was asked for, or empty when
     *         the value is not one of ours
     */
    public static Optional<ExportFormat> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(JSON);
        }
        return Arrays.stream(values())
                .filter(f -> f.name().equalsIgnoreCase(value.trim()))
                .findFirst();
    }
}
