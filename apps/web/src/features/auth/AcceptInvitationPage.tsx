import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router'
import { Button } from '../../components/ui/button'
import { TextField } from '../../components/ui/field'
import { apiClient, ApiError, unwrap } from '../../lib/api'
import { fieldErrorsOf } from '../../lib/forms'
import { useDocumentTitle } from '../../lib/useDocumentTitle'

const MIN_PASSWORD = 12

/**
 * FR-ID-07: the page an invitation link opens. The invited volunteer or admin chooses a password,
 * which creates their account, and is then sent to log in.
 */
export function Component() {
  const { t } = useTranslation()
  useDocumentTitle(t('pages.acceptInvitation'))
  const navigate = useNavigate()
  const token = useSearchParams()[0].get('token')
  const [password, setPassword] = useState('')
  const [tooShort, setTooShort] = useState(false)
  const accept = useMutation({
    mutationFn: (chosen: string) =>
      unwrap(
        apiClient.POST('/api/v1/auth/invitations/accept', {
          body: { token: token ?? '', password: chosen },
        }),
      ),
    onSuccess: () => navigate('/login?invited=done', { replace: true }),
  })

  const invalid =
    token === null || (accept.error instanceof ApiError && accept.error.code === 'TOKEN_INVALID')

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const short = password.length < MIN_PASSWORD
    setTooShort(short)
    if (!short) accept.mutate(password)
  }

  return (
    <section className="mx-auto max-w-md space-y-6">
      <h1 className="text-3xl font-bold">{t('pages.acceptInvitation')}</h1>
      {invalid ? (
        <p role="alert" className="rounded-card bg-danger-soft p-4">
          {t('acceptInvitation.invalid')}
        </p>
      ) : (
        <form onSubmit={onSubmit} noValidate className="space-y-5">
          <p className="text-ink-muted">{t('acceptInvitation.intro')}</p>
          {accept.error instanceof ApiError && accept.error.isUnexpected && (
            <p role="alert">{t('errors.unexpected')}</p>
          )}
          <TextField
            label={t('acceptInvitation.password')}
            type="password"
            autoComplete="new-password"
            required
            maxLength={128}
            hint={t('acceptInvitation.passwordHint')}
            value={password}
            onChange={(event) => {
              setPassword(event.target.value)
              setTooShort(false)
            }}
            error={
              tooShort ? t('acceptInvitation.passwordHint') : fieldErrorsOf(accept.error).password
            }
          />
          <Button type="submit" disabled={accept.isPending} className="w-full sm:w-auto">
            {accept.isPending ? t('acceptInvitation.saving') : t('acceptInvitation.save')}
          </Button>
        </form>
      )}
    </section>
  )
}
