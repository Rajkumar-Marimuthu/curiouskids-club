import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { components } from '../../api/schema'
import { Button } from '../../components/ui/button'
import { CheckboxField, TextField } from '../../components/ui/field'
import { apiClient, unwrap } from '../../lib/api'
import { fieldErrorsOf, type FieldErrors } from '../../lib/forms'
import { useDocumentTitle } from '../../lib/useDocumentTitle'

type RegisterRequest = components['schemas']['RegisterRequest']

const MIN_PASSWORD = 12

/** FR-ID-01: a parent registers their family. Password strength is checked by the API. */
export function Component() {
  const { t } = useTranslation()
  useDocumentTitle(t('pages.register'))
  const [form, setForm] = useState<RegisterRequest>({
    email: '',
    password: '',
    name: '',
    phone: '',
    confirmAdult: false,
    acceptTerms: false,
  })
  const [clientErrors, setClientErrors] = useState<FieldErrors>({})
  const register = useMutation({
    mutationFn: (body: RegisterRequest) =>
      unwrap(apiClient.POST('/api/v1/auth/register', { body })),
  })

  if (register.isSuccess) {
    return (
      <section className="mx-auto max-w-lg space-y-4">
        <h1 className="text-3xl font-bold">{t('register.checkEmailTitle')}</h1>
        <p role="status" className="rounded-card bg-brand-soft p-6 text-lg">
          {t('register.checkEmail', { email: form.email.trim() })}
        </p>
      </section>
    )
  }

  const errors: FieldErrors = { ...fieldErrorsOf(register.error), ...clientErrors }
  const set = <K extends keyof RegisterRequest>(key: K, value: RegisterRequest[K]) => {
    setForm((current) => ({ ...current, [key]: value }))
    setClientErrors((current) => ({ ...current, [key]: undefined }))
  }

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const found: FieldErrors = {}
    if (form.email.trim() === '') found.email = t('register.required')
    if (form.name.trim() === '') found.name = t('register.required')
    if (form.password.length < MIN_PASSWORD) found.password = t('register.passwordHint')
    if (!form.confirmAdult) found.confirmAdult = t('register.confirmAdultRequired')
    if (!form.acceptTerms) found.acceptTerms = t('register.acceptTermsRequired')
    setClientErrors(found)
    if (Object.keys(found).length > 0) return
    register.mutate({
      ...form,
      email: form.email.trim(),
      name: form.name.trim(),
      phone: form.phone?.trim() || undefined,
    })
  }

  const hasErrors = Object.values(errors).some(Boolean)

  return (
    <section className="mx-auto max-w-lg space-y-6">
      <div>
        <h1 className="text-3xl font-bold">{t('pages.register')}</h1>
        <p className="mt-2 text-ink-muted">{t('register.intro')}</p>
      </div>
      {hasErrors && (
        <p role="alert" className="rounded-card bg-danger-soft p-4">
          {t('register.fixErrors')}
        </p>
      )}
      <form onSubmit={onSubmit} noValidate className="space-y-5">
        <TextField
          label={t('register.name')}
          autoComplete="name"
          required
          maxLength={100}
          value={form.name}
          onChange={(event) => set('name', event.target.value)}
          error={errors.name}
        />
        <TextField
          label={t('register.email')}
          type="email"
          autoComplete="email"
          required
          maxLength={254}
          value={form.email}
          onChange={(event) => set('email', event.target.value)}
          error={errors.email}
        />
        <TextField
          label={t('register.password')}
          type="password"
          autoComplete="new-password"
          required
          maxLength={128}
          hint={t('register.passwordHint')}
          value={form.password}
          onChange={(event) => set('password', event.target.value)}
          error={errors.password}
        />
        <TextField
          label={t('register.phone')}
          type="tel"
          autoComplete="tel"
          maxLength={30}
          hint={t('register.phoneHint')}
          value={form.phone ?? ''}
          onChange={(event) => set('phone', event.target.value)}
          error={errors.phone}
        />
        <CheckboxField
          label={t('register.confirmAdult')}
          checked={form.confirmAdult}
          onChange={(event) => set('confirmAdult', event.target.checked)}
          error={errors.confirmAdult}
        />
        <CheckboxField
          label={t('register.acceptTerms')}
          checked={form.acceptTerms}
          onChange={(event) => set('acceptTerms', event.target.checked)}
          error={errors.acceptTerms}
        />
        <Button type="submit" disabled={register.isPending} className="w-full sm:w-auto">
          {register.isPending ? t('register.submitting') : t('register.submit')}
        </Button>
      </form>
    </section>
  )
}
