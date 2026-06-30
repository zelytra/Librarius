package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/** Progression de lecture d'un titre possédé (1-1 avec library_item). */
@Entity
@Table(name = "reading_progress")
public class ReadingProgress {

    @Id
    @GeneratedValue
    public UUID id;

    @OneToOne(optional = false)
    @JoinColumn(name = "library_item_id")
    public LibraryItem libraryItem;

    @Column(name = "current_page")
    public Integer currentPage;

    public Integer percent;

    @Column(name = "started_at")
    public LocalDate startedAt;

    @Column(name = "finished_at")
    public LocalDate finishedAt;
}
