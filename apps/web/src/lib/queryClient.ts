import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'
import { ApiError } from './api'
import { i18n } from './i18n'
import { showToast } from './toast'

/**
 * Expected errors (4xx with a known code) are handled by the page that made the call; anything
 * else (server faults, network failures) gets one global toast.
 */
export function reportUnexpectedError(error: unknown) {
  if (error instanceof ApiError && !error.isUnexpected) return
  showToast({
    tone: 'error',
    title: i18n.t('errors.unexpected'),
    description:
      error instanceof ApiError && error.traceId
        ? i18n.t('errors.reference', { traceId: error.traceId })
        : undefined,
  })
}

export function createQueryClient() {
  return new QueryClient({
    queryCache: new QueryCache({ onError: reportUnexpectedError }),
    mutationCache: new MutationCache({ onError: reportUnexpectedError }),
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        retry: (failureCount, error) =>
          !(error instanceof ApiError && !error.isUnexpected) && failureCount < 2,
      },
    },
  })
}
