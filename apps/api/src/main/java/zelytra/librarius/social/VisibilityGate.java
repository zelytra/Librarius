package zelytra.librarius.social;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.repository.AppUserRepository;
import zelytra.librarius.domain.repository.UserFollowRepository;

/**
 * The one authorization primitive that answers "may this caller see that member's shared
 * content?" (#201) — the rule the whole v1.2 social milestone leans on.
 *
 * <p>Until now every resource filtered only on {@code CurrentUser.id()}: a caller saw their own
 * rows and nothing else, so the question never arose. This is the first place one account's
 * content becomes readable by another, and it is exactly where a leak would happen — so the
 * rule lives here, once, and every cross-account endpoint calls {@link #canView} rather than
 * re-deriving it. It is intentionally the single site the block check (#203) will be added to,
 * without touching a caller a second time.
 *
 * <h2>The rule</h2>
 * <ul>
 *   <li>A caller always sees their <em>own</em> content.</li>
 *   <li>A {@link AppUser#publicAccount public} account is visible to any signed-in member, no
 *       follow required either way.</li>
 *   <li>A private account (the default) is visible only through a <em>mutual</em> follow:
 *       the caller follows the target <em>and</em> the target follows the caller back. A
 *       one-way follow reveals nothing.</li>
 *   <li>An id that is nobody is not visible — the caller cannot tell "does not exist" from
 *       "exists but not for you", which is the 404-not-403 convention every caller of this
 *       gate then applies.</li>
 * </ul>
 *
 * <p>This gate decides only the shared content — reviews, reading activity, the library. The
 * display name and the trusted badge (#186) are visible to anyone signed in regardless, and
 * the email, locale, time zone and private rating are never visible to anyone but the account
 * itself; neither of those surfaces goes through here.
 */
@ApplicationScoped
public class VisibilityGate {

    @Inject
    AppUserRepository users;

    @Inject
    UserFollowRepository follows;

    /**
     * Whether {@code viewerId} may read {@code targetId}'s shared content.
     *
     * @return {@code true} when the caller is the target, when the target is public, or when the
     *         two mutually follow; {@code false} when either id is null, the target does not
     *         exist, or the follow is one-way or absent. A caller of this method turns a
     *         {@code false} into a 404 (never a 403), per the API convention.
     */
    public boolean canView(String viewerId, String targetId) {
        if (viewerId == null || targetId == null) {
            return false;
        }
        // A caller always sees their own content, whatever the preference or the follow state.
        if (viewerId.equals(targetId)) {
            return true;
        }
        AppUser target = users.findById(targetId);
        if (target == null) {
            return false;
        }
        // #203 seam: when blocks land, a block denies here — before the public and mutual
        // checks below — so a blocked caller never sees even a public account. Adding it is
        // this one line and nothing at any call site.
        if (target.publicAccount) {
            return true;
        }
        return follows.isFollowing(viewerId, targetId)
                && follows.isFollowing(targetId, viewerId);
    }
}
