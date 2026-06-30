package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.ReadingGoal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ReadingGoalRepository implements PanacheRepositoryBase<ReadingGoal, UUID> {

    public List<ReadingGoal> listByUser(String userId) {
        return list("userId = ?1 order by year desc", userId);
    }

    public Optional<ReadingGoal> findByUserAndYear(String userId, int year) {
        return find("userId = ?1 and year = ?2", userId, year).firstResultOptional();
    }
}
