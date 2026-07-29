package zelytra.librarius.releases;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.UpcomingRelease;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.domain.repository.LibraryItemRepository.OwnedVolume;
import zelytra.librarius.domain.repository.SeriesFollowRepository;
import zelytra.librarius.domain.repository.UpcomingReleaseRepository;
import zelytra.librarius.domain.repository.WishlistItemRepository;
import zelytra.librarius.web.ApiDtos.UpcomingReleaseDto;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What is coming out, for one reader.
 *
 * <p>The {@code upcoming_release} table is catalog data — it belongs to nobody. This is
 * where it becomes personal: the set of series the caller has a stake in is read from
 * <em>their</em> collection, <em>their</em> wishlist and <em>their</em> follows, and
 * nothing outside that set is ever returned. Two readers of the same run see the same
 * announcements; a reader with no stake in it sees none of them, exactly as
 * {@code /api/series} refuses to be a catalog browser.
 *
 * <p><strong>No provider is called here.</strong> Displaying the section costs one query
 * over rows {@code UpcomingReleaseRefresher} wrote earlier — the Open Library and AniList
 * quotas belong to the whole instance, and a screen that spends them on every render is
 * what this table exists to stop.
 */
@ApplicationScoped
public class UpcomingReleaseService {

    @Inject
    UpcomingReleaseRepository releases;

    @Inject
    LibraryItemRepository items;

    @Inject
    WishlistItemRepository wishes;

    @Inject
    SeriesFollowRepository follows;

    /**
     * The releases still ahead of the caller, soonest first.
     *
     * @param kind  restrict to books or to mangas, {@code null} for both
     * @param limit how many rows at most
     */
    public List<UpcomingReleaseDto> listForUser(String userId, Kind kind, int limit) {
        Set<UUID> stake = seriesWithAStake(userId);
        if (stake.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        Set<OwnedVolume> owned = items.ownedVolumes(userId);

        return releases.listForSeries(stake, today.withDayOfMonth(1)).stream()
                .filter(r -> kind == null || r.series.kind == kind)
                .filter(r -> r.stillAhead(today))
                .filter(r -> !alreadyOwned(owned, r))
                .limit(limit)
                .map(UpcomingReleaseDto::of)
                .toList();
    }

    /**
     * The series the user has a reason to hear about: those they own a volume of, those
     * they have a wish on, and those they follow.
     *
     * <p>Three queries rather than one union: each is backed by an index of its own, and
     * each of the three sets is the size of one user's shelves.
     */
    private Set<UUID> seriesWithAStake(String userId) {
        Set<UUID> stake = new LinkedHashSet<>(items.seriesIdsOwned(userId));
        stake.addAll(wishes.seriesIdsWished(userId));
        stake.addAll(follows.followedSeriesIds(userId));
        return stake;
    }

    /**
     * Whether the caller already has that very volume.
     *
     * <p>An announcement naming no volume can match nothing, and is therefore never hidden:
     * it says "the run continues", which stays true whatever the reader owns.
     */
    private static boolean alreadyOwned(Set<OwnedVolume> owned, UpcomingRelease release) {
        return release.volumeNumber != null
                && owned.contains(new OwnedVolume(release.series.id, release.volumeNumber));
    }
}
