import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router'
import { Button } from '../../components/ui/button'
import { TextField } from '../../components/ui/field'
import { apiClient, ApiError, unwrap } from '../../lib/api'
import { fieldErrorsOf, rateLimitMinutes } from '../../lib/forms'
import { useDocumentTitle } from '../../lib/useDocumentTitle'

const MIN_PASSWORD = 12

/**
 * FR-ID-04: without a token, asks for the email to send a reset link to; from the emailed link,
 * asks for the new password and then sends the user to log in.
 */
export function Component() {
  const { t } = useTranslation()
  useDocumentTitle(t('pages.resetPassword'))
  const [params] = useSearchParams()
  const token = params.get('token')
  const [linkInvalid, setLinkInvalid] = useState(false)

  return (
    <section className="mx-auto max-w-md space-y-6">
      <h1 className="text-3xl font-bold">{t('pages.resetPassword')}</h1>
      {token && !linkInvalid ? (
        <ChoosePasswordForm token={token} onInvalidLink={() => setLinkInvalid(true)} />
      ) : (
        <>
          {linkInvalid && (
            <p role="alert" className="rounded-card bg-danger-soft p-4">
              {t('resetPassword.invalid')}
            </p>
          )}
          <RequestLinkForm />
        </>
      )}
    </section>
  )
}

function RequestLinkForm() {
  const { t } = useTranslation()
  const [email, setEmail] = useState('')
  const [missing, setMissing] = useState(false)
  const request = useMutation({
    mutationFn: (address: string) =>
      unwrap(apiClient.POST('/api/v1/auth/password-reset/request', { body: { email: address } })),
  })

  if (request.isSuccess) {
    return (
      <p role="status" className="rounded-card bg-brand-soft p-6 text-lg">
        {t('resetPassword.sent')}
      </p>
    )
  }

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const address = email.trim()
    setMissing(address === '')
    if (address !== '') request.mutate(address)
  }

  const minutes = rateLimitMinutes(request.error)

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      <p className="text-ink-muted">{t('resetPassword.requestIntro')}</p>
      {minutes && (
        <p role="alert" className="rounded-card bg-danger-soft p-4">
          {t('errors.rateLimited', { minutes })}
        </p>
      )}
      <TextField
        label={t('resetPassword.email')}
        type="email"
        autoComplete="email"
        required
        maxLength={254}
        value={email}
        onChange={(event) => {
          setEmail(event.target.value)
          setMissing(false)
        }}
        error={missing ? t('resetPassword.required') : fieldErrorsOf(request.error).email}
      />
      <Button type="submit" disabled={request.isPending} className="w-full sm:w-auto">
        {request.isPending ? t('resetPassword.sending') : t('resetPassword.send')}
      </Button>
    </form>
  )
}

function ChoosePasswordForm({
  token,
  onInvalidLink,
}: {
  token: string
  onInvalidLink: () => void
}) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [tooShort, setTooShort] = useState(false)
  const confirm = useMutation({
    mutationFn: (newPassword: string) =>
      unwrap(
        apiClient.POST('/api/v1/auth/password-reset/confirm', {
          body: { token, password: newPassword },
        }),
      ),
    onSuccess: () => navigate('/login?reset=done', { replace: true }),
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'TOKEN_INVALID') onInvalidLink()
    },
  })

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const short = password.length < MIN_PASSWORD
    setTooShort(short)
    if (!short) confirm.mutate(password)
  }

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      <p className="text-ink-muted">{t('resetPassword.chooseIntro')}</p>
      <TextField
        label={t('resetPassword.password')}
        type="password"
        autoComplete="new-password"
        required
        maxLength={128}
        hint={t('resetPassword.passwordHint')}
        value={password}
        onChange={(event) => {
          setPassword(event.target.value)
          setTooShort(false)
        }}
        error={tooShort ? t('resetPassword.passwordHint') : fieldErrorsOf(confirm.error).password}
      />
      <Button type="submit" disabled={confirm.isPending} className="w-full sm:w-auto">
        {confirm.isPending ? t('resetPassword.saving') : t('resetPassword.save')}
      </Button>
    </form>
  )
}
