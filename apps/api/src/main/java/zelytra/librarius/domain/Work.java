package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Catalog work: a novel, or a single manga volume. */
@Entity
@Table(name = "work")
public class Work {

    @Id
    @GeneratedValue
    public UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public Kind kind;

    @Column(nullable = false, length = 512)
    public String title;

    /**
     * Free-text credit line as the provider or the manual form wrote it, e.g.
     * {@code "Isaac Asimov, Robert Silverberg"}.
     *
     * <p>Denormalised label list of {@link #authors}, kept for the clients that still read it
     * through {@code BookView} and for both export formats — and, unlike the two other
     * denormalised labels, still part of the key {@code WorkRepository.findMatch}
     * deduplicates works on. Dropped once the front end goes through the author identifiers —
     * see V13.
     */
    @Column(name = "authors", length = 512)
    public String authorsText;

    /**
     * The people credited on the work — what the bibliography of an author is read from.
     *
     * <p>Lazy, and deliberately never read by {@code BookView}: touching it while rendering
     * a page of the collection would cost one query per item, exactly as for {@link #genres}.
     */
    @ManyToMany
    @JoinTable(name = "work_author",
            joinColumns = @JoinColumn(name = "work_id"),
            inverseJoinColumns = @JoinColumn(name = "author_id"))
    public Set<Author> authors = new LinkedHashSet<>();

    /**
     * Denormalised label of {@link #series}, kept for the clients that still read it.
     * Dropped once the front end goes through the series identifier — see V4.
     */
    @Column(name = "series_title", length = 512)
    public String seriesTitle;

    /** The run this work belongs to, {@code null} for a standalone title. */
    @ManyToOne
    @JoinColumn(name = "series_id")
    public Series series;

    @Column(name = "volume_number")
    public Integer volumeNumber;

    @Column(columnDefinition = "text")
    public String synopsis;

    /**
     * Free-text genres as the provider or the manual form wrote them, e.g.
     * {@code "Fantasy, Aventure"}.
     *
     * <p>Denormalised label list of {@link #genres}, kept for the clients that still read it
     * through {@code BookView}. Dropped once the front end goes through the genre codes —
     * see V6.
     */
    @Column(name = "genres", length = 512)
    public String genresText;

    /**
     * The normalised genres of the work — what the statistics group on and what the
     * collection filters by.
     *
     * <p>Lazy, and deliberately never read by {@code BookView}: touching it while rendering
     * a page of the collection would cost one query per item.
     */
    @ManyToMany
    @JoinTable(name = "work_genre",
            joinColumns = @JoinColumn(name = "work_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id"))
    public Set<Genre> genres = new LinkedHashSet<>();

    @Column(name = "original_year")
    public Integer originalYear;

    /**
     * Catalog the work was first entered from, {@code null} when it was typed by hand.
     *
     * <p>Held together with {@link #providerRef}: the pair names a record a provider can be
     * asked about again — the other editions of the title, a better cover — and either half
     * alone answers nothing. {@code ck_work_provider_reference} (V12) enforces that.
     */
    @Column(length = 32)
    public String provider;

    /** Identifier of the work in {@link #provider}'s catalog. Filled with it, or not at all. */
    @Column(name = "provider_ref", length = 255)
    public String providerRef;

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;
}
