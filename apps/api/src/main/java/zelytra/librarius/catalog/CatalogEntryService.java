package zelytra.librarius.catalog;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.Work;
import zelytra.librarius.domain.repository.EditionRepository;
import zelytra.librarius.domain.repository.WorkRepository;
import zelytra.librarius.web.ApiDtos.ManualBookDto;

/**
 * Crée une œuvre + édition à partir d'une saisie manuelle. Sert de point d'entrée
 * unique pour la collection et la liste de souhaits tant que le catalogue externe
 * (PR #4) n'est pas branché ; il fournira alors d'autres fabriques d'éditions.
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
