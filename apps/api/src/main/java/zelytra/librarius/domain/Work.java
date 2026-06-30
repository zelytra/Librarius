package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Œuvre du catalogue : un roman, ou un tome de manga. */
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

    @Column(length = 512)
    public String authors;

    @Column(name = "series_title", length = 512)
    public String seriesTitle;

    @Column(name = "volume_number")
    public Integer volumeNumber;

    @Column(columnDefinition = "text")
    public String synopsis;

    @Column(length = 512)
    public String genres;

    @Column(name = "original_year")
    public Integer originalYear;

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;
}
