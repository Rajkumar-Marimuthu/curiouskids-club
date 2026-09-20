# Business rules

**Status:** Draft v0.1 | **Date:** 2026-09-19

Every rule has an ID, a plain statement, and (where configurable) a setting key and default. Rules are settings, not code, so the owner can change them without a developer. Settings live in the `setting` table and are editable in the admin UI (FR-ADM-01); defaults below are seeded by migration.

## Time and slots

| ID | Rule | Setting key: default |
| --- | --- | --- |
| BR-01 | All slot dates and times are in the club timezone. Timestamps are stored in UTC. | `club.timezone`: `Europe/London` (OQ-01) |
| BR-02 | A handover window is a recurring daily time range that serves both pickups and returns. Admins define windows per weekday. | Defaults: 17:00 to 17:30 and 19:00 to 19:30, every day (OQ-02) |
| BR-03 | Each window occurrence has a pickup capacity. Booked pickups never exceed it. Returns do not count against capacity in release 1 (OQ-04). | `slot.default-capacity`: 10 |
| BR-04 | A closure date removes all windows for that date. Closing a date that has bookings requires admin confirmation; those reservations are cancelled, families notified, copies released to the next waitlister. | none |
| BR-05 | Pickup slots can be booked up to N days ahead. | `slot.booking-horizon-days`: 14 |
| BR-06 | A pickup slot can be booked or changed until N minutes before the window starts. | `slot.cutoff-minutes`: 120 |

## Loans

| ID | Rule | Setting key: default |
| --- | --- | --- |
| BR-07 | Loan period runs from the pickup date. Due date = pickup date + N days. | `loan.period-days`: 14 |
| BR-08 | If the due date has no window (closure or no window that weekday), the due date moves to the next date that has one. | none |
| BR-09 | A loan can be renewed N times by the member (online) or a volunteer, only if the title has no active waitlist and the loan is not overdue. Renewal extends by the loan period, then BR-08 applies. | `loan.max-renewals`: 1 |
| BR-10 | A loan is overdue when the club-local date is after its due date and it is not returned. Overdue is derived, not stored. No fines in release 1. | none |
| BR-11 | After N days overdue, the loan appears in the admin "possible lost" queue. | `loan.possible-lost-after-days`: 30 |

## Limits

| ID | Rule | Setting key: default |
| --- | --- | --- |
| BR-12 | A family may have at most N active items, counting loans in progress plus reservations in READY_FOR_PICKUP or AWAITING_SLOT. | `limits.max-active-items`: 5 |
| BR-13 | A family may hold at most N waitlist entries. | `limits.max-waitlist-entries`: 5 |
| BR-14 | A family may hold or borrow at most one copy of the same title at a time. | none |
| BR-15 | A family may have at most N child profiles. | `limits.max-children`: 6 |

## Reservations and waitlist

| ID | Rule |
| --- | --- |
| BR-16 | **Reserve with a free copy.** If at least one copy of the title is AVAILABLE, the request must include a bookable pickup slot. In one transaction the system marks one copy HELD, books the slot and sets the reservation to READY_FOR_PICKUP. Without a slot the request fails with `SLOT_REQUIRED`. |
| BR-17 | **Reserve with no free copy.** The request joins the title's waitlist (WAITLISTED), first in, first out by creation time. No slot is chosen yet. |
| BR-18 | **Promotion.** When a copy becomes available (check-in, cancellation, expiry), the first eligible waitlisted reservation is promoted to AWAITING_SLOT and the copy is marked HELD. A family that is over its limits (BR-12) or already holds the title (BR-14) is skipped, keeping its place. |
| BR-19 | **Choose a slot after promotion.** The member has N hours to choose a pickup slot. If they do not, the reservation becomes EXPIRED and the copy goes to the next waitlister. Setting: `promotion.choose-slot-hours`: 48. |
| BR-20 | **No-show.** A READY_FOR_PICKUP reservation not collected by the end of its window becomes EXPIRED (a periodic job, or the volunteer marks it). The copy is released (BR-18). |
| BR-21 | **Repeat no-shows.** N no-shows in D days flags the family on the admin dashboard. No automatic penalty; the admin decides. Settings: `noshow.flag-count`: 3, `noshow.flag-days`: 60. |
| BR-22 | **Cancellation.** Members can cancel WAITLISTED and AWAITING_SLOT any time, and READY_FOR_PICKUP until the slot cut-off (BR-06). After that only a volunteer can. |

## Circulation

| ID | Rule |
| --- | --- |
| BR-23 | Only volunteers and admins check books out or in. |
| BR-24 | Check-out requires a READY_FOR_PICKUP reservation for that copy and family, on the booked date. A volunteer may override the date or window with a required reason, which is audited. |
| BR-25 | Check-in marks the loan RETURNED and the copy AVAILABLE (or DAMAGED if the volunteer flags it), then triggers promotion (BR-18). |
| BR-26 | A volunteer can mark a copy LOST or DAMAGED. A lost copy closes its open loan as LOST. |
| BR-27 | A volunteer can reserve or check out on behalf of a family (walk-in at a window). All rules still apply except BR-06 cut-off. |

## Copy statuses

`AVAILABLE`, `HELD` (allocated to a reservation), `ON_LOAN`, `LOST`, `DAMAGED`, `WITHDRAWN`. Only AVAILABLE copies can be allocated.

## Accounts

| ID | Rule |
| --- | --- |
| BR-28 | Registrants declare they are 18 or older and accept the terms and privacy notice. Consent and its version are stored with a timestamp. |
| BR-29 | Email verification is required before reserving. Browsing is public. |
| BR-30 | A child profile stores a first name or nickname and an age band only (`0-2`, `3-5`, `6-8`, `9-12`, `13+`). No surname, birth date, photo or contact details. |
| BR-31 | Roles: `MEMBER`, `VOLUNTEER`, `ADMIN`. Staff accounts are created by invitation from an admin. |

## Reminders (all by email)

| ID | Rule | Setting key: default |
| --- | --- | --- |
| BR-32 | Pickup reminder N hours before the booked window, if the booking was made earlier than that. | `reminder.pickup-hours-before`: 24 |
| BR-33 | Due-soon reminder N days before due date; due-today reminder on the due date. | `reminder.due-soon-days`: 2 |
| BR-34 | Overdue reminders at 1, 4 and 8 days overdue, then stop. | `reminder.overdue-days`: `1,4,8` |
| BR-35 | Promotion notice when a reservation becomes AWAITING_SLOT, plus a reminder 24 hours before it expires. | `reminder.awaiting-slot-hours-before-expiry`: 24 |
| BR-36 | Transactional emails (verification, reset, confirmations) are always sent. Reminder emails have a member preference toggle for pickup/due-soon but never for overdue. | none |

## Data retention

| ID | Rule | Setting key: default |
| --- | --- | --- |
| BR-37 | Families with no login and no open loans for N months are warned, then anonymised. Loan history is kept without personal data for statistics. (OQ-06) | `retention.inactive-months`: 24 |
