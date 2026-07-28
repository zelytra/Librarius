package zelytra.librarius.catalog;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.Work;
import zelytra.librarius.domain.repository.EditionRepository;
import zelytra.librarius.domain.repository.WorkRepository;
import zelytra.librarius.web.ApiDtos.ManualBookDto;

/**
 * Creates a work and its edition from a manual entry. Acts as the single entry point
 * for the library and the wishlist as long as the external catalog (PR #4) is not
 * wired in; it will then provide other edition factories.
 */
@ApplicationScoped
public class CatalogEntryService {

    @Inject
    WorkRepository works;

    @Inject
    EditionRepository editions;

    public Edition createManualEdition(ManualBookDto dto) {
        Work work = new Work();
        work.kind = dto.kind();
        work.title = dto.title();
        work.authors = dto.authors();
        work.seriesTitle = dto.seriesTitle();
        work.volumeNumber = dto.volumeNumber();
        work.synopsis = dto.synopsis();
        work.genres = dto.genres();
        work.originalYear = dto.originalYear();
        works.persist(work);

        Edition edition = new Edition();
        edition.work = work;
        edition.isbn13 = dto.isbn13();
        edition.publisher = dto.publisher();
        edition.language = dto.language();
        edition.pageCount = dto.pageCount();
        edition.coverUrl = dto.coverUrl();
        edition.format = dto.format();
        edition.releaseDate = dto.releaseDate();
        edition.provider = "manual";
        editions.persist(edition);

        return edition;
    }
}
