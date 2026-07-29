package zelytra.librarius.library;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.ReadingProgress;
import zelytra.librarius.domain.repository.EditionRepository;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.domain.repository.ReadingProgressRepository;
import zelytra.librarius.web.ApiDtos.EditionDto;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The editions of a work, and the ownership row that points at one of them.
 *
 * <p><strong>Scoping.</strong> An edition is shared catalog data — publisher, ISBN, page
 * count — and says nothing about who owns it. The ownership row is the opposite: it is the
 * caller's alone. So the listing is only opened to a work the caller owns something of (an
 * unknown work and someone else's answer the same 404), and a switch only ever moves the
 * caller's own item.
 */
@ApplicationScoped
public class EditionService {

    @Inject
    LibraryItemRepository items;

    @Inject
    EditionRepository editions;

    @Inject
    ReadingProgressRepository progresses;

    /** Why a switch was refused, so the resource can answer with the right status. */
    public enum Refusal {
        /** The item does not exist, or belongs to someone else. */
        UNKNOWN_ITEM,
        /** The target edition does not exist, or materialises another work. */
        NOT_AN_EDITION_OF_THIS_WORK,
        /** The caller already owns that edition — {@code UNIQUE(user_id, edition_id)}. */
        ALREADY_OWNED
    }

    /**
     * Outcome of a switch: the updated item, or the reason it was refused.
     *
     * @param item     the item, now pointing at the chosen edition; null when refused
     * @param refusal  why it was refused; null on success
     */
    public record SwitchOutcome(LibraryItem item, Refusal refusal) {

        public boolean ok() {
            return refusal == null;
        }

        static SwitchOutcome done(LibraryItem item) {
            return new SwitchOutcome(item, null);
        }

        static SwitchOutcome refused(Refusal refusal) {
            return new SwitchOutcome(null, refusal);
        }
    }

    /**
     * The editions of a work, marking the ones the caller already owns.
     *
     * @return empty when the caller owns nothing of that work, which the resource turns into
     *         a 404 — the same answer an unknown identifier gets
     */
    public Optional<List<EditionDto>> editionsOf(String userId, UUID workId) {
        if (workId == null || !items.ownsWork(userId, workId)) {
            return Optional.empty();
        }
        Set<UUID> owned = items.ownedEditionIds(userId, workId);
        return Optional.of(editions.listByWork(workId).stream()
                .map(edition -> EditionDto.of(edition, owned.contains(edition.id)))
                .toList());
    }

    /**
     * Points an owned title at another edition of the same work — "this is the one I
     * actually own".
     *
     * <p>Everything the row records about the reader is left alone: status, rating, review,
     * rank, acquisition date, and the reading dates. They describe the reader, not the
     * object; buying the hardcover does not un-read a book. The reading position is the one
     * exception, and {@link #reanchor} explains why.
     *
     * <p>Switching onto the edition already in force is a no-op rather than an error: a
     * double click on the section must not be an accident.
     */
    public SwitchOutcome switchEdition(String userId, UUID itemId, UUID editionId) {
        LibraryItem item = items.findOwned(userId, itemId).orElse(null);
        if (item == null) {
            return SwitchOutcome.refused(Refusal.UNKNOWN_ITEM);
        }
        if (editionId == null) {
            return SwitchOutcome.refused(Refusal.NOT_AN_EDITION_OF_THIS_WORK);
        }
        if (editionId.equals(item.edition.id)) {
            return SwitchOutcome.done(item);
        }
        Edition target = editions.findById(editionId);
        if (target == null || !target.work.id.equals(item.edition.work.id)) {
            return SwitchOutcome.refused(Refusal.NOT_AN_EDITION_OF_THIS_WORK);
        }
        // Checked rather than left to the constraint: the user is told which edition they
        // already own, where a violation would surface as a 500.
        if (items.findByEdition(userId, editionId).isPresent()) {
            return SwitchOutcome.refused(Refusal.ALREADY_OWNED);
        }

        item.edition = target;
        reanchor(progresses.findByItem(item.id).orElse(null), target.pageCount);
        return SwitchOutcome.done(item);
    }

    /**
     * Carries the reading position over to the new edition.
     *
     * <p>The percentage is the only measure that means the same thing in two paginations:
     * being 40 % into a novel is being 40 % into it whichever printing sits on the shelf. A
     * page number is not — page 120 of a 512-page paperback is nowhere near page 120 of a
     * 640-page hardcover. So the position is kept as its percentage and the page is
     * recomputed from the page count of the edition the user now owns, exactly the way
     * {@link ReadingProgressService} derives one side from the other on every save.
     *
     * <p>An edition with no page count leaves the page empty, which is honest: 40 % read,
     * and no way to name the page. And when no percentage was ever recorded — a raw page
     * typed on an edition that carried no page count either — the page is left untouched: it
     * is the only thing the reader ever told us, and re-deriving it would be inventing a
     * conversion from a total nobody knows.
     */
    private static void reanchor(ReadingProgress progress, Integer pageCount) {
        if (progress == null || progress.percent == null) {
            return;
        }
        progress.currentPage = ReadingProgress.pageOf(progress.percent, pageCount);
    }
}
