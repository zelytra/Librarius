package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.Report;

import java.util.UUID;

/**
 * Persistence of {@link Report}. Write-only: nothing here reads a report back for a caller,
 * because no endpoint in this issue does (#192). The revocation consumer (#195) is what will
 * query these rows, and it lands with its own read methods.
 */
@ApplicationScoped
public class ReportRepository implements PanacheRepositoryBase<Report, UUID> {
}
