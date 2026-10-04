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
