import { http, HttpResponse } from 'msw'
import type { components } from '../api/schema'

type PingResponse = components['schemas']['PingResponse']
type Problem = components['schemas']['Problem']

export const pingOk: PingResponse = { status: 'ok', time: '2026-09-21T16:00:00Z' }

/** Default happy-path responses, typed from the contract so they drift with it. */
export const handlers = [
  http.get('*/api/v1/ping', () => HttpResponse.json(pingOk)),
  http.post('*/api/v1/auth/register', () => new HttpResponse(null, { status: 202 })),
  http.post('*/api/v1/auth/verify-email', () => new HttpResponse(null, { status: 204 })),
  http.post('*/api/v1/auth/verify-email/resend', () => new HttpResponse(null, { status: 202 })),
]

/** A problem+json error as the API sends it (docs/architecture/api-conventions.md). */
export function problem(
  status: number,
  code: Problem['code'],
  traceId = 'trace-123',
  errors?: Problem['errors'],
) {
  const body: Problem = {
    type: `https://curiouskids.example/problems/${code}`,
    title: code,
    status,
    code,
    traceId,
    errors,
  }
  return HttpResponse.json(body, {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}
