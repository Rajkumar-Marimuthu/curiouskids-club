import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { Button } from '../../components/ui/button'
import { TextField } from '../../components/ui/field'
import { ApiError } from '../../lib/api'
import { homeFor, safeNext, useLogin } from '../../lib/auth'
import type { FieldErrors } from '../../lib/forms'
import { useDocumentTitle } from '../../lib/useDocumentTitle'

/** FR-ID-03: log in with email and password, then go back to the page that asked for it. */
export function Component() {
  const { t } = useTranslation()
  useDocumentTitle(t('pages.login'))
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [clientErrors, setClientErrors] = useState<FieldErrors>({})
  const login = useLogin()

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const found: FieldErrors = {}
    if (email.trim() === '') found.email = t('login.required')
    if (password === '') found.password = t('login.required')
    setClientErrors(found)
    if (Object.keys(found).length > 0) return
    login.mutate(
      { email: email.trim(), password },
      {
        onSuccess: (me) =>
          navigate(safeNext(params.get('next')) ?? homeFor[me.role], { replace: true }),
      },
    )
  }

  return (
    <section className="mx-auto max-w-md space-y-6">
      <div>
        <h1 className="text-3xl font-bold">{t('pages.login')}</h1>
        <p className="mt-2 text-ink-muted">{t('login.intro')}</p>
      </div>
      {params.get('reset') === 'done' && !login.error && (
        <p role="status" className="rounded-card bg-brand-soft p-4">
          {t('login.passwordChanged')}
        </p>
      )}
      {login.error instanceof ApiError && !login.error.isUnexpected && (
        <p role="alert" className="rounded-card bg-danger-soft p-4">
          {loginErrorMessage(login.error, t)}
        </p>
      )}
      <form onSubmit={onSubmit} noValidate className="space-y-5">
        <TextField
          label={t('login.email')}
          type="email"
          autoComplete="username"
          required
          maxLength={254}
          value={email}
          onChange={(event) => {
            setEmail(event.target.value)
            setClientErrors((current) => ({ ...current, email: undefined }))
          }}
          error={clientErrors.email}
        />
        <TextField
          label={t('login.password')}
          type="password"
          autoComplete="current-password"
          required
          maxLength={128}
          value={password}
          onChange={(event) => {
            setPassword(event.target.value)
            setClientErrors((current) => ({ ...current, password: undefined }))
          }}
          error={clientErrors.password}
        />
        <Button type="submit" disabled={login.isPending} className="w-full sm:w-auto">
          {login.isPending ? t('login.submitting') : t('login.submit')}
        </Button>
      </form>
      <p>
        <Link to="/reset-password" className="font-semibold text-brand underline">
          {t('login.forgot')}
        </Link>
      </p>
      <p>
        {t('login.noAccount')}{' '}
        <Link to="/register" className="font-semibold text-brand underline">
          {t('login.register')}
        </Link>
      </p>
    </section>
  )
}

function loginErrorMessage(error: ApiError, t: ReturnType<typeof useTranslation>['t']): string {
  if (error.code === 'RATE_LIMITED') {
    const minutes = Math.max(1, Math.ceil((error.retryAfterSeconds ?? 900) / 60))
    return t('login.rateLimited', { minutes })
  }
  return t('login.failed')
}
