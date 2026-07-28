package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.Genre;
import zelytra.librarius.domain.GenreAlias;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class GenreRepository implements PanacheRepositoryBase<Genre, UUID> {

    /** The genres carrying one of these codes, keyed by code. Unknown codes are absent. */
    public Map<String, Genre> byCodes(Collection<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        Map<String, Genre> found = new LinkedHashMap<>();
        for (Genre genre : list("code in ?1", codes)) {
            found.put(genre.code, genre);
        }
        return found;
    }

    /**
     * Resolves provider wordings onto canonical codes.
     *
     * <p>One query for the whole list rather than one per wording: a work carries a handful
     * of genres, and creating it must not cost a round-trip for each of them.
     *
     * @param codes codes as {@code GenreNormalizer} produced them, before aliasing
     * @return the entries that are aliases, keyed by the code they were looked up under; a
     *         wording that is already canonical is simply absent
     */
    public Map<String, String> aliasesOf(Collection<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> aliases = new LinkedHashMap<>();
        List<GenreAlias> rows = getEntityManager()
                .createQuery("select a from GenreAlias a where a.alias in :codes", GenreAlias.class)
                .setParameter("codes", codes)
                .getResultList();
        for (GenreAlias alias : rows) {
            aliases.put(alias.alias, alias.code);
        }
        return aliases;
    }
}
