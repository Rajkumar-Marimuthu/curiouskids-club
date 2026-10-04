import { ApiError } from './api'

export type FieldErrors = Partial<Record<string, string>>

/** The first error per field from a VALIDATION_FAILED problem, keyed by field name. */
export function fieldErrorsOf(error: unknown): FieldErrors {
  if (!(error instanceof ApiError)) return {}
  const errors: FieldErrors = {}
  for (const { field, message } of error.fieldErrors) {
    errors[field] ??= message
  }
  return errors
}

/** Whole minutes to wait after a 429 RATE_LIMITED, or undefined for any other error. */
export function rateLimitMinutes(error: unknown): number | undefined {
  if (!(error instanceof ApiError) || error.code !== 'RATE_LIMITED') return undefined
  return Math.max(1, Math.ceil((error.retryAfterSeconds ?? 3600) / 60))
}
