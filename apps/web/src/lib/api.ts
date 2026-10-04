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

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

function readCookie(name: string): string | undefined {
  return globalThis.document?.cookie
    .split('; ')
    .find((pair) => pair.startsWith(`${name}=`))
    ?.slice(name.length + 1)
}

/**
 * Sends the CSRF token on every state-changing call (ADR-0004): the XSRF-TOKEN cookie echoed in the
 * X-XSRF-TOKEN header. When the cookie is missing (first visit, or just after login or logout), the
 * API sets a new one first.
 */
async function csrfToken(origin: string): Promise<string | undefined> {
  const existing = readCookie(CSRF_COOKIE)
  if (existing) return existing
  await globalThis.fetch(`${origin}/api/v1/auth/csrf`, { credentials: 'same-origin' })
  return readCookie(CSRF_COOKIE)
}

apiClient.use({
  async onRequest({ request }) {
    if (SAFE_METHODS.has(request.method)) return undefined
    const token = await csrfToken(new URL(request.url).origin)
    if (token) request.headers.set(CSRF_HEADER, decodeURIComponent(token))
    return request
  },
})

export type ApiClient = typeof apiClient

/** An RFC 9457 problem+json error, carrying the stable `code` the UI switches on. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly traceId?: string
  readonly fieldErrors: { field: string; message: string }[]
  /** Seconds to wait before trying again, from the Retry-After header of a 429. */
  readonly retryAfterSeconds?: number

  constructor(
    status: number,
    problem: { code?: string; detail?: string; title?: string; traceId?: string; errors?: unknown },
    retryAfterSeconds?: number,
  ) {
    super(problem.detail ?? problem.title ?? `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.code = problem.code ?? 'UNKNOWN'
    this.traceId = problem.traceId
    this.fieldErrors = Array.isArray(problem.errors) ? problem.errors : []
    this.retryAfterSeconds = retryAfterSeconds
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
    const retryAfter = Number(response.headers.get('Retry-After'))
    throw new ApiError(response.status, problem, retryAfter > 0 ? retryAfter : undefined)
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
