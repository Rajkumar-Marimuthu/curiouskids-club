import { CLUB_TIMEZONE } from './config'
import { i18n } from './i18n'

type DateInput = string | Date

function toDate(value: DateInput): Date {
  return typeof value === 'string' ? new Date(value) : value
}

function format(value: DateInput, options: Intl.DateTimeFormatOptions, timeZone: string): string {
  return new Intl.DateTimeFormat(i18n.language, { ...options, timeZone }).format(toDate(value))
}

/** A date in the club timezone, for example "21 Sept 2026". */
export function formatDate(value: DateInput, timeZone = CLUB_TIMEZONE): string {
  return format(value, { dateStyle: 'medium' }, timeZone)
}

/** A 24-hour time in the club timezone, for example "17:00". */
export function formatTime(value: DateInput, timeZone = CLUB_TIMEZONE): string {
  return format(value, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }, timeZone)
}

/** A date and time in the club timezone. */
export function formatDateTime(value: DateInput, timeZone = CLUB_TIMEZONE): string {
  return format(value, { dateStyle: 'medium', timeStyle: 'short', hourCycle: 'h23' }, timeZone)
}
