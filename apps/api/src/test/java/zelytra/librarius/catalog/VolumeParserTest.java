package zelytra.librarius.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VolumeParserTest {

    @Test
    void readsTheSeriesAndVolumeOffACommonTitle() {
        assertEquals(new VolumeParser.Parsed("Astérix", 1), VolumeParser.parse("Astérix - Tome 1"));
    }

    @Test
    void acceptsTheAbbreviatedAndPunctuatedForms() {
        assertEquals(new VolumeParser.Parsed("Naruto", 5), VolumeParser.parse("Naruto, Vol. 5"));
        assertEquals(new VolumeParser.Parsed("Lanfeust de Troy", 5),
                VolumeParser.parse("Lanfeust de Troy T5"));
        assertEquals(new VolumeParser.Parsed("One Piece", 12), VolumeParser.parse("One Piece #12"));
    }

    @Test
    void leavesTheSeriesNullWhenTheTitleIsOnlyAMarker() {
        assertEquals(new VolumeParser.Parsed(null, 3), VolumeParser.parse("Volume 3"));
    }

    @Test
    void readsNothingFromATitleWithNoMarker() {
        assertEquals(new VolumeParser.Parsed(null, null), VolumeParser.parse("One Piece"));
        assertEquals(new VolumeParser.Parsed(null, null), VolumeParser.parse("The Hobbit"));
        // A bare trailing number is not a volume marker.
        assertEquals(new VolumeParser.Parsed(null, null), VolumeParser.parse("Fahrenheit 451"));
    }

    @Test
    void toleratesNullAndBlank() {
        assertEquals(new VolumeParser.Parsed(null, null), VolumeParser.parse(null));
        assertEquals(new VolumeParser.Parsed(null, null), VolumeParser.parse("   "));
    }

    @Test
    void readsTheBnfIsbdSeriesDotVolumeForm() {
        assertEquals(new VolumeParser.Parsed("One Piece", 1),
                VolumeParser.parse("One Piece. 1, À l'aube d'une grande aventure"));
        // A four-digit number after the period is a year, not a volume.
        assertEquals(new VolumeParser.Parsed(null, null),
                VolumeParser.parse("Foundation. 1951, a novel"));
        // No period, no ISBD volume: a number that is part of the title stays in it.
        assertEquals(new VolumeParser.Parsed(null, null), VolumeParser.parse("Catch 22"));
        // "No." abbreviates a number that belongs to the title.
        assertEquals(new VolumeParser.Parsed(null, null), VolumeParser.parse("Kaiju No. 8"));
        assertEquals(new VolumeParser.Parsed("Kaiju No. 8", 3), VolumeParser.parse("Kaiju No. 8 Tome 3"));
    }

    @Test
    void readsTheBnfVolumeThatEndsTheTitle() {
        // Live BnF titles, once the statement of responsibility is cut: most volumes end right on
        // the number, or carry their own title after a colon — neither carried a comma, so none
        // of them used to parse, and no BnF manga volume joined its series.
        assertEquals(new VolumeParser.Parsed("One Piece", 56), VolumeParser.parse("One Piece. 56"));
        assertEquals(new VolumeParser.Parsed("One piece party", 1),
                VolumeParser.parse("One piece party. 1"));
        assertEquals(new VolumeParser.Parsed("One Piece", 112),
                VolumeParser.parse("One Piece. 112 : le coffre"));
    }

    @Test
    void dropsTheMarkerWordsAndKeepsTheNumber() {
        assertEquals("one piece 1", VolumeParser.withoutMarkers("one piece tome 1"));
        assertEquals("Frieren 3", VolumeParser.withoutMarkers("Frieren Vol. 3"));
        assertEquals("Naruto 12", VolumeParser.withoutMarkers("Naruto T. 12"));
        // Words that merely start like a marker stay.
        assertEquals("Les tomates vertes", VolumeParser.withoutMarkers("Les tomates vertes"));
        assertEquals("Voltaire", VolumeParser.withoutMarkers("Voltaire"));
    }

    @Test
    void readsNoVolumeFromADecimalNumber() {
        // "Tome 1.5" is a novella between two volumes, not volume 1 (#642).
        assertEquals(new VolumeParser.Parsed(null, null), VolumeParser.parse("Insaisissable, Tome 1.5"));
        assertEquals(new VolumeParser.Parsed(null, null), VolumeParser.parse("Série T2,5"));
    }

    @Test
    void keepsTheClosingBracketOfASeriesTitle() {
        assertEquals(new VolumeParser.Parsed("U4 (BD)", 1), VolumeParser.parse("U4 (BD), Tome 1 : Jules"));
        // A bracket the marker opens is still not part of the series.
        assertEquals(new VolumeParser.Parsed("Naruto", 5), VolumeParser.parse("Naruto (Vol. 5)"));
    }

    /**
     * A long run of separators inside the series used to be scanned once per position by the
     * trailing-separator regex — quadratic on a title a member types (CodeQL, #642).
     */
    @Test
    void readsATitleWithALongRunOfSeparatorsInLinearTime() {
        String title = "a" + "\t".repeat(200_000) + "b, Tome 1";
        VolumeParser.Parsed parsed = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                java.time.Duration.ofSeconds(2), () -> VolumeParser.parse(title));
        assertEquals(Integer.valueOf(1), parsed.volumeNumber());
        assertEquals("a" + "\t".repeat(200_000) + "b", parsed.seriesTitle());
    }
}
