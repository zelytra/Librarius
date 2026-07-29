package zelytra.librarius.author;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import zelytra.librarius.domain.Author;
import zelytra.librarius.domain.repository.AuthorRepository;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Turns the free-text credit line of an entry into the shared {@code author} rows.
 *
 * <p>Runtime counterpart of the backfill in {@code V13__author_entities.sql}: same split,
 * same fold. A work recorded today and a work migrated from the free-text column therefore
 * land on the same rows, which is the whole point — a bibliography is read from
 * {@code work_author}, so two rows for one person would split it in half.
 *
 * <p>Without this service the tables would be dead on arrival: the backfill covers what the
 * catalog held when the migration ran, and every title added afterwards would carry a credit
 * line and no author.
 */
@ApplicationScoped
public class AuthorService {

    @Inject
    AuthorRepository authors;

    /**
     * Resolves a free-text credit line, creating the authors nobody has credited yet.
     *
     * <p>Duplicates within the line collapse: "Asimov, asimov" credits one person, which is
     * what the {@code work_author} primary key would enforce anyway.
     *
     * @param creditLine the raw value, e.g. {@code "Isaac Asimov, Robert Silverberg"}; may be
     *                   null or blank
     * @return the authors to credit on the work, empty when the line names none
     */
    public Set<Author> resolve(String creditLine) {
        // Folded key -> the spelling it was first seen under.
        Map<String, String> names = new LinkedHashMap<>();
        for (String part : AuthorNormalizer.parts(creditLine)) {
            String key = AuthorNormalizer.key(part);
            if (key != null) {
                names.putIfAbsent(key, AuthorNormalizer.name(part));
            }
        }
        if (names.isEmpty()) {
            return Set.of();
        }

        Map<String, Author> known = authors.byKeys(names.keySet());
        Set<Author> resolved = new LinkedHashSet<>();
        names.forEach((key, name) -> resolved.add(known.computeIfAbsent(key, missing -> {
            Author created = new Author();
            created.name = name;
            created.nameKey = missing;
            authors.persist(created);
            return created;
        })));
        return resolved;
    }
}
