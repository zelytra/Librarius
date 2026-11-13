package zelytra.librarius.library;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.Ownership;
import zelytra.librarius.domain.WishPriority;
import zelytra.librarius.domain.WishlistItem;
import zelytra.librarius.domain.repository.WishlistItemRepository;

/**
 * Keeps the ownership of a title and the wishlist in agreement (#411).
 *
 * <p>The wishlist keeps its own table — a wish carries a priority and a price a title has no
 * room for — so {@link Ownership#WISHLIST} is the library side of a wish on the same edition:
 * setting it puts the edition on the wishlist, and leaving it takes the wish off again.
 * Moving to {@link Ownership#OWNED} in particular is "I bought it".
 */
@ApplicationScoped
public class OwnershipService {

    @Inject
    WishlistItemRepository wishes;

    /** Sets the ownership of a title the caller already resolved as theirs. */
    public void apply(LibraryItem item, Ownership ownership) {
        Ownership target = ownership != null ? ownership : Ownership.OWNED;
        item.ownership = target;
        if (target == Ownership.WISHLIST) {
            if (wishes.findByEdition(item.userId, item.edition.id).isEmpty()) {
                WishlistItem wish = new WishlistItem();
                wish.userId = item.userId;
                wish.edition = item.edition;
                wish.priority = WishPriority.SOON;
                wishes.persist(wish);
            }
        } else {
            wishes.deleteByEdition(item.userId, item.edition.id);
        }
    }
}
