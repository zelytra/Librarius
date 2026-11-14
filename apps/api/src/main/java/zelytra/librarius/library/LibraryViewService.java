package zelytra.librarius.library;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import zelytra.librarius.domain.LibraryView;
import zelytra.librarius.domain.LibraryViewEntry;
import zelytra.librarius.web.ApiDtos.LibraryViewDto;
import zelytra.librarius.web.ApiDtos.LibraryViewInput;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The reader's saved library views (#451) and the manual order of each one. Every read and
 * write is scoped by the owner: another reader's view is unknown, and an order only ever
 * places the owner's own titles.
 */
@ApplicationScoped
public class LibraryViewService {

    /** Views one reader keeps at most. */
    public static final int MAX_VIEWS = 20;

    /** Entries one manual order holds at most. */
    public static final int MAX_ENTRIES = 5000;

    @Inject
    EntityManager em;

    public List<LibraryViewDto> list(String userId) {
        List<LibraryView> views = em.createQuery(
                        "from LibraryView where userId = :u order by position, createdAt", LibraryView.class)
                .setParameter("u", userId).getResultList();
        if (views.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<String>> orders = em.createQuery("""
                        select ve from LibraryViewEntry ve where ve.viewId in :views order by ve.position""",
                        LibraryViewEntry.class)
                .setParameter("views", views.stream().map(v -> v.id).toList())
                .getResultList().stream()
                .collect(Collectors.groupingBy(ve -> ve.viewId, Collectors.mapping(LibraryViewService::key,
                        Collectors.toList())));
        return views.stream().map(v -> LibraryViewDto.of(v, orders.getOrDefault(v.id, List.of()))).toList();
    }

    public Optional<LibraryView> find(String userId, UUID id) {
        LibraryView view = id == null ? null : em.find(LibraryView.class, id);
        return view != null && view.userId.equals(userId) ? Optional.of(view) : Optional.empty();
    }

    /** A new view, last of the reader's; empty when they already keep {@link #MAX_VIEWS}. */
    @Transactional
    public Optional<LibraryViewDto> create(String userId, LibraryViewInput input) {
        long count = em.createQuery("select count(v) from LibraryView v where v.userId = :u", Long.class)
                .setParameter("u", userId).getSingleResult();
        if (count >= MAX_VIEWS) {
            return Optional.empty();
        }
        LibraryView view = new LibraryView();
        view.userId = userId;
        view.position = (int) count;
        apply(view, input);
        em.persist(view);
        return Optional.of(LibraryViewDto.of(view, List.of()));
    }

    @Transactional
    public Optional<LibraryViewDto> update(String userId, UUID id, LibraryViewInput input) {
        return find(userId, id).map(view -> {
            apply(view, input);
            return LibraryViewDto.of(view, order(view.id));
        });
    }

    @Transactional
    public boolean delete(String userId, UUID id) {
        return find(userId, id).map(view -> {
            em.remove(view);
            return true;
        }).orElse(false);
    }

    /**
     * Replaces the manual order of a view with {@code keys} — {@code item:<id>} and
     * {@code series:<id>} — in that order. A key naming nothing of the reader's, a duplicate or
     * a malformed one is dropped rather than refused: a stale tile in the reader's screen must
     * not cost them the rest of their arrangement.
     */
    @Transactional
    public Optional<LibraryViewDto> reorder(String userId, UUID id, List<String> keys) {
        return find(userId, id).map(view -> {
            Set<UUID> items = new HashSet<>(em.createQuery(
                            "select li.id from LibraryItem li where li.userId = :u", UUID.class)
                    .setParameter("u", userId).getResultList());
            em.createQuery("delete from LibraryViewEntry ve where ve.viewId = :v")
                    .setParameter("v", view.id).executeUpdate();
            Set<String> seen = new LinkedHashSet<>();
            List<String> kept = new ArrayList<>();
            for (String key : keys == null ? List.<String>of() : keys) {
                if (key == null || !seen.add(key) || kept.size() >= MAX_ENTRIES) {
                    continue;
                }
                LibraryViewEntry entry = entry(view.id, key, items);
                if (entry != null) {
                    entry.position = kept.size();
                    em.persist(entry);
                    kept.add(key);
                }
            }
            return LibraryViewDto.of(view, kept);
        });
    }

    private LibraryViewEntry entry(UUID viewId, String key, Set<UUID> items) {
        int colon = key.indexOf(':');
        if (colon < 0) {
            return null;
        }
        UUID target;
        try {
            target = UUID.fromString(key.substring(colon + 1));
        } catch (IllegalArgumentException e) {
            return null;
        }
        LibraryViewEntry entry = new LibraryViewEntry();
        entry.viewId = viewId;
        switch (key.substring(0, colon)) {
            case "item" -> {
                if (!items.contains(target)) {
                    return null;
                }
                entry.itemId = target;
            }
            case "series" -> {
                if (em.find(zelytra.librarius.domain.Series.class, target) == null) {
                    return null;
                }
                entry.seriesId = target;
            }
            default -> {
                return null;
            }
        }
        return entry;
    }

    private List<String> order(UUID viewId) {
        return em.createQuery("from LibraryViewEntry where viewId = :v order by position", LibraryViewEntry.class)
                .setParameter("v", viewId).getResultStream().map(LibraryViewService::key).toList();
    }

    private static String key(LibraryViewEntry entry) {
        return entry.itemId != null ? "item:" + entry.itemId : "series:" + entry.seriesId;
    }

    private static void apply(LibraryView view, LibraryViewInput input) {
        view.name = input.name().trim();
        view.kind = input.kind();
        view.shelf = input.shelf() == null || input.shelf().isBlank() ? null : input.shelf().trim();
        view.sort = input.sort() == null ? "recent" : input.sort();
        view.layout = input.layout() == null ? "grouped" : input.layout();
        view.groupByCategory = Boolean.TRUE.equals(input.groupByCategory());
    }
}
