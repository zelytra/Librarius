package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.Work;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class WorkRepository implements PanacheRepositoryBase<Work, UUID> {

    /**
     * The catalog work an entry describes, when the catalog already knows it.
     *
     * <p>Without this lookup every entry founds a work of its own, so the one-work-to-many-
     * editions structure of the schema never materialises: a reader who owns the paperback
     * and buys the collector's edition ends up with two unrelated works, and the two readers
     * of the same novel never share a catalog row — which
     * {@code PRODUCT § 3} says they should.
     *
     * <p>The key is the one the import path has always deduplicated on (kind, title,
     * authors), folded to lower case, plus the volume number: two volumes of the same run
     * carry the same series and often the same authors, and must not collapse into each
     * other. Everything below that — publisher, ISBN, page count, format — is precisely what
     * distinguishes two editions and therefore has no business in the key.
     *
     * @param volumeNumber the volume the entry describes; a null one matches only works that
     *                     carry none, a standalone novel never being a volume of anything
     * @return the matching work, or empty when the catalog does not know it yet
     */
    public Optional<Work> findMatch(Kind kind, String title, String authors,
            Integer volumeNumber) {
        if (kind == null || title == null || title.isBlank()) {
            return Optional.empty();
        }
        // `lower(title)` and not `lower(trim(title))`: the expression index shipped by V3 is
        // on the former, and a lookup on every add is not worth a sequential scan.
        String volumeClause = volumeNumber == null
                ? "w.volumeNumber is null"
                : "w.volumeNumber = :volume";
        TypedQuery<Work> query = getEntityManager()
                .createQuery("select w from Work w"
                        + " where w.kind = :kind"
                        + "   and lower(w.title) = :title"
                        + "   and lower(coalesce(w.authors, '')) = :authors"
                        + "   and " + volumeClause
                        + " order by w.createdAt asc, w.id asc", Work.class)
                .setParameter("kind", kind)
                .setParameter("title", fold(title))
                .setParameter("authors", fold(authors));
        if (volumeNumber != null) {
            query.setParameter("volume", volumeNumber);
        }
        // The oldest match wins: duplicates predating this lookup exist in the catalog, and
        // gathering later editions on the first of them keeps the choice stable.
        return query.setMaxResults(1).getResultList().stream().findFirst();
    }

    private static String fold(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
