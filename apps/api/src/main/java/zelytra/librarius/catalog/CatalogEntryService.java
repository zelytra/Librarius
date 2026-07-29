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
 * Turns a manual entry into catalog rows: the work it describes, and one edition of it.
 * Acts as the single entry point for the library, the wishlist and the imports as long as
 * the external catalog (PR #4) is not wired in; it will then provide other edition
 * factories.
 *
 * <p>The work is deduplicated, the edition never is. That asymmetry is the whole point of
 * the split: one work, N materialisations of it.
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

    /**
     * Records an entry: the work it describes, and the edition of it the user handled.
     *
     * <p>The work is matched against the catalog before being created. Without that, every
     * entry founded a work of its own and the one-work-to-many-editions structure of the
     * schema never materialised: buying the collector's edition of a novel already on the
     * shelf produced two unrelated works, and two readers of the same title never shared a
     * catalog row. The edition, on the other hand, is always created — it is precisely what
     * differs from one entry to the next, and the collection is keyed on it.
     */
    public Edition createManualEdition(ManualBookDto dto) {
        Work work = works
                .findMatch(dto.kind(), dto.title(), dto.authors(), dto.volumeNumber())
                .map(known -> complete(known, dto))
                .orElseGet(() -> createWork(dto));

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

    /** A work the catalog did not know yet. */
    private Work createWork(ManualBookDto dto) {
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
        return work;
    }

    /**
     * Completes a work the catalog already holds with what the new entry knows and it does
     * not.
     *
     * <p>Only the empty fields are filled in. The row is shared by everyone owning the
     * title, so an entry typed in a hurry — or returned by a thinner provider — must never
     * overwrite a synopsis or a genre list somebody else's entry had already supplied.
     */
    private Work complete(Work work, ManualBookDto dto) {
        if (work.synopsis == null) {
            work.synopsis = dto.synopsis();
        }
        if (work.originalYear == null) {
            work.originalYear = dto.originalYear();
        }
        if (work.genresText == null && dto.genres() != null) {
            work.genresText = dto.genres();
            // Filled in place rather than replaced: the collection of a managed entity is a
            // Hibernate wrapper, and swapping it out is a good way to lose the diff it
            // tracks. It is empty anyway — a work with no wording carries no genre.
            work.genres.addAll(genres.resolve(dto.genres()));
        }
        if (work.series == null) {
            work.series = resolveSeries(dto.kind(), dto.seriesTitle());
            work.seriesTitle = work.series != null ? work.series.title : null;
        }
        return work;
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
