package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Yearly reading goal, for one kind of book. */
@Entity
@Table(name = "reading_goal")
public class ReadingGoal {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "user_id", nullable = false, length = 255)
    public String userId;

    @Column(nullable = false)
    public Integer year;

    @Column(name = "target_count", nullable = false)
    public Integer targetCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public GoalUnit unit = GoalUnit.BOOKS;

    /** The kind of book the goal counts — roman, manga, BD… — one goal per kind and year (#393). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public Kind kind = Kind.BOOK;
}
