import { QueryClient } from '@tanstack/react-query';
import { ApiError } from './apiClient';

/**
 * Shared React Query configuration.
 *
 * Catalogue searches hit rate-limited third-party providers and library data barely
 * changes within a session, so the defaults lean towards fewer requests rather than
 * fresher ones.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        retry: (failureCount, error) => {
          // Retrying an authentication or validation error only delays the message the
          // user needs to see; the client already replays once after renewing a token.
          if (error instanceof ApiError && error.status < 500) return false;
          return failureCount < 2;
        },
      },
      mutations: {
        retry: false,
      },
    },
  });
}
