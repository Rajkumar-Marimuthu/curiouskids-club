# API conventions

**Status:** Draft v0.1 | **Date:** 2026-09-19 | Contract file: `contracts/openapi.yaml`

## Workflow (contract-first)

1. Change `contracts/openapi.yaml` first, in the same PR as the code.
2. Implement the endpoint in the API. CI exports the running API's OpenAPI document and fails if it differs from the contract.
3. Regenerate the TypeScript client (`make api-client`) into `apps/web/src/api`. Generated files are committed and never edited by hand.
4. Breaking changes (removing or renaming fields, tightening validation) require a new version path; additive changes do not.

## General style

| Topic | Convention |
| --- | --- |
| Base path | `/api/v1` |
| Format | JSON, camelCase fields, UTF-8 |
| IDs | UUID strings |
| Instants | ISO-8601 UTC with `Z`, for example `2026-09-21T16:00:00Z` |
| Slot dates and times | Local date `2026-09-21` plus local `startTime`/`endTime` (`17:00`) and the timezone name in the response; the server, not the client, decides bookability |
| Enums | UPPER_SNAKE_CASE strings |
| Pagination | `page` (0-based) and `size` (default 24, max 100); response `{ content: [], page: { number, size, totalElements, totalPages } }` |
| Sorting | `sort=field,asc|desc` with a whitelist per endpoint |
| Idempotency | `Idempotency-Key` header (UUID) required on `POST /reservations` and `POST /staff/checkouts`; a repeat returns the original result; the same key with a different body returns 422 `IDEMPOTENCY_KEY_REUSED` |
| Concurrency | Editable resources return an `ETag`; `PUT`/`PATCH` require `If-Match`; mismatch returns 412 |
| Auth | Session cookie plus CSRF header `X-XSRF-TOKEN`; see `security-and-privacy.md` |
| Rate limiting | Login, register, password reset and reserve return 429 `RATE_LIMITED` with `Retry-After` |
| Caching | Public catalogue reads send `Cache-Control: public, max-age=60` with ETags; personal data is `no-store` |

## Errors

RFC 9457 `application/problem+json`:

```json
{
  "type": "https://curiouskids.example/problems/slot-full",
  "title": "Slot is full",
  "status": 409,
  "code": "SLOT_FULL",
  "detail": "The 17:00 window on 2026-09-21 has no places left.",
  "traceId": "c0ffee-...",
  "errors": [{ "field": "pickupSlotId", "message": "Slot is full" }]
}
```

`code` is the stable contract the UI switches on; `detail` is human text and may change. Never include stack traces, SQL or personal data.

| Code | HTTP | Meaning |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | Field errors in `errors` |
| `UNAUTHENTICATED` | 401 | Not logged in |
| `FORBIDDEN` | 403 | Role or ownership check failed |
| `NOT_FOUND` | 404 | Also used when the caller may not know the resource exists |
| `CONFLICT` | 409 | State conflict not covered below |
| `EMAIL_NOT_VERIFIED` | 403 | Verify email first |
| `SLOT_REQUIRED` | 422 | A copy is free, so a pickup slot is needed |
| `SLOT_FULL` | 409 | No capacity left |
| `SLOT_NOT_BOOKABLE` | 422 | Closed, past cut-off, or beyond the horizon |
| `LIMIT_REACHED` | 422 | A limit in BR-12, BR-13 or BR-15 |
| `ALREADY_HOLDING_TITLE` | 409 | BR-14 |
| `COPY_NOT_AVAILABLE` | 409 | Copy cannot be allocated or scanned in this state |
| `RENEWAL_NOT_ALLOWED` | 422 | BR-09 |
| `IDEMPOTENCY_KEY_REUSED` | 422 | Key reused with a different request |
| `RATE_LIMITED` | 429 | Slow down |

## Initial resource map

| Area | Method and path | Who |
| --- | --- | --- |
| Auth | `POST /auth/register`, `POST /auth/verify-email`, `POST /auth/login`, `POST /auth/logout`, `POST /auth/password-reset/request`, `POST /auth/password-reset/confirm`, `POST /auth/invitations/accept`, `GET /auth/me`, `GET /auth/csrf` | Public / member |
| Catalogue | `GET /titles`, `GET /titles/{id}`, `GET /categories` | Public |
| Slots | `GET /slots?from=&to=` | Public (marks bookable for a logged-in member) |
| Family | `GET/PATCH /me/profile`, `GET/POST /me/children`, `PATCH/DELETE /me/children/{id}`, `POST /me/data-export`, `POST /me/delete-request` | Member |
| Reservations | `POST /reservations`, `GET /me/reservations`, `POST /reservations/{id}/choose-slot`, `POST /reservations/{id}/cancel` | Member |
| Loans | `GET /me/loans`, `POST /loans/{id}/renew` | Member |
| Circulation (staff) | `GET /staff/pick-list?date=&windowId=`, `POST /staff/checkouts`, `POST /staff/checkins`, `POST /staff/reservations/{id}/no-show`, `GET /staff/loans/overdue`, `POST /staff/loans/{id}/mark-lost`, `GET /staff/families?q=`, `POST /staff/families/{id}/reservations` | Volunteer, Admin |
| Catalogue (staff) | `POST /staff/titles`, `PUT /staff/titles/{id}`, `POST /staff/titles/lookup`, `POST /staff/titles/{id}/copies`, `PATCH /staff/copies/{id}`, `POST /staff/imports` (dry run), `POST /staff/imports/{id}/confirm` | Volunteer, Admin |
| Administration | `GET/POST/PUT/DELETE /admin/slot-windows`, `GET/POST/DELETE /admin/closures`, `GET/PUT /admin/settings`, `GET/POST /admin/users`, `POST /admin/invitations`, `GET /admin/dashboard`, `GET /admin/audit-log` | Admin |
| Operations | `GET /actuator/health/liveness`, `/actuator/health/readiness` (internal only) | Platform |

## Response shape rules

- Return DTO records, never entities. Do not expose internal IDs of other families.
- Member-facing title responses omit barcodes and staff notes.
- Lists return summaries; details come from the item endpoint.
- Actions that are not CRUD (cancel, renew, check-in) are `POST` sub-resources returning the updated resource.
