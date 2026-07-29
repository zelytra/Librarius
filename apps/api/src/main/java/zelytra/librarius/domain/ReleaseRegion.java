package zelytra.librarius.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Market an announced release date applies to.
 *
 * <p>The whole point of the table: a date is meaningless without the edition it belongs to.
 * A French reader waiting for tome 12 is not waiting for the Japanese one, and showing the
 * original date unlabelled is the confusion this enum exists to remove.
 *
 * <p>Kept to the three markets the application can name in its interface. A region it could
 * not label would be a code shown raw to the reader, which is no better than no label at
 * all — a release whose market cannot be established is dropped rather than filed under a
 * fourth, meaningless value.
 */
public enum ReleaseRegion {

    /** French edition. */
    FR,
    /** Original edition — Japanese for a manga. */
    JP,
    /** English edition. */
    EN;

    /**
     * Region an edition belongs to, read off its language.
     *
     * @param language ISO 639-1 code as the catalog stores it, possibly {@code null}
     * @return the market, or empty when the language says nothing usable
     */
    public static Optional<ReleaseRegion> ofLanguage(String language) {
        if (language == null || language.isBlank()) {
            return Optional.empty();
        }
        return switch (language.trim().toLowerCase(Locale.ROOT)) {
            case "fr", "fre", "fra" -> Optional.of(FR);
            case "ja", "jp", "jpn" -> Optional.of(JP);
            case "en", "eng" -> Optional.of(EN);
            default -> Optional.empty();
        };
    }
}
