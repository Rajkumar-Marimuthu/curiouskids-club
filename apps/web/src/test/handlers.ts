import { http, HttpResponse } from 'msw'
import type { components } from '../api/schema'

type PingResponse = components['schemas']['PingResponse']

export const pingOk: PingResponse = { status: 'ok', time: '2026-09-21T16:00:00Z' }

/** Default happy-path responses, typed from the contract so they drift with it. */
export const handlers = [http.get('*/api/v1/ping', () => HttpResponse.json(pingOk))]

/** A problem+json error as the API sends it (docs/architecture/api-conventions.md). */
export function problem(status: number, code: string, traceId = 'trace-123') {
  return HttpResponse.json(
    { type: `https://curiouskids.example/problems/${code}`, title: code, status, code, traceId },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}
