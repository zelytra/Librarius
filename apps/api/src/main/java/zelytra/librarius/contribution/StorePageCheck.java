package zelytra.librarius.contribution;

import org.jsoup.Jsoup;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Whether a store page confirms a book (#620): it must carry the edition's ISBN — anywhere in
 * the page, structured data included, hyphens and spaces ignored — and its title, folded so
 * that case, accents and punctuation do not matter.
 *
 * <p>Pure, so it is tested on recorded HTML; fetching the page is {@link StorePageFetcher}'s job.
 */
public final class StorePageCheck {

    public enum Outcome { PASSED, NO_ISBN, ISBN_NOT_FOUND, TITLE_NOT_FOUND }

    /** A separator between two digits, as an ISBN is often printed ("978-2-07-…"). */
    private static final Pattern DIGIT_SEPARATOR = Pattern.compile("(?<=\\d)[-\\s\\u00a0](?=\\d)");

    private StorePageCheck() {
    }

    public static Outcome check(String html, String isbn13, String isbn10, String title) {
        if (isBlank(isbn13) && isBlank(isbn10)) {
            return Outcome.NO_ISBN;
        }
        String digits = DIGIT_SEPARATOR.matcher(html).replaceAll("");
        if (!containsNumber(digits, isbn13) && !containsNumber(digits, isbn10)) {
            return Outcome.ISBN_NOT_FOUND;
        }
        String text = fold(Jsoup.parse(html).text());
        return text.contains(fold(title)) ? Outcome.PASSED : Outcome.TITLE_NOT_FOUND;
    }

    /** {@code number} as a whole token of {@code haystack}, not inside a longer run of digits. */
    private static boolean containsNumber(String haystack, String number) {
        if (isBlank(number)) {
            return false;
        }
        return Pattern.compile("(?<![0-9])" + Pattern.quote(number) + "(?![0-9Xx])").matcher(haystack).find();
    }

    static String fold(String value) {
        String stripped = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return " " + stripped.replaceAll("[^a-z0-9]+", " ").trim() + " ";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
