package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.ReadingProgress;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ReadingProgressRepository implements PanacheRepositoryBase<ReadingProgress, UUID> {

    public Optional<ReadingProgress> findByItem(UUID libraryItemId) {
        return find("libraryItem.id", libraryItemId).firstResultOptional();
    }
}
