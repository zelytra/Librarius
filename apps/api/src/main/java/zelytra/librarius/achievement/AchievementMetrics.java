package zelytra.librarius.achievement;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import zelytra.librarius.achievement.Achievement.Metric;
import zelytra.librarius.domain.ReadingGoal;
import zelytra.librarius.domain.repository.ReadingGoalRepository;
import zelytra.librarius.domain.repository.ReadingProgressRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The figures the achievements are measured on (#287), for one reader, every query scoped by
 * {@code user_id}. Aggregated by the database — one query per figure, whatever the size of the
 * library — except the week streak, which walks the distinct weeks a book was finished in.
 */
@ApplicationScoped
public class AchievementMetrics {

    /** The month an annual goal must be reached by to count as early: end of September. */
    static final int EARLY_MONTH = 9;

    @Inject
    EntityManager em;

    @Inject
    ReadingGoalRepository goals;

    @Inject
    ReadingProgressRepository progresses;

    public Map<Metric, Long> of(String userId) {
        Map<Metric, Long> values = new EnumMap<>(Metric.class);
        values.put(Metric.BOOKS_READ, count("""
                select count(*) from library_item li where li.user_id = :u and li.status = 'READ'""", userId));
        values.put(Metric.PAGES_READ, count("""
                select coalesce(sum(coalesce(li.page_count, e.page_count, e.page_count_estimate)), 0)
                from library_item li join edition e on e.id = li.edition_id
                where li.user_id = :u and li.status = 'READ'""", userId));
        // A run the reader holds at least two volumes of, every one of them read.
        values.put(Metric.SERIES_FINISHED, count("""
                select count(*) from (
                  select w.series_id from library_item li
                    join edition e on e.id = li.edition_id join work w on w.id = e.work_id
                  where li.user_id = :u and w.series_id is not null
                  group by w.series_id
                  having count(*) >= 2 and bool_and(li.status = 'READ')) s""", userId));
        values.put(Metric.ONE_DAY_READS, count("""
                select count(*) from library_item li join reading_progress rp on rp.library_item_id = li.id
                where li.user_id = :u and li.status = 'READ'
                  and rp.started_at is not null and rp.started_at = rp.finished_at""", userId));
        values.put(Metric.WEEK_STREAK, weekStreak(userId));
        long[] reached = goalsReached(userId);
        values.put(Metric.GOALS_REACHED, reached[0]);
        values.put(Metric.GOALS_REACHED_EARLY, reached[1]);
        values.put(Metric.RATED, count("""
                select count(*) from library_item li where li.user_id = :u and li.rating is not null""", userId));
        values.put(Metric.REVIEWED, count("""
                select count(*) from library_item li where li.user_id = :u and li.review is not null""", userId));
        values.put(Metric.TITLES, count("""
                select count(*) from library_item li where li.user_id = :u""", userId));
        values.put(Metric.CATEGORIES, count("""
                select count(distinct li.rank_category_id) from library_item li
                where li.user_id = :u and li.rank_category_id is not null""", userId));
        values.put(Metric.GENRES, count("""
                select count(distinct wg.genre_id) from library_item li
                  join edition e on e.id = li.edition_id join work_genre wg on wg.work_id = e.work_id
                where li.user_id = :u""", userId));
        // Two letters: the catalogs write French as "fr" or "fre", and both are one language.
        values.put(Metric.LANGUAGES, count("""
                select count(distinct left(lower(e.language), 2)) from library_item li
                  join edition e on e.id = li.edition_id
                where li.user_id = :u and e.language is not null and length(trim(e.language)) >= 2""", userId));
        values.put(Metric.KINDS, count("""
                select count(distinct w.kind) from library_item li
                  join edition e on e.id = li.edition_id join work w on w.id = e.work_id
                where li.user_id = :u""", userId));
        // Added from a catalog result rather than typed by hand.
        values.put(Metric.FROM_CATALOG, count("""
                select count(*) from library_item li join edition e on e.id = li.edition_id
                where li.user_id = :u and e.provider is not null""", userId));
        values.put(Metric.AUTHORS_FOLLOWED, count("""
                select count(*) from author_follow af where af.user_id = :u""", userId));
        // An author with at least three known works, every one of them in the reader's library.
        values.put(Metric.AUTHORS_COMPLETE, count("""
                select count(*) from (
                  select wa.author_id from work_author wa
                    left join (select distinct e.work_id from library_item li
                               join edition e on e.id = li.edition_id where li.user_id = :u) mine
                      on mine.work_id = wa.work_id
                  group by wa.author_id
                  having count(*) >= 3 and count(mine.work_id) = count(*)) a""", userId));
        values.put(Metric.EVENT, 0L);
        return values;
    }

    private long count(String sql, String userId) {
        Object value = em.createNativeQuery(sql).setParameter("u", userId).getSingleResult();
        return value == null ? 0 : ((Number) value).longValue();
    }

    /** The longest run of consecutive weeks with at least one book finished. */
    long weekStreak(String userId) {
        @SuppressWarnings("unchecked")
        List<Object> weeks = em.createNativeQuery("""
                select distinct cast(date_trunc('week', rp.finished_at) as date) as week
                from library_item li join reading_progress rp on rp.library_item_id = li.id
                where li.user_id = :u and li.status = 'READ' and rp.finished_at is not null
                order by week""").setParameter("u", userId).getResultList();
        return longestRun(weeks.stream().map(AchievementMetrics::toDate).toList());
    }

    /** The longest run of week starts seven days apart, in an ascending list. */
    static long longestRun(List<LocalDate> weeks) {
        long best = 0;
        long run = 0;
        LocalDate previous = null;
        for (LocalDate week : weeks) {
            run = previous != null && ChronoUnit.DAYS.between(previous, week) == 7 ? run + 1 : 1;
            best = Math.max(best, run);
            previous = week;
        }
        return best;
    }

    private static LocalDate toDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        return LocalDate.parse(value.toString().substring(0, 10));
    }

    /** {reached, reached by the end of September}: annual goals met, each on its own kind and unit. */
    private long[] goalsReached(String userId) {
        long reached = 0;
        long early = 0;
        for (ReadingGoal goal : goals.listByUser(userId)) {
            if (goal.targetCount == null || goal.targetCount <= 0 || goal.year == null) {
                continue;
            }
            LocalDate from = LocalDate.of(goal.year, 1, 1);
            if (progresses.progressTowards(userId, from, LocalDate.of(goal.year, 12, 31), goal.unit, goal.kind)
                    >= goal.targetCount) {
                reached++;
                if (progresses.progressTowards(userId, from, LocalDate.of(goal.year, EARLY_MONTH, 30),
                        goal.unit, goal.kind) >= goal.targetCount) {
                    early++;
                }
            }
        }
        return new long[] {reached, early};
    }
}
