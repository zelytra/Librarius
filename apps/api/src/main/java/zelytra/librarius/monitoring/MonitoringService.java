package zelytra.librarius.monitoring;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import zelytra.librarius.web.ApiDtos.MonitoringDayDto;
import zelytra.librarius.web.ApiDtos.MonitoringDto;
import zelytra.librarius.web.ApiDtos.ServiceStatusDto;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The monitoring screen's figures (#491): over the last {@link #DAYS} days, the members'
 * reports and the errors recorded each day and the members active each day; the members
 * active right now; and whether the database and Keycloak answer.
 */
@ApplicationScoped
public class MonitoringService {

    static final int DAYS = 30;

    /** "Right now": seen within these minutes. */
    static final int NOW_MINUTES = 5;

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);

    @Inject
    EntityManager em;

    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    Optional<String> authServer;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(PROBE_TIMEOUT).build();

    @Transactional
    public MonitoringDto snapshot() {
        LocalDate today = LocalDate.now();
        LocalDate first = today.minusDays(DAYS - 1L);
        Map<LocalDate, long[]> byDay = new HashMap<>();
        rows("""
                        select cast(created_at as date), count(*) filter (where source = 'USER'),
                               count(*) filter (where source <> 'USER')
                        from feedback where created_at >= :first group by 1""", first)
                .forEach(row -> {
                    long[] counts = byDay.computeIfAbsent((LocalDate) row[0], d -> new long[3]);
                    counts[0] = ((Number) row[1]).longValue();
                    counts[1] = ((Number) row[2]).longValue();
                });
        rows("select day, count(*) from user_activity where day >= :first group by day", first)
                .forEach(row -> byDay.computeIfAbsent((LocalDate) row[0], d -> new long[3])[2] =
                        ((Number) row[1]).longValue());

        List<MonitoringDayDto> days = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            long[] counts = byDay.getOrDefault(day, new long[3]);
            days.add(new MonitoringDayDto(day, counts[0], counts[1], counts[2]));
        }
        long activeNow = ((Number) em.createNativeQuery(
                        "select count(distinct user_id) from user_activity where last_seen_at > now() - make_interval(mins => :minutes)")
                .setParameter("minutes", NOW_MINUTES)
                .getSingleResult()).longValue();
        return new MonitoringDto(activeNow, days, List.of(database(), keycloak()));
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String sql, LocalDate first) {
        return em.createNativeQuery(sql).setParameter("first", first).getResultList();
    }

    private ServiceStatusDto database() {
        try {
            em.createNativeQuery("select 1").getSingleResult();
            return new ServiceStatusDto("database", true, null);
        } catch (RuntimeException e) {
            return new ServiceStatusDto("database", false, e.getMessage());
        }
    }

    /** Keycloak answers when its realm's discovery document does. */
    private ServiceStatusDto keycloak() {
        if (authServer.isEmpty()) {
            return new ServiceStatusDto("keycloak", false, "no auth server configured");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(authServer.get().replaceAll("/+$", "") + "/.well-known/openid-configuration"))
                    .timeout(PROBE_TIMEOUT).GET().build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return new ServiceStatusDto("keycloak", status == 200, status == 200 ? null : "HTTP " + status);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new ServiceStatusDto("keycloak", false, e.getClass().getSimpleName());
        }
    }
}
