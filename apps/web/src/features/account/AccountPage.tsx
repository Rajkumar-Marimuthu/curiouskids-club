import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { components } from '../../api/schema'
import { Button } from '../../components/ui/button'
import { CheckboxField, TextField } from '../../components/ui/field'
import { apiClient, unwrap, useApi } from '../../lib/api'
import { fieldErrorsOf, rateLimitMinutes, type FieldErrors } from '../../lib/forms'
import { useDocumentTitle } from '../../lib/useDocumentTitle'

type Profile = components['schemas']['ProfileResponse']
type ProfileUpdate = components['schemas']['ProfileUpdateRequest']

const PROFILE_KEY = ['me', 'profile'] as const
const MIN_PASSWORD = 12

/**
 * FR-ID-06: the member's own details, reminder preferences (BR-36), email and password. Each
 * section saves on its own and announces the result in the shared status line.
 */
export function Component() {
  const { t } = useTranslation()
  useDocumentTitle(t('pages.account'))
  const profile = useApi(PROFILE_KEY, (api) => api.GET('/api/v1/me/profile'))
  const [notice, setNotice] = useState<string>()

  return (
    <section className="mx-auto max-w-xl space-y-8">
      <div>
        <h1 className="text-3xl font-bold">{t('pages.account')}</h1>
        <p className="mt-2 text-ink-muted">{t('account.intro')}</p>
      </div>
      <p role="status" className={notice ? 'rounded-card bg-brand-soft p-4' : 'sr-only'}>
        {notice}
      </p>
      {profile.isPending ? (
        <p className="text-ink-muted">{t('app.loading')}</p>
      ) : profile.isError ? (
        <p role="alert">{t('errors.unexpected')}</p>
      ) : (
        <>
          <DetailsForm profile={profile.data} onDone={setNotice} />
          <RemindersForm profile={profile.data} onDone={setNotice} />
          <EmailForm profile={profile.data} onDone={setNotice} />
          <PasswordForm onDone={setNotice} />
        </>
      )}
    </section>
  )
}

function Card({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="space-y-4 rounded-card border border-line p-5">
      <h2 className="text-xl font-bold">{title}</h2>
      {children}
    </div>
  )
}

function useUpdateProfile(onSaved: () => void) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: ProfileUpdate) => unwrap(apiClient.PATCH('/api/v1/me/profile', { body })),
    onSuccess: (profile) => {
      queryClient.setQueryData(PROFILE_KEY, profile)
      onSaved()
    },
  })
}

function RateLimitAlert({ error }: { error: unknown }) {
  const { t } = useTranslation()
  const minutes = rateLimitMinutes(error)
  if (!minutes) return null
  return (
    <p role="alert" className="rounded-card bg-danger-soft p-4">
      {t('errors.rateLimited', { minutes })}
    </p>
  )
}

function DetailsForm({ profile, onDone }: { profile: Profile; onDone: (n: string) => void }) {
  const { t } = useTranslation()
  const [name, setName] = useState(profile.name)
  const [phone, setPhone] = useState(profile.phone ?? '')
  const [nameMissing, setNameMissing] = useState(false)
  const save = useUpdateProfile(() => onDone(t('account.detailsSaved')))

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const missing = name.trim() === ''
    setNameMissing(missing)
    if (!missing) save.mutate({ name: name.trim(), phone: phone.trim() })
  }

  const errors = fieldErrorsOf(save.error)
  return (
    <Card title={t('account.detailsTitle')}>
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        <TextField
          label={t('account.name')}
          autoComplete="name"
          required
          maxLength={100}
          value={name}
          onChange={(event) => {
            setName(event.target.value)
            setNameMissing(false)
          }}
          error={nameMissing ? t('account.required') : errors.name}
        />
        <TextField
          label={t('account.phone')}
          hint={t('account.phoneHint')}
          type="tel"
          autoComplete="tel"
          maxLength={30}
          value={phone}
          onChange={(event) => setPhone(event.target.value)}
          error={errors.phone}
        />
        <Button type="submit" disabled={save.isPending}>
          {save.isPending ? t('account.saving') : t('account.saveDetails')}
        </Button>
      </form>
    </Card>
  )
}

function RemindersForm({ profile, onDone }: { profile: Profile; onDone: (n: string) => void }) {
  const { t } = useTranslation()
  const [remindPickup, setRemindPickup] = useState(profile.remindPickup)
  const [remindDueSoon, setRemindDueSoon] = useState(profile.remindDueSoon)
  const save = useUpdateProfile(() => onDone(t('account.remindersSaved')))

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    save.mutate({ remindPickup, remindDueSoon })
  }

  return (
    <Card title={t('account.remindersTitle')}>
      <form onSubmit={onSubmit} noValidate className="space-y-3">
        <fieldset className="space-y-1">
          <legend className="text-ink-muted">{t('account.remindersIntro')}</legend>
          <CheckboxField
            label={t('account.remindPickup')}
            checked={remindPickup}
            onChange={(event) => setRemindPickup(event.target.checked)}
          />
          <CheckboxField
            label={t('account.remindDueSoon')}
            checked={remindDueSoon}
            onChange={(event) => setRemindDueSoon(event.target.checked)}
          />
        </fieldset>
        <p className="text-sm text-ink-muted">{t('account.overdueAlways')}</p>
        <Button type="submit" disabled={save.isPending}>
          {save.isPending ? t('account.saving') : t('account.saveReminders')}
        </Button>
      </form>
    </Card>
  )
}

function EmailForm({ profile, onDone }: { profile: Profile; onDone: (n: string) => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [newEmail, setNewEmail] = useState('')
  const [currentPassword, setCurrentPassword] = useState('')
  const [clientErrors, setClientErrors] = useState<FieldErrors>({})
  const change = useMutation({
    mutationFn: (body: components['schemas']['EmailChangeRequest']) =>
      unwrap(apiClient.POST('/api/v1/me/email', { body })),
    onSuccess: (_, body) => {
      void queryClient.invalidateQueries({ queryKey: PROFILE_KEY })
      setNewEmail('')
      setCurrentPassword('')
      onDone(t('account.emailSent', { email: body.newEmail.trim() }))
    },
  })

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const found: FieldErrors = {}
    if (newEmail.trim() === '') found.newEmail = t('account.required')
    if (currentPassword === '') found.currentPassword = t('account.required')
    setClientErrors(found)
    if (Object.keys(found).length === 0) {
      change.mutate({ newEmail: newEmail.trim(), currentPassword })
    }
  }

  const errors = { ...fieldErrorsOf(change.error), ...clientErrors }
  return (
    <Card title={t('account.emailTitle')}>
      <p>{t('account.currentEmail', { email: profile.email })}</p>
      {profile.pendingEmail && (
        <p className="rounded-card bg-brand-soft p-4">
          {t('account.pendingEmail', { email: profile.pendingEmail })}
        </p>
      )}
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        <RateLimitAlert error={change.error} />
        <TextField
          label={t('account.newEmail')}
          hint={t('account.newEmailHint')}
          type="email"
          autoComplete="email"
          required
          maxLength={254}
          value={newEmail}
          onChange={(event) => {
            setNewEmail(event.target.value)
            setClientErrors((current) => ({ ...current, newEmail: undefined }))
          }}
          error={errors.newEmail}
        />
        <TextField
          label={t('account.currentPassword')}
          type="password"
          autoComplete="current-password"
          required
          maxLength={128}
          value={currentPassword}
          onChange={(event) => {
            setCurrentPassword(event.target.value)
            setClientErrors((current) => ({ ...current, currentPassword: undefined }))
          }}
          error={errors.currentPassword}
        />
        <Button type="submit" disabled={change.isPending}>
          {change.isPending ? t('account.sending') : t('account.changeEmail')}
        </Button>
      </form>
    </Card>
  )
}

function PasswordForm({ onDone }: { onDone: (n: string) => void }) {
  const { t } = useTranslation()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [clientErrors, setClientErrors] = useState<FieldErrors>({})
  const change = useMutation({
    mutationFn: (body: components['schemas']['PasswordChangeRequest']) =>
      unwrap(apiClient.POST('/api/v1/me/password', { body })),
    onSuccess: () => {
      setCurrentPassword('')
      setNewPassword('')
      onDone(t('account.passwordChanged'))
    },
  })

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const found: FieldErrors = {}
    if (currentPassword === '') found.currentPassword = t('account.required')
    if (newPassword.length < MIN_PASSWORD) found.newPassword = t('account.newPasswordHint')
    setClientErrors(found)
    if (Object.keys(found).length === 0) change.mutate({ currentPassword, newPassword })
  }

  const errors = { ...fieldErrorsOf(change.error), ...clientErrors }
  return (
    <Card title={t('account.passwordTitle')}>
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        <RateLimitAlert error={change.error} />
        <TextField
          label={t('account.currentPassword')}
          type="password"
          autoComplete="current-password"
          required
          maxLength={128}
          value={currentPassword}
          onChange={(event) => {
            setCurrentPassword(event.target.value)
            setClientErrors((current) => ({ ...current, currentPassword: undefined }))
          }}
          error={errors.currentPassword}
        />
        <TextField
          label={t('account.newPassword')}
          hint={t('account.newPasswordHint')}
          type="password"
          autoComplete="new-password"
          required
          maxLength={128}
          value={newPassword}
          onChange={(event) => {
            setNewPassword(event.target.value)
            setClientErrors((current) => ({ ...current, newPassword: undefined }))
          }}
          error={errors.newPassword}
        />
        <p className="text-sm text-ink-muted">{t('account.passwordOtherDevices')}</p>
        <Button type="submit" disabled={change.isPending}>
          {change.isPending ? t('account.saving') : t('account.changePassword')}
        </Button>
      </form>
    </Card>
  )
}
