import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { components } from '../../api/schema'
import { Button } from '../../components/ui/button'
import { SelectField, TextField } from '../../components/ui/field'
import { apiClient, ApiError, unwrap } from '../../lib/api'
import { fieldErrorsOf, type FieldErrors } from '../../lib/forms'
import { useDocumentTitle } from '../../lib/useDocumentTitle'

type Role = components['schemas']['InvitationRequest']['role']

const ROLES: Role[] = ['VOLUNTEER', 'ADMIN']

/** FR-ID-07: admins invite volunteers and admins. The user list follows in T-014 part 2. */
export function Component() {
  const { t } = useTranslation()
  useDocumentTitle(t('pages.users'))
  const [notice, setNotice] = useState<string>()

  return (
    <section className="mx-auto max-w-xl space-y-8">
      <div>
        <h1 className="text-3xl font-bold">{t('pages.users')}</h1>
        <p className="mt-2 text-ink-muted">{t('users.intro')}</p>
      </div>
      <p role="status" className={notice ? 'rounded-card bg-brand-soft p-4' : 'sr-only'}>
        {notice}
      </p>
      <InviteForm onInvited={setNotice} />
    </section>
  )
}

function InviteForm({ onInvited }: { onInvited: (notice: string) => void }) {
  const { t, i18n } = useTranslation()
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<Role>('VOLUNTEER')
  const [clientErrors, setClientErrors] = useState<FieldErrors>({})
  const invite = useMutation({
    mutationFn: (body: components['schemas']['InvitationRequest']) =>
      unwrap(apiClient.POST('/api/v1/admin/invitations', { body })),
    onSuccess: (invitation) => {
      setEmail('')
      const date = new Date(invitation.expiresAt).toLocaleDateString(i18n.language, {
        dateStyle: 'long',
      })
      onInvited(t('users.invited', { email: invitation.email, date }))
    },
  })

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const missing = email.trim() === ''
    setClientErrors(missing ? { email: t('users.required') } : {})
    if (!missing) invite.mutate({ email: email.trim(), role })
  }

  const exists = invite.error instanceof ApiError && invite.error.code === 'CONFLICT'
  const errors: FieldErrors = {
    ...fieldErrorsOf(invite.error),
    ...(exists ? { email: t('users.exists') } : {}),
    ...clientErrors,
  }

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4 rounded-card border border-line p-5">
      <h2 className="text-xl font-bold">{t('users.inviteTitle')}</h2>
      <TextField
        label={t('users.email')}
        type="email"
        autoComplete="off"
        required
        maxLength={254}
        value={email}
        onChange={(event) => {
          setEmail(event.target.value)
          setClientErrors({})
        }}
        error={errors.email}
      />
      <SelectField
        label={t('users.role')}
        required
        options={ROLES.map((value) => ({ value, label: t(`users.roles.${value}`) }))}
        value={role}
        onChange={(event) => setRole(event.target.value as Role)}
        error={errors.role}
      />
      <Button type="submit" disabled={invite.isPending}>
        {invite.isPending ? t('users.inviting') : t('users.invite')}
      </Button>
    </form>
  )
}
