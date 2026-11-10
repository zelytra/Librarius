import type { Query } from '@tanstack/react-query';

/**
 * Every statistics query: the summary (`/api/stats`) and the timeline (`/api/stats/timeline`,
 * one query per year and metric).
 *
 * Invalidating `getGetApiStatsQueryKey()` alone missed the timeline: query keys match by prefix
 * element by element, and `'/api/stats/timeline'` is not `'/api/stats'`. A title marked read
 * updated the counters and left the monthly chart showing the month before.
 */
export const STATS_QUERIES = {
  predicate: (query: Query) => {
    const head = query.queryKey[0];
    return typeof head === 'string' && head.startsWith('/api/stats');
  },
};
