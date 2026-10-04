import { describe, expect, it } from 'vitest'
import { formatDate, formatDateTime, formatTime } from './dates'

describe('dates in the club timezone (NFR-12, BR-01)', () => {
  it('shows times in Europe/London regardless of the browser timezone', () => {
    expect(formatTime('2026-09-21T16:00:00Z')).toBe('17:00')
    expect(formatDate('2026-09-21T23:30:00Z')).toBe('22 Sept 2026')
  })

  it('handles the daylight saving change', () => {
    // Clocks go forward at 01:00 UTC on 29 March 2026.
    expect(formatTime('2026-03-29T00:30:00Z')).toBe('00:30')
    expect(formatTime('2026-03-29T01:30:00Z')).toBe('02:30')
  })

  it('accepts another timezone when given', () => {
    expect(formatTime('2026-09-21T16:00:00Z', 'Asia/Kolkata')).toBe('21:30')
    expect(formatDateTime('2026-09-21T16:00:00Z')).toBe('21 Sept 2026, 17:00')
  })
})
