import { useId, type ComponentProps, type ReactNode } from 'react'
import { cn } from '../../lib/utils'

type TextFieldProps = Omit<ComponentProps<'input'>, 'id'> & {
  label: string
  hint?: string
  error?: string
}

/**
 * A labelled text input. The hint and error are announced with the field (aria-describedby), and an
 * error marks it invalid (NFR-07).
 */
export function TextField({ label, hint, error, className, ...props }: TextFieldProps) {
  const id = useId()
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  return (
    <div className={cn('space-y-1', className)}>
      <label htmlFor={id} className="block font-semibold">
        {label}
      </label>
      {hint && (
        <p id={hintId} className="text-sm text-ink-muted">
          {hint}
        </p>
      )}
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={[hintId, errorId].filter(Boolean).join(' ') || undefined}
        className={cn(
          'block min-h-11 w-full rounded-lg border border-line bg-surface-raised px-3 text-base',
          'focus:outline-2 focus:outline-offset-2 focus:outline-brand',
          error && 'border-danger',
        )}
        {...props}
      />
      {error && (
        <p id={errorId} className="text-sm font-semibold text-danger">
          {error}
        </p>
      )}
    </div>
  )
}

type CheckboxFieldProps = Omit<ComponentProps<'input'>, 'id' | 'type'> & {
  label: ReactNode
  error?: string
}

/** A labelled checkbox with a large tap target and an announced error. */
export function CheckboxField({ label, error, className, ...props }: CheckboxFieldProps) {
  const id = useId()
  const errorId = error ? `${id}-error` : undefined
  return (
    <div className={cn('space-y-1', className)}>
      <div className="flex min-h-11 items-center gap-3">
        <input
          id={id}
          type="checkbox"
          aria-invalid={error ? true : undefined}
          aria-describedby={errorId}
          className="size-5 shrink-0 accent-brand"
          {...props}
        />
        <label htmlFor={id}>{label}</label>
      </div>
      {error && (
        <p id={errorId} className="text-sm font-semibold text-danger">
          {error}
        </p>
      )}
    </div>
  )
}
