package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.Series;
import zelytra.librarius.domain.Work;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class SeriesRepository implements PanacheRepositoryBase<Series, UUID> {

    /**
     * Looks a series up by its title within a kind, case-insensitively — the same key as the
     * {@code uq_series_kind_title} unique index, so that importing volume 12 of a run
     * attaches it to the series volume 1 already created.
     */
    public Optional<Series> findByKindAndTitle(Kind kind, String title) {
        if (kind == null || title == null || title.isBlank()) {
            return Optional.empty();
        }
        return find("kind = ?1 and lower(title) = ?2", kind, title.trim().toLowerCase(Locale.ROOT))
                .firstResultOptional();
    }

    /**
     * How many volumes of a series the user owns, and how many of those they have read.
     *
     * @param seriesId the series the counters belong to
     * @param owned    distinct works of the series present in the user's collection
     * @param read     those of them marked {@link LibraryStatus#READ}
     */
    public record OwnershipCounters(UUID seriesId, long owned, long read) {
    }

    /**
     * Ownership counters for every series the user owns at least one volume of, in a single
     * grouped query — the series list must not cost one query per series.
     *
     * <p>Counting distinct works rather than items keeps two editions of the same volume
     * from inflating the run.
     */
    public List<OwnershipCounters> ownershipCountersByUser(String userId) {
        return getEntityManager()
                .createQuery("""
                        select w.series.id, count(distinct w.id),
                               count(distinct case when li.status = :read then w.id end)
                        from LibraryItem li
                          join li.edition e
                          join e.work w
                        where li.userId = :userId
                          and w.series is not null
                        group by w.series.id
                        """, Object[].class)
                .setParameter("userId", userId)
                .setParameter("read", LibraryStatus.READ)
                .getResultList()
                .stream()
                .map(row -> new OwnershipCounters((UUID) row[0], ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue()))
                .toList();
    }

    /** Loads several series at once, ordered by title so the list is stable. */
    public List<Series> listByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return list("id in ?1 order by lower(title) asc, id asc", ids);
    }

    /**
     * Every work attached to the series, in volume order. This is catalog data, shared by
     * all users: it is what tells the series screen that volume 4 exists at all, even when
     * nobody owns it yet.
     */
    public List<Work> worksOfSeries(UUID seriesId) {
        return getEntityManager()
                .createQuery("""
                        select w from Work w
                        where w.series.id = :seriesId
                        order by w.volumeNumber asc nulls last, lower(w.title) asc, w.id asc
                        """, Work.class)
                .setParameter("seriesId", seriesId)
                .getResultList();
    }

    /** Series still missing a volume total, capped so one refresh run stays bounded. */
    public List<Series> needingVolumeCount(int limit) {
        return find("totalVolumes is null").page(0, limit).list();
    }
}
