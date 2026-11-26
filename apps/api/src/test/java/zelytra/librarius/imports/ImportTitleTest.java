package zelytra.librarius.imports;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The series an imported title belongs to, read off the titles a real Booknode library carries (#642). */
class ImportTitleTest {

    private static void series(String raw, String series, Integer volume) {
        ImportTitle parsed = ImportTitle.parse(raw);
        assertEquals(raw, parsed.title(), "the title is kept whole");
        assertEquals(series, parsed.seriesTitle(), raw);
        assertEquals(volume, parsed.volumeNumber(), raw);
    }

    @Test
    void readsBooknodesOtherVolumeWordsAfterTheSeriesComma() {
        series("La Passe-miroir, Livre 1 : Les Fiancés de l'hiver", "La Passe-miroir", 1);
        series("Gods of men, Livre 2 : Temple of Sand", "Gods of men", 2);
        series("À la place du cœur, Saison 3", "À la place du cœur", 3);
        series("Aurora Squad, Épisode 2", "Aurora Squad", 2);
        series("Fabricant de larmes, Partie 1", "Fabricant de larmes", 1);
        series("Le Marchand de sable, Saison 2 : Quand la femme de sa vie resurgit de la nuit",
                "Le Marchand de sable", 2);
    }

    @Test
    void leavesTitlesThatOnlyContainSuchAWordAlone() {
        series("Le Livre des heures", null, null);
        series("Comme dans les livres", null, null);
        series("La Voleuse de livres", null, null);
        series("Chroniques d'Avonlea 1", null, null);
    }

    @Test
    void keepsTheTomeAsTheVolumeWhenASeasonNamesTheSeries() {
        series("Insaisissable - Saison 1, Tome 2 : Ne m'échappe pas", "Insaisissable - Saison 1", 2);
    }

    @Test
    void filesADecimalTomeUnderItsSeriesWithoutANumber() {
        // A ".5" novella belongs to the run but is no volume of it: read as volume 1, it sat over
        // the real first volume.
        series("Insaisissable - Saison 1, Tome 1.5 : Ne me Résiste pas", "Insaisissable - Saison 1", null);
        series("Insaisissable, Saison 2, Tome 3.5 : Believe Me", "Insaisissable, Saison 2", null);
    }

    @Test
    void keepsABracketInTheSeriesWhole() {
        series("U4 (BD), Tome 1 : Jules", "U4 (BD)", 1);
    }
}
