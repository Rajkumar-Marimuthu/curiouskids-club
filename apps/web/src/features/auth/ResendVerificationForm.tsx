import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '../../components/ui/button'
import { TextField } from '../../components/ui/field'
import { apiClient, unwrap } from '../../lib/api'
import { fieldErrorsOf } from '../../lib/forms'

/**
 * FR-ID-02: asks for a new verification link. The answer is the same whether or not the email is
 * registered, so it never reveals who has an account.
 */
export function ResendVerificationForm() {
  const { t } = useTranslation()
  const [email, setEmail] = useState('')
  const resend = useMutation({
    mutationFn: (address: string) =>
      unwrap(apiClient.POST('/api/v1/auth/verify-email/resend', { body: { email: address } })),
  })

  if (resend.isSuccess) {
    return (
      <p role="status" className="rounded-card bg-brand-soft p-4">
        {t('verifyEmail.resent')}
      </p>
    )
  }

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    resend.mutate(email.trim())
  }

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4">
      <h2 className="text-xl font-bold">{t('verifyEmail.resendTitle')}</h2>
      <TextField
        label={t('register.email')}
        type="email"
        autoComplete="email"
        required
        value={email}
        onChange={(event) => setEmail(event.target.value)}
        error={fieldErrorsOf(resend.error).email}
      />
      <Button type="submit" disabled={resend.isPending || email.trim() === ''}>
        {t('verifyEmail.resend')}
      </Button>
    </form>
  )
}
