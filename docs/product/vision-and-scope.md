# Vision and scope

**Status:** Draft v0.1 | **Date:** 2026-09-19 | **Owner:** Rajkumar

## Problem

A small home-based kids' library lends books to local families. Reservations arrive by message at any hour, and pickups and returns are handled ad hoc. The 1 to 2 volunteers who run it spend too much time coordinating, and a growing collection (100 books now, up to 10,000) will make that unmanageable.

## Vision

Families register once, reserve any book at any time from their phone, and collect or return it only in fixed daily windows (for example 5:00 pm and 7:00 pm). Volunteers see exactly who is coming to which window, scan books in and out, and never negotiate times.

## Users

| Persona | Goal | Notes |
| --- | --- | --- |
| Parent (member) | Register the family, reserve books for children, know when to collect and return | Owns the account; adds child profiles |
| Child | Browse and pick books | No login in release 1; browses with a parent's session |
| Volunteer | Run each window quickly: pick list, scan out, scan in, overdue follow-up | Mostly on a phone at the door |
| Admin (owner) | Configure windows, rules, users; manage catalogue; see reports | Same person as a volunteer at first |

## Goals (release 1)

1. Families register and verify by email, add child profiles.
2. Browse and search the catalogue; see live availability.
3. Reserve 24x7 with a pickup slot; join a waitlist when all copies are out.
4. Enforce fixed handover windows, capacity and closures in software.
5. Volunteers check books out and in by barcode scan; daily pick list; overdue list.
6. Automatic email reminders for pickup, due and overdue.
7. Add books by ISBN scan with auto-filled details; bulk import the first 100 books.

## Non-goals (release 1)

- Online payments, fines or deposits (design leaves room; see open questions).
- Native mobile apps (the web app is responsive and installable as a PWA later).
- Child logins, reviews, reading lists, recommendations.
- Multiple locations or branches; multiple languages (structure is i18n-ready).
- Inter-library loans, e-books, RFID.

## Future scope (not release 1)

Curiouskids Club may grow beyond lending: digital learning, play sessions, social meetups and summer courses. Nothing here is specified or built yet.

- Each new area starts as its own module with its own requirements and an ADR, added beside the existing modules (ADR-0002). Do not add speculative abstractions now.
- Likely reuse: `identity` (families and children), `scheduling` (capacity-limited sessions, closures) and `notification` (email reminders). Summer-course enrolment is close to slot booking.
- Higher-risk areas need a design review before any build: digital learning content (hosting, licensing, possibly a third-party service), and social features involving children (moderation, safeguarding, stricter privacy). Revisit `security-and-privacy.md` and ADR-0004 (child logins) first.

## Success measures

| Measure | Target |
| --- | --- |
| Volunteer admin time for a normal day | 15 minutes or less outside the handover windows |
| Reservations needing manual fix-up | Under 5% |
| Double-booked copies or over-full slots | Zero (enforced by the database) |
| Page load, catalogue search (95th percentile) | Under 500 ms server time at 50 concurrent users |
| Reminder emails delivered without duplicates | 99% or better |

## Scale assumptions

| Stage | Books | Families | Peak concurrent users |
| --- | --- | --- | --- |
| Launch | 100 | 50 to 100 | Under 10 |
| Growth | 1,000 | about 300 | Under 25 |
| Scale | 10,000 | 1,000 or more | Under 100 |

## Constraints

- Budget: about $25 to $65 a month hosting at launch (see the plan doc); build effort about 450 developer hours.
- Team: 1 developer with an AI coding agent, 1 to 2 non-technical volunteers.
- Children's data: collect the minimum; comply with the privacy law of the operating country.

## Open questions

Each needs an owner and a decision date before the task that depends on it starts.

| ID | Question | Needed by | Default assumed until answered |
| --- | --- | --- | --- |
| OQ-01 | Which country and timezone? Sets `club.timezone`, privacy law, email wording, currency | Before T-030 | `Europe/London` |
| OQ-02 | Exact handover windows and capacity per window | Before T-030 | 17:00 to 17:30 and 19:00 to 19:30 daily, 10 pickups each |
| OQ-03 | Is borrowing free, or are there fees or deposits? | Before M4 | Free; no payments in release 1 |
| OQ-04 | Must returns be pre-booked, or are they accepted in any window? | Before T-044 | Accepted in any window; not booked |
| OQ-05 | Can children have their own login later? | After launch | No |
| OQ-06 | Data retention period for inactive families | Before T-061 | 24 months, then anonymise |
| OQ-07 | Modular monolith or microservices (see ADR-0002) | Before T-003 | Modular monolith |
