package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.Author;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class AuthorRepository implements PanacheRepositoryBase<Author, UUID> {

    /**
     * The authors carrying one of these folded keys, keyed by it. Unknown keys are absent.
     *
     * <p>One query for the whole credit line rather than one per name: a work names a handful
     * of authors, and recording it must not cost a round-trip for each of them.
     *
     * @param keys keys as {@code AuthorNormalizer.key} produced them
     */
    public Map<String, Author> byKeys(Collection<String> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        Map<String, Author> found = new LinkedHashMap<>();
        for (Author author : list("nameKey in ?1", keys)) {
            found.put(author.nameKey, author);
        }
        return found;
    }
}
