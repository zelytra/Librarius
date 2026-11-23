package zelytra.librarius.account;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.web.ApiDtos.AdminUserDto;
import zelytra.librarius.web.ApiDtos.AdminUserPageDto;
import zelytra.librarius.web.ApiDtos.CountryCountDto;
import zelytra.librarius.web.PageRequest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * The admins' view of the members (#499): who has an account, since when, and how much they
 * keep here. Deleting one goes through {@link AccountDeletionService}, the same path as a
 * member deleting themself — Keycloak first, then every row.
 */
@ApplicationScoped
public class AdminUserService {

    @Inject
    EntityManager em;

    @Inject
    zelytra.librarius.trust.TrustEvaluator trust;

    /** The members, newest first; {@code query} narrows on username, display name or email. */
    @Transactional
    public AdminUserPageDto list(PageRequest window, String query) {
        String filter = query == null || query.isBlank() ? ""
                : " where lower(coalesce(u.username, '')) like :q or lower(coalesce(u.display_name, '')) like :q"
                        + " or lower(coalesce(u.email, '')) like :q";
        var rows = em.createNativeQuery("""
                        select u.id, u.username, u.display_name, u.email, u.created_at, u.avatar_version,
                               (select count(*) from library_item li where li.user_id = u.id),
                               (select max(a.last_seen_at) from user_activity a where a.user_id = u.id),
                               u.trusted, u.trust_override, u.admin
                        from app_user u""" + filter + " order by u.created_at desc nulls last, u.id");
        var count = em.createNativeQuery("select count(*) from app_user u" + filter);
        if (!filter.isEmpty()) {
            String like = "%" + query.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
            rows.setParameter("q", like);
            count.setParameter("q", like);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> result = rows.setFirstResult(window.offset()).setMaxResults(window.size()).getResultList();
        List<AdminUserDto> items = result.stream().map(row -> new AdminUserDto((String) row[0], (String) row[1],
                (String) row[2], (String) row[3], toOffset(row[4]),
                row[5] == null ? null : ((Number) row[5]).longValue(), ((Number) row[6]).longValue(),
                toOffset(row[7]), Boolean.TRUE.equals(row[8]), TrustMode.of((Boolean) row[9]).name(),
                Boolean.TRUE.equals(row[10]))).toList();
        return new AdminUserPageDto(items, window.page(), window.size(), ((Number) count.getSingleResult()).longValue());
    }

    /** Members per country they filled themselves (#573), the largest first: counts, never who. */
    @Transactional
    public List<CountryCountDto> countries() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        select country, count(*) from app_user where country is not null
                        group by country order by count(*) desc, country""").getResultList();
        return rows.stream().map(row -> new CountryCountDto((String) row[0], ((Number) row[1]).longValue())).toList();
    }

    /** How an admin decides a member's trust (#625): left to the evaluation, or kept one way. */
    public enum TrustMode {
        AUTO, TRUSTED, UNTRUSTED;

        static TrustMode of(Boolean override) {
            return override == null ? AUTO : override ? TRUSTED : UNTRUSTED;
        }

        Boolean override() {
            return this == AUTO ? null : this == TRUSTED;
        }
    }

    /**
     * Sets how a member's trust is decided, and applies it at once — {@code AUTO} included, which
     * hands the account back to the criteria right now rather than at the next daily run. False
     * for an unknown member.
     */
    @Transactional
    public boolean setTrust(String id, TrustMode mode) {
        AppUser user = em.find(AppUser.class, id);
        if (user == null) {
            return false;
        }
        user.trustOverride = mode.override();
        trust.evaluate(user, OffsetDateTime.now());
        return true;
    }

    @Transactional
    public boolean exists(String id) {
        return !em.createNativeQuery("select 1 from app_user where id = :id").setParameter("id", id)
                .getResultList().isEmpty();
    }

    private static OffsetDateTime toOffset(Object value) {
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        if (value instanceof java.time.Instant instant) {
            return instant.atOffset(ZoneOffset.UTC);
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(ZoneOffset.UTC);
        }
        return null;
    }
}
