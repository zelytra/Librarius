package zelytra.librarius.contribution;

import java.io.IOException;

/** Reads a store page (#620). The URL is always one {@link SaleStore#of} recognised. */
public interface StorePageFetcher {

    /** The page's HTML, or an {@link IOException} when the store cannot be read. */
    String fetch(String url) throws IOException;
}
