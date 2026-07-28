package zelytra.librarius.web;

/**
 * Pagination window asked for by a client, already clamped to the bounds the API accepts.
 *
 * <p>Clamping rather than rejecting: a client asking for {@code size=10000} gets the 200
 * first rows instead of a 400 it would have no way to recover from. The envelope returned
 * alongside the items carries the effective {@code size}, so the caller can always tell
 * what it actually received.
 *
 * @param page zero-based page index
 * @param size number of items per page
 */
public record PageRequest(int page, int size) {

    /** Page size applied when the client does not ask for one. */
    public static final int DEFAULT_SIZE = 50;

    /** Hard ceiling on the page size, whatever the client asks for. */
    public static final int MAX_SIZE = 200;

    /**
     * Highest page index served. Beyond that the offset would overflow an {@code int};
     * nobody paginates that far, and the response is simply an empty page.
     */
    private static final int MAX_PAGE = 100_000;

    /** Builds a window from raw query parameters, clamping both of them. */
    public static PageRequest of(int page, int size) {
        return new PageRequest(
                Math.min(Math.max(page, 0), MAX_PAGE),
                Math.min(Math.max(size, 1), MAX_SIZE));
    }

    /** Index of the first row of the window, for {@code setFirstResult}. */
    public int offset() {
        return page * size;
    }
}
