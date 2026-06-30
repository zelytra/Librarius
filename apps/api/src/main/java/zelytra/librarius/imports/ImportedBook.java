package zelytra.librarius.imports;

import zelytra.librarius.domain.LibraryStatus;

/** Ligne normalisée issue d'un import (scraping ou fichier). */
public record ImportedBook(String title, String author, String coverUrl, LibraryStatus status) {
}
