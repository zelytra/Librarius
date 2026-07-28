package zelytra.librarius.category;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import zelytra.librarius.domain.RankCategory;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.domain.repository.RankCategoryRepository;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The user's ranking categories: what can be created, renamed, deleted, and what happens to
 * the titles filed under a category that goes away.
 *
 * <p>Three rules, all enforced here rather than at the call sites:
 *
 * <ul>
 *   <li><b>A category belongs to its creator.</b> Everything is resolved through
 *       {@link RankCategoryRepository#findForUser}, which only ever returns the caller's own
 *       categories and the shared built-ins. Someone else's identifier is reported as
 *       unknown — a 404 rather than a 403, so nobody learns what other people collect.</li>
 *   <li><b>The built-ins are read-only.</b> {@code Or}, {@code Argent} and {@code Bronze}
 *       are shared rows ({@code user_id NULL}): renaming one would rename it for every
 *       account. They are visible to the caller, so refusing is a 403 — a 404 would deny
 *       the existence of a row the same caller just listed.</li>
 *   <li><b>One name per user.</b> The code derived from the label is the identity a filter
 *       uses ({@code /api/library?rank=}), and two shelves answering to the same code
 *       cannot be told apart. The check covers the built-ins too, since they share that
 *       code space.</li>
 * </ul>
 */
@ApplicationScoped
public class CategoryService {

    /** Colour a category gets when the caller supplies none: neutral, and readable on both themes. */
    private static final String DEFAULT_COLOR = "#9a8fa6";

    /** Custom categories sort after the three built-ins, which occupy 1 to 3. */
    private static final int CUSTOM_SORT_ORDER = 100;

    @Inject
    RankCategoryRepository categories;

    @Inject
    LibraryItemRepository items;

    public List<RankCategory> list(String userId) {
        return categories.listForUser(userId);
    }

    @Transactional
    public RankCategory create(String userId, String label, String color) {
        String name = label.trim();
        String code = slug(name);
        requireFreeCode(userId, code, null);

        RankCategory category = new RankCategory();
        category.userId = userId;
        category.label = name;
        category.code = code;
        category.color = color != null ? color : DEFAULT_COLOR;
        category.sortOrder = CUSTOM_SORT_ORDER;
        category.builtin = false;
        categories.persist(category);
        return category;
    }

    /**
     * Renames a category. The code follows the label: it is derived from it, and letting the
     * two drift would leave a category shown as "Coup de cœur" answering to a filter named
     * after whatever it used to be called. The titles keep their rank throughout — they
     * point at the category by identifier, not by code.
     */
    @Transactional
    public RankCategory rename(String userId, UUID id, String label) {
        RankCategory category = requireOwn(userId, id);
        String name = label.trim();
        String code = slug(name);
        requireFreeCode(userId, code, id);

        category.label = name;
        category.code = code;
        return category;
    }

    /**
     * Deletes a category and unfiles the titles that were in it.
     *
     * <p>Of the three ways out — cascading onto the titles, refusing while the category is
     * in use, or detaching them — only detaching is defensible. A rank is a label the reader
     * sticks on a book they own: deleting the label cannot delete the book, and refusing the
     * deletion would force the user to unrank a hundred titles by hand before being allowed
     * to drop a category they no longer want. The titles stay, they simply become unranked.
     */
    @Transactional
    public void delete(String userId, UUID id) {
        RankCategory category = requireOwn(userId, id);
        items.clearRank(userId, id);
        categories.delete(category);
    }

    /** Resolves a category the caller is allowed to write to, or fails the way it must. */
    private RankCategory requireOwn(String userId, UUID id) {
        RankCategory category = categories.findForUser(userId, id)
                .orElseThrow(() -> new NotFoundException("Unknown category: " + id));
        if (category.builtin) {
            throw new ForbiddenException("The built-in categories cannot be modified.");
        }
        return category;
    }

    private void requireFreeCode(String userId, String code, UUID excluding) {
        if (categories.codeTaken(userId, code, excluding)) {
            throw new ClientErrorException("A category with that name already exists.",
                    Response.Status.CONFLICT);
        }
    }

    /**
     * Turns a label into the code the API filters on: ligatures expanded, accents folded
     * onto ASCII, lower case, anything else collapsed into a single hyphen. "Coup de cœur"
     * and "COUP DE COEUR" therefore describe the same shelf.
     *
     * <p>A label made only of characters the fold drops — an emoji, a punctuation run — has
     * no usable code, so it falls back to one derived from the label itself rather than to a
     * constant every such label would share.
     *
     * <p>The fold is deliberately not {@code GenreNormalizer.code}: a category is not a
     * genre. A genre code is shared catalog data, ported verbatim from the SQL functions of
     * {@code V6__normalized_genres.sql} and kept in step with them by a parity test; a
     * category code is one user's private label, bounded by the 32 characters of
     * {@code rank_category.code} rather than by the 64 of {@code genre.code}. Reusing the
     * genre fold here would put category codes under a contract they have no part in.
     */
    static String slug(String label) {
        String expanded = label.replace("œ", "oe").replace("Œ", "OE")
                .replace("æ", "ae").replace("Æ", "AE");
        String folded = Normalizer.normalize(expanded, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.FRENCH)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        String code = folded.isBlank() ? "cat-" + Integer.toHexString(label.hashCode()) : folded;
        // The column holds 32 characters; a longer label keeps its own label, not its code.
        return code.length() > 32 ? code.substring(0, 32).replaceAll("-$", "") : code;
    }
}
