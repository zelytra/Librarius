package zelytra.librarius.catalog;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.Series;
import zelytra.librarius.domain.Work;
import zelytra.librarius.domain.repository.EditionRepository;
import zelytra.librarius.domain.repository.SeriesRepository;
import zelytra.librarius.domain.repository.WorkRepository;
import zelytra.librarius.genre.GenreService;
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

    @Inject
    SeriesRepository series;

    @Inject
    GenreService genres;

    public Edition createManualEdition(ManualBookDto dto) {
        Work work = new Work();
        work.kind = dto.kind();
        work.title = dto.title();
        work.authors = dto.authors();
        work.series = resolveSeries(dto.kind(), dto.seriesTitle());
        // Denormalised label of the series, still read by the clients that predate it.
        work.seriesTitle = work.series != null ? work.series.title : null;
        work.volumeNumber = dto.volumeNumber();
        work.synopsis = dto.synopsis();
        // The raw wording is kept for the clients still reading it, and normalised into the
        // genres the statistics and the collection filter go through.
        work.genresText = dto.genres();
        work.genres = genres.resolve(dto.genres());
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

    /**
     * Attaches the entry to its series, creating it on first sight. This is what turns the
     * free-text series title sent by the front end — or produced by a provider, AniList
     * exposing the series natively — into the shared {@code series} row every volume of the
     * run then points at.
     *
     * @return the series, or {@code null} for a standalone title
     */
    private Series resolveSeries(Kind kind, String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        return series.findByKindAndTitle(kind, title).orElseGet(() -> {
            Series created = new Series();
            created.kind = kind;
            created.title = title.trim();
            series.persist(created);
            return created;
        });
    }
}
