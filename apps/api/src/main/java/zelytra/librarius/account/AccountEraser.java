package zelytra.librarius.account;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import zelytra.librarius.domain.repository.AppUserRepository;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.domain.repository.RankCategoryRepository;
import zelytra.librarius.domain.repository.ReadingGoalRepository;
import zelytra.librarius.domain.repository.SeriesFollowRepository;
import zelytra.librarius.domain.repository.WishlistItemRepository;

/**
 * Erases everything one user owns, in one statement.
 *
 * <p>Deleting the {@code app_user} row is enough: every foreign key pointing at it is
 * {@code ON DELETE CASCADE}, and {@code reading_progress} cascades in turn from
 * {@code library_item}. Walking the tables by hand would be one more place to forget one the
 * day a table is added; the schema already carries the rule, so this reads it rather than
 * restating it.
 *
 * <p>What it deliberately leaves alone is the shared catalog — {@code work},
 * {@code edition}, {@code series}, {@code genre}. Those rows describe books, not people:
 * they are what every other user's collection is built on, and none of them references an
 * {@code app_user}.
 */
@ApplicationScoped
public class AccountEraser {

    @Inject
    AppUserRepository users;

    @Inject
    LibraryItemRepository items;

    @Inject
    WishlistItemRepository wishes;

    @Inject
    ReadingGoalRepository goals;

    @Inject
    RankCategoryRepository categories;

    @Inject
    SeriesFollowRepository follows;

    /**
     * What went, counted before it went.
     *
     * <p>Counters, not identifiers: they are what a confirmation screen shows and what the
     * deletion log records, and none of them says anything about who the user was or what
     * they read.
     */
    public record Erased(int libraryItems, int wishlistItems, int goals, int categories,
            int seriesFollows) {
    }

    /**
     * @return what the cascade took with it, or {@code null} when there was no such account
     */
    @Transactional
    public Erased erase(String userId) {
        if (users.findById(userId) == null) {
            return null;
        }
        Erased erased = new Erased(
                (int) items.count("userId", userId),
                (int) wishes.count("userId", userId),
                (int) goals.count("userId", userId),
                (int) categories.count("userId", userId),
                (int) follows.count("userId", userId));
        users.deleteById(userId);
        return erased;
    }
}
