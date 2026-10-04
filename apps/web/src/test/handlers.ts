import { http, HttpResponse } from 'msw'
import type { components } from '../api/schema'

type PingResponse = components['schemas']['PingResponse']
type Problem = components['schemas']['Problem']
type Me = components['schemas']['MeResponse']
type Profile = components['schemas']['ProfileResponse']

export const pingOk: PingResponse = { status: 'ok', time: '2026-09-21T16:00:00Z' }

export const memberMe: Me = {
  accountId: '0b6c1d9e-2f4a-4c1e-9d55-6a1f7c2b3e01',
  role: 'MEMBER',
  familyId: '7d2e4f10-5a3b-4e8c-b1a2-9c0d8e7f6a52',
  emailVerified: true,
}

export const memberProfile: Profile = {
  name: 'Sam Parent',
  email: 'sam@example.com',
  remindPickup: true,
  remindDueSoon: true,
}

/** The logged-in account for this test; by default nobody is logged in. */
export function loggedInAs(role: Me['role'] | null) {
  const me: Me | null =
    role === null
      ? null
      : role === 'MEMBER'
        ? memberMe
        : { accountId: memberMe.accountId, role, emailVerified: true }
  return http.get('*/api/v1/auth/me', () =>
    me ? HttpResponse.json(me) : problem(401, 'UNAUTHENTICATED'),
  )
}

/** Default happy-path responses, typed from the contract so they drift with it. */
export const handlers = [
  http.get('*/api/v1/ping', () => HttpResponse.json(pingOk)),
  http.get('*/api/v1/auth/csrf', () => {
    document.cookie = 'XSRF-TOKEN=test-csrf-token; path=/'
    return new HttpResponse(null, { status: 204 })
  }),
  loggedInAs(null),
  http.post('*/api/v1/auth/login', () => HttpResponse.json(memberMe)),
  http.post('*/api/v1/auth/logout', () => new HttpResponse(null, { status: 204 })),
  http.get('*/api/v1/me/children', () => HttpResponse.json([])),
  http.get('*/api/v1/me/profile', () => HttpResponse.json(memberProfile)),
  http.post('*/api/v1/auth/register', () => new HttpResponse(null, { status: 202 })),
  http.post('*/api/v1/auth/verify-email', () => new HttpResponse(null, { status: 204 })),
  http.post('*/api/v1/auth/verify-email/resend', () => new HttpResponse(null, { status: 202 })),
  http.post('*/api/v1/auth/password-reset/request', () => new HttpResponse(null, { status: 202 })),
  http.post('*/api/v1/auth/password-reset/confirm', () => new HttpResponse(null, { status: 204 })),
  http.post('*/api/v1/auth/email-change/confirm', () => new HttpResponse(null, { status: 204 })),
  http.post('*/api/v1/auth/invitations/accept', () => new HttpResponse(null, { status: 204 })),
]

/** A problem+json error as the API sends it (docs/architecture/api-conventions.md). */
export function problem(
  status: number,
  code: Problem['code'],
  traceId = 'trace-123',
  errors?: Problem['errors'],
  headers: Record<string, string> = {},
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
    headers: { 'Content-Type': 'application/problem+json', ...headers },
  })
}
