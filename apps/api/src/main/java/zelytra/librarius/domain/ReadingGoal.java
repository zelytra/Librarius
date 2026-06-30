package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Objectif de lecture annuel. */
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
}
