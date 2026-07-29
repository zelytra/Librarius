package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Concrete edition of a work (1 work -> N editions). */
@Entity
@Table(name = "edition")
public class Edition {

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "work_id")
    public Work work;

    @Column(length = 13)
    public String isbn13;

    @Column(length = 10)
    public String isbn10;

    @Column(length = 255)
    public String publisher;

    @Column(length = 16)
    public String language;

    @Column(name = "page_count")
    public Integer pageCount;

    @Column(name = "cover_url", length = 1024)
    public String coverUrl;

    @Column(length = 32)
    public String format;

    @Column(name = "release_date")
    public LocalDate releaseDate;

    /**
     * Catalog this materialisation was entered from, {@code null} when it was typed by hand.
     *
     * <p>Same pair rule as {@link Work#provider}, enforced by
     * {@code ck_edition_provider_reference} (V12). Until then every edition carried
     * {@code "manual"} whatever it came from, and no reference next to it; V12 cleared those.
     */
    @Column(length = 32)
    public String provider;

    /** Identifier of the edition in {@link #provider}'s catalog. Filled with it, or not at all. */
    @Column(name = "provider_ref", length = 255)
    public String providerRef;

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;
}
