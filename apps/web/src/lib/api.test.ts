import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { problem } from '../test/handlers'
import { server } from '../test/server'
import { apiClient, ApiError, unwrap } from './api'
import { reportUnexpectedError } from './queryClient'
import { currentToasts, dismissToast } from './toast'

afterEach(() => currentToasts().forEach((t) => dismissToast(t.id)))

describe('typed API client', () => {
  it('returns typed data from the contract', async () => {
    const ping = await unwrap(apiClient.GET('/api/v1/ping'))
    expect(ping.status).toBe('ok')
  })

  it('surfaces the problem+json code, detail and traceId as an ApiError', async () => {
    server.use(http.get('*/api/v1/ping', () => problem(409, 'SLOT_FULL', 'abc-123')))

    const error = await unwrap(apiClient.GET('/api/v1/ping')).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 409, code: 'SLOT_FULL', traceId: 'abc-123' })
    expect((error as ApiError).isUnexpected).toBe(false)
  })

  it('uses UNKNOWN when the error body is not problem+json', async () => {
    server.use(http.get('*/api/v1/ping', () => new HttpResponse('Bad gateway', { status: 502 })))

    const error = (await unwrap(apiClient.GET('/api/v1/ping')).catch((e: unknown) => e)) as ApiError

    expect(error.code).toBe('UNKNOWN')
    expect(error.isUnexpected).toBe(true)
  })
})

describe('global error toast', () => {
  it('stays quiet for expected errors the page handles', () => {
    reportUnexpectedError(new ApiError(422, { code: 'LIMIT_REACHED' }))
    expect(currentToasts()).toHaveLength(0)
  })

  it('shows one toast with the trace reference for server faults', () => {
    reportUnexpectedError(new ApiError(500, { code: 'INTERNAL_ERROR', traceId: 'abc-123' }))
    expect(currentToasts()).toEqual([
      expect.objectContaining({ tone: 'error', description: 'Reference: abc-123' }),
    ])
  })

  it('shows a toast for network failures', () => {
    reportUnexpectedError(new TypeError('Failed to fetch'))
    expect(currentToasts()).toHaveLength(1)
  })
})

describe('CSRF token (ADR-0004)', () => {
  it('fetches the token cookie once and echoes it on state-changing calls only', async () => {
    let csrfCalls = 0
    const headers: (string | null)[] = []
    server.use(
      http.get('*/api/v1/auth/csrf', () => {
        csrfCalls++
        document.cookie = 'XSRF-TOKEN=abc%3D; path=/'
        return new HttpResponse(null, { status: 204 })
      }),
      http.get('*/api/v1/ping', ({ request }) => {
        headers.push(request.headers.get('X-XSRF-TOKEN'))
        return HttpResponse.json({ status: 'ok', time: '2026-09-21T16:00:00Z' })
      }),
      http.post('*/api/v1/auth/logout', ({ request }) => {
        headers.push(request.headers.get('X-XSRF-TOKEN'))
        return new HttpResponse(null, { status: 204 })
      }),
    )

    await unwrap(apiClient.GET('/api/v1/ping'))
    await unwrap(apiClient.POST('/api/v1/auth/logout'))
    await unwrap(apiClient.POST('/api/v1/auth/logout'))

    expect(headers).toEqual([null, 'abc=', 'abc='])
    expect(csrfCalls).toBe(1)
  })
})
