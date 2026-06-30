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

/** Édition concrète d'une œuvre (1 œuvre → N éditions). */
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

    @Column(length = 32)
    public String provider;

    @Column(name = "provider_ref", length = 255)
    public String providerRef;

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;
}
