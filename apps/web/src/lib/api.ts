import { useQuery, type QueryKey, type UseQueryOptions } from '@tanstack/react-query'
import createClient from 'openapi-fetch'
import type { paths } from '../api/schema'

/** The typed API client. Paths and bodies come from contracts/openapi.yaml via `make api-client`. */
export const apiClient = createClient<paths>({
  // Absolute so it also works outside a browser (tests); the API is always same-origin.
  baseUrl: globalThis.location?.origin ?? '',
  credentials: 'same-origin',
  // Look fetch up on each call rather than capturing it once, so test mocks (MSW) apply.
  fetch: (request) => globalThis.fetch(request),
})

export type ApiClient = typeof apiClient

/** An RFC 9457 problem+json error, carrying the stable `code` the UI switches on. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly traceId?: string
  readonly fieldErrors: { field: string; message: string }[]

  constructor(
    status: number,
    problem: { code?: string; detail?: string; title?: string; traceId?: string; errors?: unknown },
  ) {
    super(problem.detail ?? problem.title ?? `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.code = problem.code ?? 'UNKNOWN'
    this.traceId = problem.traceId
    this.fieldErrors = Array.isArray(problem.errors) ? problem.errors : []
  }

  /** Server faults and anything the UI cannot act on; shown as a global toast. */
  get isUnexpected(): boolean {
    return this.status >= 500
  }
}

type FetchResult<T> = { data?: T; error?: unknown; response: Response }

/** Returns the data of a successful call, or throws an {@link ApiError}. */
export async function unwrap<T>(request: Promise<FetchResult<T>>): Promise<T> {
  const { data, error, response } = await request
  if (!response.ok) {
    const problem = typeof error === 'object' && error !== null ? error : {}
    throw new ApiError(response.status, problem)
  }
  return data as T
}

/**
 * Runs a typed API call through TanStack Query. Errors are {@link ApiError}s, so pages can switch on
 * `error.code`, for example `SLOT_FULL`.
 *
 * @example const ping = useApi(['ping'], (api) => api.GET('/api/v1/ping'))
 */
export function useApi<T>(
  key: QueryKey,
  request: (api: ApiClient) => Promise<FetchResult<T>>,
  options?: Omit<UseQueryOptions<T, ApiError>, 'queryKey' | 'queryFn'>,
) {
  return useQuery<T, ApiError>({
    queryKey: key,
    queryFn: () => unwrap(request(apiClient)),
    ...options,
  })
}
