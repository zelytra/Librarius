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

    /**
     * A guess at the length when no catalog gave one (#414, V28): the series average, else a
     * figure per kind. Never read on its own — {@link #effectivePageCount()} prefers the real
     * count — and never exported, being no fact about the book.
     */
    @Column(name = "page_count_estimate")
    public Integer pageCountEstimate;

    /** Publisher's price of a new copy (#421, V29), read from the BnF record. */
    @Column(name = "list_price", precision = 8, scale = 2)
    public java.math.BigDecimal listPrice;

    /** ISO 4217 code of {@link #listPrice}, {@code EUR} for every price the BnF gives. */
    @Column(name = "list_price_currency", length = 3)
    public String listPriceCurrency;

    /** Last time the price lookup ran on this edition, found or not. */
    @Column(name = "price_checked_at")
    public OffsetDateTime priceCheckedAt;

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

    /** Last time the import enricher looked this edition up in the catalog (V23), or null. */
    @Column(name = "enrich_attempted_at")
    public OffsetDateTime enrichAttemptedAt;

    /** The length every figure is computed from: the real count, else the estimate. */
    public Integer effectivePageCount() {
        return pageCount != null ? pageCount : pageCountEstimate;
    }

    /** Whether {@link #effectivePageCount()} is a guess rather than a count. */
    public boolean pageCountEstimated() {
        return pageCount == null && pageCountEstimate != null;
    }

    /**
     * A new edition with no length gets the per-kind figure straight away, so it leaves no
     * hole in the stats between its creation and the next {@code PageCountEstimator} pass,
     * which refines it from the series.
     */
    @jakarta.persistence.PrePersist
    void estimateMissingLength() {
        if (pageCount == null && pageCountEstimate == null && work != null) {
            pageCountEstimate = zelytra.librarius.library.PageCountEstimator.defaultFor(work.kind);
        }
    }
}
