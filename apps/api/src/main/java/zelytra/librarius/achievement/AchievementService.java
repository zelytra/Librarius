package zelytra.librarius.achievement;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import zelytra.librarius.achievement.Achievement.Metric;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.web.ApiDtos.AchievementDto;
import zelytra.librarius.web.ApiDtos.AchievementUnlockDto;
import zelytra.librarius.web.ApiDtos.AchievementsDto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Achievements, XP and levels (#287), server-authoritative: the client only reads them.
 *
 * <p>Evaluated when the reader looks — every call to {@link #overview} measures the library,
 * stores whatever tier was newly reached, and answers. Nothing on a write path waits for it, and
 * a write the screens follow with a refetch (they invalidate the statistics) sees its unlock on
 * that refetch. The first evaluation of an account is retroactive and silent: what a long-time
 * reader had already earned is unlocked as already seen.
 *
 * <p>One event cannot be read back from the data and is recorded where it happens:
 * {@link Achievement#COMEBACK}, an abandoned title read to the end ({@link #unlock}).
 */
@ApplicationScoped
public class AchievementService {

    @Inject
    EntityManager em;

    @Inject
    AchievementMetrics metrics;

    /** One stored unlock. */
    record Unlock(String code, int tier, OffsetDateTime unlockedAt, OffsetDateTime seenAt) {
    }

    /** Measures, unlocks what was reached, and describes where the reader stands. */
    @Transactional
    public AchievementsDto overview(String userId) {
        AppUser user = em.find(AppUser.class, userId);
        Map<Metric, Long> values = metrics.of(userId);
        Map<String, Unlock> stored = unlocks(userId);

        if (user != null) {
            OffsetDateTime now = OffsetDateTime.now();
            boolean silent = user.achievementsEvaluatedAt == null;
            for (Achievement achievement : Achievement.values()) {
                if (achievement.metric == Metric.EVENT) {
                    continue;
                }
                int reached = achievement.tierReached(values.getOrDefault(achievement.metric, 0L));
                for (int tier = 1; tier <= reached; tier++) {
                    String key = key(achievement.name(), tier);
                    if (!stored.containsKey(key)) {
                        insert(userId, achievement, tier, silent ? now : null);
                        stored.put(key, new Unlock(achievement.name(), tier, now, silent ? now : null));
                    }
                }
            }
            if (silent) {
                user.achievementsEvaluatedAt = now;
            }
        }
        return describe(values, stored);
    }

    /** Records an event-borne achievement — unseen, so the reader is told. Idempotent. */
    @Transactional
    public void unlock(String userId, Achievement achievement) {
        insert(userId, achievement, 1, null);
    }

    /** Marks every unlock the reader was shown as seen. */
    @Transactional
    public int markSeen(String userId) {
        return em.createNativeQuery("""
                update user_achievement set seen_at = now() where user_id = :u and seen_at is null""")
                .setParameter("u", userId)
                .executeUpdate();
    }

    private void insert(String userId, Achievement achievement, int tier, OffsetDateTime seenAt) {
        em.createNativeQuery("""
                insert into user_achievement (user_id, code, tier, unlocked_at, seen_at)
                values (:u, :code, :tier, now(), :seen)
                on conflict (user_id, code, tier) do nothing""")
                .setParameter("u", userId)
                .setParameter("code", achievement.name())
                .setParameter("tier", (short) tier)
                .setParameter("seen", seenAt)
                .executeUpdate();
    }

    private Map<String, Unlock> unlocks(String userId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                select code, tier, unlocked_at, seen_at from user_achievement where user_id = :u""")
                .setParameter("u", userId)
                .getResultList();
        Map<String, Unlock> found = new HashMap<>();
        for (Object[] row : rows) {
            Unlock unlock = new Unlock((String) row[0], ((Number) row[1]).intValue(),
                    toDateTime(row[2]), toDateTime(row[3]));
            found.put(key(unlock.code(), unlock.tier()), unlock);
        }
        return found;
    }

    private static OffsetDateTime toDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime time) {
            return time;
        }
        if (value instanceof java.time.Instant instant) {
            return instant.atOffset(java.time.ZoneOffset.UTC);
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(value.toString());
    }

    private static String key(String code, int tier) {
        return code + '#' + tier;
    }

    /** The answer: every achievement with its progress, the XP, the level, and what is new. */
    static AchievementsDto describe(Map<Metric, Long> values, Map<String, Unlock> stored) {
        long xp = Levels.XP_PER_BOOK * values.getOrDefault(Metric.BOOKS_READ, 0L);
        List<AchievementDto> items = new ArrayList<>();
        for (Achievement achievement : Achievement.values()) {
            int tier = 0;
            OffsetDateTime unlockedAt = null;
            int earned = 0;
            for (int t = 1; t <= achievement.tiers(); t++) {
                Unlock unlock = stored.get(key(achievement.name(), t));
                if (unlock != null) {
                    tier = t;
                    unlockedAt = unlock.unlockedAt();
                    earned += achievement.xp(t);
                }
            }
            xp += earned;
            long value = achievement.metric == Metric.EVENT
                    ? tier
                    : values.getOrDefault(achievement.metric, 0L);
            Long next = tier < achievement.tiers() ? achievement.threshold(tier + 1) : null;
            items.add(new AchievementDto(achievement.name(), achievement.family.name(),
                    achievement.tiers(), tier, unlockedAt, value, next, earned,
                    tier < achievement.tiers() ? achievement.xp(tier + 1) : null));
        }
        List<AchievementUnlockDto> unseen = stored.values().stream()
                .filter(unlock -> unlock.seenAt() == null)
                .flatMap(unlock -> Achievement.parse(unlock.code()).stream()
                        .map(a -> new AchievementUnlockDto(a.name(), unlock.tier(), a.tiers(), a.xp(unlock.tier()))))
                .sorted(java.util.Comparator.comparing(AchievementUnlockDto::code)
                        .thenComparing(AchievementUnlockDto::tier))
                .toList();
        int level = Levels.levelOf(xp);
        return new AchievementsDto(xp, level, Levels.xpForLevel(level), Levels.xpForLevel(level + 1),
                items, unseen);
    }
}
