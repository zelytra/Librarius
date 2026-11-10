package zelytra.librarius.scan;

/** Reads the ISBN a barcode or a hand-typed number carries, as an ISBN-13. */
public final class Isbn {

    private Isbn() {
    }

    /**
     * The ISBN-13 in {@code raw} — separators and an "ISBN" prefix allowed, an ISBN-10 converted —
     * or {@code null} when it is not one. The check digit is verified: a barcode that is not a
     * book's (a shop's own EAN, a misread) must not reach the catalogs as one.
     */
    public static String toIsbn13(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replaceFirst("(?i)^isbn[-\\s:]*(1[03][-\\s:]*)?", "").replaceAll("[\\s-]", "")
                .toUpperCase();
        if (s.matches("97[89]\\d{10}")) {
            return checkDigit13(s.substring(0, 12)) == s.charAt(12) - '0' ? s : null;
        }
        if (s.matches("\\d{9}[\\dX]")) {
            int sum = 0;
            for (int i = 0; i < 10; i++) {
                char c = s.charAt(i);
                sum += (c == 'X' ? 10 : c - '0') * (10 - i);
            }
            if (sum % 11 != 0) {
                return null;
            }
            String body = "978" + s.substring(0, 9);
            return body + checkDigit13(body);
        }
        return null;
    }

    private static int checkDigit13(String twelve) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += (twelve.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return (10 - sum % 10) % 10;
    }
}
