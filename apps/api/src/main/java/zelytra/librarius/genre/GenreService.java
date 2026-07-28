package zelytra.librarius.genre;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import zelytra.librarius.domain.Genre;
import zelytra.librarius.domain.repository.GenreRepository;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Turns the free-text genres of an entry into the shared {@code genre} rows.
 *
 * <p>Runtime counterpart of the backfill in {@code V6__normalized_genres.sql}: same split,
 * same fold, same alias table. A work written today and a work migrated from the free-text
 * column therefore land on the same rows, which is the whole point of the exercise — the
 * statistics group on the code, so two codes for one genre would split the ranking again.
 */
@ApplicationScoped
public class GenreService {

    @Inject
    GenreRepository genres;

    /**
     * Resolves a free-text genre list, creating the genres nobody has used yet.
     *
     * <p>Duplicates within the list collapse: "Fantasy, fantasy" attaches one genre, which
     * is what the {@code work_genre} primary key would enforce anyway.
     *
     * @param rawList the raw value, e.g. {@code "Fantasy, Aventure"}; may be null or blank
     * @return the genres to attach to the work, empty when the value names none
     */
    public Set<Genre> resolve(String rawList) {
        // Code as first written -> label of the wording it was first seen under.
        Map<String, String> wordings = new LinkedHashMap<>();
        for (String part : GenreNormalizer.parts(rawList)) {
            String code = GenreNormalizer.code(part);
            if (code != null) {
                wordings.putIfAbsent(code, GenreNormalizer.label(part));
            }
        }
        if (wordings.isEmpty()) {
            return Set.of();
        }

        Map<String, String> aliases = genres.aliasesOf(wordings.keySet());
        Map<String, String> canonical = new LinkedHashMap<>();
        wordings.forEach((code, label) ->
                canonical.putIfAbsent(aliases.getOrDefault(code, code), label));

        Map<String, Genre> known = genres.byCodes(canonical.keySet());
        Set<Genre> resolved = new LinkedHashSet<>();
        canonical.forEach((code, label) -> resolved.add(known.computeIfAbsent(code, missing -> {
            Genre created = new Genre();
            created.code = missing;
            created.label = label;
            genres.persist(created);
            return created;
        })));
        return resolved;
    }
}
