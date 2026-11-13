package zelytra.librarius.wishlist;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.Ownership;
import zelytra.librarius.domain.WishlistItem;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.domain.repository.WishlistItemRepository;
import zelytra.librarius.web.ApiDtos.LibraryItemDto;
import zelytra.librarius.web.ApiDtos.WishlistAcquireDto;
import zelytra.librarius.web.ApiDtos.WishlistItemDto;
import zelytra.librarius.web.ApiDtos.WishlistUpdateDto;

import java.util.Optional;
import java.util.UUID;

/**
 * Writes on the wishlist that are more than a single row.
 *
 * <p>Every method resolves the wish through {@link WishlistItemRepository#findOwned} and
 * reports "not the caller's" as an empty result: the resource turns that into a 404, the
 * same answer an unknown identifier gets, so that nobody can probe what other people want.
 */
@ApplicationScoped
public class WishlistService {

    @Inject
    WishlistItemRepository wishes;

    @Inject
    LibraryItemRepository items;

    /**
     * Replaces the editable fields of a wish. A null price or note clears it — the caller
     * sends the state it wants, not a delta.
     *
     * @return the updated wish, or empty when it does not exist or is not the caller's
     */
    /**
     * Removes a wish. A title of the library that pointed at it through
     * {@link Ownership#WISHLIST} is no longer wanted: it becomes {@link Ownership#NOT_OWNED}.
     *
     * @return whether the wish existed and was the caller's
     */
    @Transactional
    public boolean remove(String userId, UUID id) {
        WishlistItem wish = wishes.findOwned(userId, id).orElse(null);
        if (wish == null) {
            return false;
        }
        items.findByEdition(userId, wish.edition.id)
                .filter(item -> item.ownership == Ownership.WISHLIST)
                .ifPresent(item -> item.ownership = Ownership.NOT_OWNED);
        return wishes.deleteOwned(userId, id);
    }

    /**
     * A new wish on an edition that is already a title of the library, not owned in any
     * form: that title moves to {@link Ownership#WISHLIST}, the two staying in agreement.
     */
    @Transactional
    public void linkToLibrary(String userId, WishlistItem wish) {
        items.findByEdition(userId, wish.edition.id)
                .filter(item -> item.ownership == Ownership.NOT_OWNED)
                .ifPresent(item -> item.ownership = Ownership.WISHLIST);
    }

    @Transactional
    public Optional<WishlistItemDto> update(String userId, UUID id, WishlistUpdateDto dto) {
        return wishes.findOwned(userId, id).map(wish -> {
            wish.priority = dto.priority();
            wish.estimatedPrice = dto.estimatedPrice();
            wish.note = dto.note();
            return WishlistItemDto.of(wish);
        });
    }

    /**
     * Moves a wish into the collection: the user bought the book.
     *
     * <p>Two writes, one transaction. The wish is removed first, and through a bulk delete
     * so the statement reaches the database straight away; the insert that follows is the
     * one that can still fail. Either both land or neither does — a collection that refuses
     * the title must not also cost the user the wish that recorded it.
     *
     * <p>The owned title points at the very edition the wish pointed at, so buying a book
     * does not fork a second catalog row for something the user already described.
     *
     * @param dto status, rating and purchase date, all optional and {@code null}-tolerant
     * @return the freshly owned title, or empty when the wish is not the caller's
     */
    @Transactional
    public Optional<LibraryItemDto> acquire(String userId, UUID id, WishlistAcquireDto dto) {
        WishlistItem wish = wishes.findOwned(userId, id).orElse(null);
        if (wish == null) {
            return Optional.empty();
        }
        wishes.deleteOwned(userId, id);

        // Already a title of the library — read from a borrowed copy and wished since (#411):
        // buying it makes that title owned, keeping the reading history it carries.
        LibraryItem existing = items.findByEdition(userId, wish.edition.id).orElse(null);
        if (existing != null) {
            existing.ownership = Ownership.OWNED;
            if (dto != null && dto.status() != null) {
                existing.status = dto.status();
            }
            if (dto != null && dto.rating() != null) {
                existing.rating = dto.rating();
            }
            existing.acquiredAt = dto != null && dto.acquiredAt() != null ? dto.acquiredAt() : existing.acquiredAt;
            return Optional.of(LibraryItemDto.of(existing));
        }

        LibraryItem item = new LibraryItem();
        item.userId = userId;
        item.edition = wish.edition;
        item.status = dto != null && dto.status() != null ? dto.status() : LibraryStatus.TO_READ;
        item.ownership = Ownership.OWNED;
        item.rating = dto != null ? dto.rating() : null;
        item.acquiredAt = dto != null ? dto.acquiredAt() : null;
        items.persist(item);

        return Optional.of(LibraryItemDto.of(item));
    }
}
