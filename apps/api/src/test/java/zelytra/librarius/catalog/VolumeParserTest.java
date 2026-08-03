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
}
