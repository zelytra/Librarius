package zelytra.librarius.releases;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.UpcomingRelease;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.domain.repository.LibraryItemRepository.OwnedVolume;
import zelytra.librarius.domain.repository.SeriesFollowRepository;
import zelytra.librarius.domain.repository.SeriesRepository;
import zelytra.librarius.domain.repository.UpcomingReleaseRepository;
import zelytra.librarius.domain.repository.WishlistItemRepository;
import zelytra.librarius.web.ApiDtos.UpcomingReleaseDto;

import jakarta.transaction.Transactional;
import zelytra.librarius.domain.DatePrecision;
import zelytra.librarius.domain.ReleaseConfidence;
import zelytra.librarius.domain.ReleaseRegion;
import zelytra.librarius.domain.Series;
import zelytra.librarius.social.NotificationService;
import zelytra.librarius.web.ApiDtos.CurateReleaseDto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
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

    @Inject
    SeriesRepository series;

    @Inject
    NotificationService notifications;

    /**
     * The releases still ahead of the caller, soonest first.
     *
     * <p>{@code seriesId} narrows the caller's stake to that one run — a run they have no stake
     * in stays as invisible as {@code /api/series} keeps it. {@code authorId} instead lists the
     * runs that author is credited on, stake or not (#614): an author is shared catalog data, and
     * their page shows what is coming of their work to whoever opens it.
     *
     * @param kind     restrict to books or to mangas, {@code null} for both
     * @param seriesId one run of the caller's, {@code null} for all of them
     * @param authorId one author's runs, {@code null} for the caller's stake
     * @param limit    how many rows at most
     */
    public List<UpcomingReleaseDto> listForUser(String userId, Kind kind, UUID seriesId,
            UUID authorId, int limit) {
        Set<UUID> stake = authorId != null
                ? new LinkedHashSet<>(series.idsCreditedTo(authorId))
                : seriesWithAStake(userId);
        if (seriesId != null) {
            stake.retainAll(Set.of(seriesId));
        }
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

    /**
     * Enters a checked French date for a volume (#614): the "other" source no free API offers.
     *
     * <p>It takes over the announcement already stored for that volume on the French market,
     * whatever deduced it, rather than standing beside it; as a curated row, no refresh touches
     * it again.
     *
     * @return the announcement, or empty when the series is unknown
     */
    @Transactional
    public Optional<UpcomingReleaseDto> curate(CurateReleaseDto input) {
        Series run = series.findById(input.seriesId());
        if (run == null) {
            return Optional.empty();
        }
        UpcomingRelease release = releases.findAnnouncement(run.id, input.volumeNumber(), ReleaseRegion.FR)
                .orElseGet(UpcomingRelease::new);
        release.series = run;
        release.volumeNumber = input.volumeNumber();
        // No made-up title: without one, the app labels the row by its volume number.
        release.title = input.title() != null && !input.title().isBlank() ? input.title().trim() : null;
        release.releaseDate = input.releaseDate();
        release.datePrecision = DatePrecision.DAY;
        release.region = ReleaseRegion.FR;
        release.publisher = input.publisher() != null && !input.publisher().isBlank()
                ? input.publisher().trim()
                : release.publisher;
        release.source = UpcomingRelease.SOURCE_MANUAL;
        release.confidence = ReleaseConfidence.CONFIRMED;
        release.updatedAt = OffsetDateTime.now();
        if (release.id == null) {
            releases.persist(release);
            notifications.release(release.id, run.id);
        }
        return Optional.of(UpcomingReleaseDto.of(release));
    }

    /** Withdraws an announcement entered by mistake (#614); {@code false} when it does not exist. */
    @Transactional
    public boolean withdraw(UUID id) {
        return releases.deleteById(id);
    }
}
