package zelytra.librarius.imports;

import zelytra.librarius.domain.Kind;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The genre a reader's own list names, when it names one.
 *
 * <p>Booknode carries no genre, and neither do the catalogs the enricher asks — but readers
 * file their books under lists, and many lists are genres: "romance", "jeunesse", "fantasy".
 * Those are the only genres an imported library has, and without them its "favourite genres"
 * stayed empty. Only lists that are recognisably a genre count: "maman", "Sarah JMass" or
 * "recommandé" are shelves, not genres, and stay out of the statistics.
 */
final class ImportGenres {

    /** Folded words a list may hold, and the genre each stands for, most specific first. */
    private static final Map<String, String> WORDS = new LinkedHashMap<>();

    static {
        WORDS.put("dark romance", "Dark romance");
        WORDS.put("new romance", "New romance");
        WORDS.put("romantasy", "Romantasy");
        WORDS.put("romance", "Romance");
        WORDS.put("jeunesse", "Jeunesse");
        WORDS.put("young adult", "Young adult");
        WORDS.put("fantasy", "Fantasy");
        WORDS.put("fantastique", "Fantastique");
        WORDS.put("science-fiction", "Science-fiction");
        WORDS.put("science fiction", "Science-fiction");
        WORDS.put("dystopie", "Dystopie");
        WORDS.put("thriller", "Thriller");
        WORDS.put("policier", "Policier");
        WORDS.put("polar", "Policier");
        WORDS.put("horreur", "Horreur");
        WORDS.put("historique", "Historique");
        WORDS.put("classique", "Classique");
        WORDS.put("poesie", "Poésie");
        WORDS.put("biographie", "Biographie");
        WORDS.put("essai", "Essai");
        WORDS.put("feel good", "Feel good");
        WORDS.put("feel-good", "Feel good");
        WORDS.put("contemporain", "Contemporain");
    }

    private ImportGenres() {
    }

    /**
     * The genre the list names, or — for a manga or a comic, which a list told by its kind —
     * that kind as a genre; null otherwise.
     */
    static String of(String list, Kind kind) {
        if (list != null) {
            String folded = Normalizer.normalize(list, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                    .toLowerCase(Locale.FRENCH).trim();
            for (Map.Entry<String, String> word : WORDS.entrySet()) {
                if (folded.equals(word.getKey()) || folded.startsWith(word.getKey() + " ")
                        || folded.endsWith(" " + word.getKey()) || folded.contains(" " + word.getKey() + " ")) {
                    return word.getValue();
                }
            }
        }
        if (kind == Kind.MANGA) {
            return "Manga";
        }
        if (kind == Kind.COMIC) {
            return "Bande dessinée";
        }
        return null;
    }
}
