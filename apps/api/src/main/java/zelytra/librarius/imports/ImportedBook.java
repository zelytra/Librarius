package zelytra.librarius.imports;

import zelytra.librarius.domain.LibraryStatus;

/** Normalized row coming from an import (scraping or file). */
public record ImportedBook(String title, String author, String coverUrl, LibraryStatus status) {
}
