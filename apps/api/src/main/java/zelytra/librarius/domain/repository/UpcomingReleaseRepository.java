package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.ReleaseRegion;
import zelytra.librarius.domain.UpcomingRelease;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UpcomingReleaseRepository implements PanacheRepositoryBase<UpcomingRelease, UUID> {

    /**
     * Announcements attached to the given series, series fetched along the way — the list
     * shows the run each release belongs to, so reading it lazily would cost one query per
     * row.
     *
     * <p>{@code from} is a coarse floor, not the exact rule: a row is kept when it carries
     * no date, or when its date is not older than that day. The precise "is it still ahead"
     * test needs the precision of each row and lives on
     * {@link UpcomingRelease#stillAhead(LocalDate)}; passing the first day of the current
     * month here keeps every row that test could still accept while leaving the bulk of the
     * table out of the scan.
     */
    public List<UpcomingRelease> listForSeries(Collection<UUID> seriesIds, LocalDate from) {
        if (seriesIds.isEmpty()) {
            return List.of();
        }
        return getEntityManager()
                .createQuery("""
                        select r from UpcomingRelease r
                          join fetch r.series s
                        where s.id in :seriesIds
                          and (r.releaseDate is null or r.releaseDate >= :from)
                        order by r.releaseDate asc nulls last, lower(s.title) asc,
                                 r.volumeNumber asc nulls last, r.id asc
                        """, UpcomingRelease.class)
                .setParameter("seriesIds", seriesIds)
                .setParameter("from", from)
                .getResultList();
    }

    /**
     * The announcement already stored for a volume on a market, if any — the key of
     * {@code uq_upcoming_release_volume}, which is what makes a refresh an update rather
     * than a pile of duplicates.
     */
    public Optional<UpcomingRelease> findAnnouncement(UUID seriesId, Integer volumeNumber,
            ReleaseRegion region) {
        String volumeClause = volumeNumber == null
                ? "r.volumeNumber is null"
                : "r.volumeNumber = :volume";
        var query = getEntityManager()
                .createQuery("select r from UpcomingRelease r where r.series.id = :seriesId"
                        + " and " + volumeClause + " and r.region = :region",
                        UpcomingRelease.class)
                .setParameter("seriesId", seriesId)
                .setParameter("region", region);
        if (volumeNumber != null) {
            query.setParameter("volume", volumeNumber);
        }
        return query.getResultList().stream().findFirst();
    }

    /**
     * Drops the announcements that have come out long enough ago to be of no use to anyone.
     *
     * <p>Reads already exclude them, so this only reclaims space — but the refresher writes
     * on every run and nothing else ever deletes, so without it the table only ever grows.
     * Curated rows are left alone: somebody entered them on purpose.
     */
    public long purgeReleased(LocalDate before) {
        return delete("releaseDate < ?1 and source <> ?2", before, UpcomingRelease.SOURCE_MANUAL);
    }
}
