package zelytra.librarius.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.Series;
import zelytra.librarius.domain.Work;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.domain.repository.SeriesFollowRepository;
import zelytra.librarius.domain.repository.SeriesRepository;
import zelytra.librarius.domain.repository.SeriesRepository.OwnershipCounters;
import zelytra.librarius.web.ApiDtos.SeriesDetailDto;
import zelytra.librarius.web.ApiDtos.SeriesMissingDto;
import zelytra.librarius.web.ApiDtos.SeriesSummaryDto;
import zelytra.librarius.web.ApiDtos.SeriesVolumeDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reads a series through the eyes of one user: how much of the run they own, which volumes
 * are missing from it, and what is still ahead.
 *
 * <p><strong>Scoping.</strong> A series is shared catalog data, but this resource is not a
 * catalog browser: a series is visible to a user only once they own a volume of it or
 * follow it. Anything else answers 404 — the same answer an unknown identifier gets, so
 * that a caller cannot probe what other people collect.
 */
@ApplicationScoped
public class SeriesService {

    /**
     * Ceiling on the number of volumes listed for a series. A provider reporting a wrong
     * {@code total_volumes} must not turn into a response of unbounded size; no published
     * run comes close to this.
     */
    private static final int MAX_LISTED_VOLUMES = 1000;

    @Inject
    SeriesRepository series;

    @Inject
    SeriesFollowRepository follows;

    @Inject
    LibraryItemRepository items;

    /**
     * The series the user has a stake in: those they own at least one volume of, plus those
     * they follow, ordered by title.
     */
    public List<SeriesSummaryDto> listForUser(String userId) {
        Map<UUID, OwnershipCounters> counters = new HashMap<>();
        for (OwnershipCounters row : series.ownershipCountersByUser(userId)) {
            counters.put(row.seriesId(), row);
        }
        Set<UUID> followed = follows.followedSeriesIds(userId);

        Set<UUID> visible = new LinkedHashSet<>(counters.keySet());
        visible.addAll(followed);

        return series.listByIds(visible).stream()
                .map(s -> {
                    OwnershipCounters row = counters.get(s.id);
                    long owned = row != null ? row.owned() : 0;
                    long read = row != null ? row.read() : 0;
                    return SeriesSummaryDto.of(s, owned, read, followed.contains(s.id));
                })
                .toList();
    }

    /** Details of a series, volume by volume. Empty when it is not visible to the user. */
    public Optional<SeriesDetailDto> detail(String userId, UUID seriesId) {
        Series found = series.findById(seriesId);
        if (found == null) {
            return Optional.empty();
        }
        boolean followed = follows.isFollowing(userId, seriesId);
        List<LibraryItem> owned = items.listBySeries(userId, seriesId);
        if (owned.isEmpty() && !followed) {
            return Optional.empty();
        }

        Run run = Run.of(found, owned, series.worksOfSeries(seriesId));
        return Optional.of(SeriesDetailDto.of(found, run.ownedCount(), run.readCount(), followed,
                run.volumes()));
    }

    /** The holes in the user's run. Empty when the series is not visible to them. */
    public Optional<SeriesMissingDto> missing(String userId, UUID seriesId) {
        return detail(userId, seriesId).map(detail -> new SeriesMissingDto(
                detail.id(),
                detail.title(),
                detail.volumes().stream()
                        .filter(SeriesVolumeDto::missing)
                        .map(SeriesVolumeDto::volumeNumber)
                        .toList()));
    }

    /**
     * Starts following a series.
     *
     * @return false when the series is not visible to the user, which the resource turns
     *         into a 404
     */
    @Transactional
    public boolean follow(String userId, UUID seriesId) {
        if (!isVisible(userId, seriesId)) {
            return false;
        }
        follows.follow(userId, seriesId);
        return true;
    }

    /**
     * Stops following a series.
     *
     * @return false when the series is neither owned nor followed by the user
     */
    @Transactional
    public boolean unfollow(String userId, UUID seriesId) {
        if (!isVisible(userId, seriesId)) {
            return false;
        }
        follows.unfollow(userId, seriesId);
        return true;
    }

    private boolean isVisible(String userId, UUID seriesId) {
        return series.findById(seriesId) != null
                && (!items.listBySeries(userId, seriesId).isEmpty()
                        || follows.isFollowing(userId, seriesId));
    }

    /**
     * The volumes of a series laid out against one user's collection.
     *
     * @param volumes    one entry per volume, in order
     * @param ownedCount distinct works of the series the user owns
     * @param readCount  of those, the ones marked {@code READ}
     */
    private record Run(List<SeriesVolumeDto> volumes, long ownedCount, long readCount) {

        /**
         * Lays the run out.
         *
         * <p>The list runs from volume 1 — or from a lower volume when the series carries
         * one, a prologue numbered 0 being the usual case — to the highest volume anyone
         * knows about: the announced total, the last volume in the catalog, or the last one
         * the user owns, whichever is furthest. A volume the user does not own is
         * {@code missing} when it sits below the highest one they own (a hole in the run) and
         * {@code upcoming} above it (what is still ahead of them).
         *
         * <p>Works carrying no volume number cannot be placed in that sequence; the ones
         * the user owns are appended at the end so that they are neither lost from the
         * counters nor mistaken for a hole.
         */
        static Run of(Series series, List<LibraryItem> owned, List<Work> catalog) {
            Map<Integer, LibraryItem> ownedByVolume = new LinkedHashMap<>();
            List<LibraryItem> unnumbered = new ArrayList<>();
            Set<UUID> ownedWorks = new LinkedHashSet<>();
            Set<UUID> readWorks = new LinkedHashSet<>();

            for (LibraryItem item : owned) {
                Work work = item.edition.work;
                ownedWorks.add(work.id);
                if (item.status == LibraryStatus.READ) {
                    readWorks.add(work.id);
                }
                if (work.volumeNumber == null) {
                    unnumbered.add(item);
                } else {
                    ownedByVolume.putIfAbsent(work.volumeNumber, item);
                }
            }

            Map<Integer, Work> catalogByVolume = new LinkedHashMap<>();
            for (Work work : catalog) {
                if (work.volumeNumber != null) {
                    catalogByVolume.putIfAbsent(work.volumeNumber, work);
                }
            }

            int highestOwned = ownedByVolume.keySet().stream().mapToInt(Integer::intValue)
                    .filter(volume -> volume > 0).max().orElse(0);
            int highestKnown = catalogByVolume.keySet().stream().mapToInt(Integer::intValue)
                    .filter(volume -> volume > 0).max().orElse(0);
            int last = Math.max(highestOwned,
                    Math.max(highestKnown, series.totalVolumes != null ? series.totalVolumes : 0));
            last = Math.min(last, MAX_LISTED_VOLUMES);

            // Where the sequence opens. Volume 1 for an ordinary run, but a series can carry
            // a volume 0 — a prologue, a "tome 0" — that the reader owns, and a loop starting
            // at 1 would drop it: it is numbered, so it never reaches the unnumbered tail
            // below, yet the counters still count it. Opening on the lowest numbered volume
            // anyone knows about, capped at 1, keeps it in the grid. The floor bounds the
            // span so that a stray number cannot ask for an unbounded response.
            int lowestOwned = ownedByVolume.keySet().stream().mapToInt(Integer::intValue)
                    .min().orElse(Integer.MAX_VALUE);
            int lowestKnown = catalogByVolume.keySet().stream().mapToInt(Integer::intValue)
                    .min().orElse(Integer.MAX_VALUE);
            int lowest = Math.min(lowestOwned, lowestKnown);
            int first = lowest == Integer.MAX_VALUE ? 1
                    : Math.max(Math.min(1, lowest), last - MAX_LISTED_VOLUMES + 1);

            List<SeriesVolumeDto> volumes = new ArrayList<>();
            for (int volume = first; volume <= last; volume++) {
                LibraryItem item = ownedByVolume.get(volume);
                Work work = catalogByVolume.get(volume);
                boolean isOwned = item != null;
                volumes.add(new SeriesVolumeDto(
                        volume,
                        work != null ? work.title : null,
                        work != null ? work.id : null,
                        isOwned ? item.id : null,
                        isOwned,
                        isOwned && item.status == LibraryStatus.READ,
                        !isOwned && volume < highestOwned,
                        !isOwned && volume > highestOwned));
            }
            // A numbered volume the span could not reach — only a stray number puts an owned
            // one outside [first, last] — is still listed, so ownership is never dropped.
            for (Map.Entry<Integer, LibraryItem> entry : ownedByVolume.entrySet()) {
                int volume = entry.getKey();
                if (volume < first || volume > last) {
                    LibraryItem item = entry.getValue();
                    Work work = catalogByVolume.get(volume);
                    volumes.add(new SeriesVolumeDto(volume, work != null ? work.title : null,
                            work != null ? work.id : null, item.id, true,
                            item.status == LibraryStatus.READ, false, false));
                }
            }
            for (LibraryItem item : unnumbered) {
                Work work = item.edition.work;
                volumes.add(new SeriesVolumeDto(null, work.title, work.id, item.id, true,
                        item.status == LibraryStatus.READ, false, false));
            }

            return new Run(List.copyOf(volumes), ownedWorks.size(), readWorks.size());
        }
    }
}
