import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { components } from '../../api/schema'
import { Button } from '../../components/ui/button'
import { SelectField, TextField } from '../../components/ui/field'
import { apiClient, ApiError, unwrap, useApi } from '../../lib/api'
import { fieldErrorsOf, type FieldErrors } from '../../lib/forms'
import { useDocumentTitle } from '../../lib/useDocumentTitle'

type Child = components['schemas']['ChildResponse']
type AgeBand = Child['ageBand']

const AGE_BANDS: AgeBand[] = ['0-2', '3-5', '6-8', '9-12', '13+']
const CHILDREN_KEY = ['me', 'children'] as const
const MAX_NAME = 40

/** FR-ID-05: a family's children, with a first name or nickname and an age band only (BR-30). */
export function Component() {
  const { t } = useTranslation()
  useDocumentTitle(t('pages.children'))
  const children = useApi(CHILDREN_KEY, (api) => api.GET('/api/v1/me/children'))
  const [notice, setNotice] = useState<string>()

  return (
    <section className="mx-auto max-w-xl space-y-8">
      <div>
        <h1 className="text-3xl font-bold">{t('pages.children')}</h1>
        <p className="mt-2 text-ink-muted">{t('children.intro')}</p>
      </div>
      <p role="status" className={notice ? 'rounded-card bg-brand-soft p-4' : 'sr-only'}>
        {notice}
      </p>
      {children.isPending ? (
        <p className="text-ink-muted">{t('app.loading')}</p>
      ) : children.data?.length ? (
        <ul aria-label={t('children.listLabel')} className="space-y-3">
          {children.data.map((child) => (
            <ChildRow key={child.id} child={child} onDone={setNotice} />
          ))}
        </ul>
      ) : (
        <p>{t('children.empty')}</p>
      )}
      <AddChildForm
        onAdded={(child) => setNotice(t('children.added', { name: child.firstName }))}
      />
    </section>
  )
}

function useAgeBandOptions() {
  const { t } = useTranslation()
  return AGE_BANDS.map((band) => ({ value: band, label: t(`children.ageBands.${band}`) }))
}

type Draft = { firstName: string; ageBand: AgeBand | '' }

function validate(draft: Draft, t: ReturnType<typeof useTranslation>['t']): FieldErrors {
  const errors: FieldErrors = {}
  if (draft.firstName.trim() === '') errors.firstName = t('children.required')
  if (draft.ageBand === '') errors.ageBand = t('children.chooseAgeBand')
  return errors
}

function AddChildForm({ onAdded }: { onAdded: (child: Child) => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const ageBands = useAgeBandOptions()
  const [draft, setDraft] = useState<Draft>({ firstName: '', ageBand: '' })
  const [clientErrors, setClientErrors] = useState<FieldErrors>({})
  const add = useMutation({
    mutationFn: (body: components['schemas']['ChildRequest']) =>
      unwrap(apiClient.POST('/api/v1/me/children', { body })),
    onSuccess: (child) => {
      void queryClient.invalidateQueries({ queryKey: CHILDREN_KEY })
      setDraft({ firstName: '', ageBand: '' })
      onAdded(child)
    },
  })

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const found = validate(draft, t)
    setClientErrors(found)
    if (Object.keys(found).length > 0 || draft.ageBand === '') return
    add.mutate({ firstName: draft.firstName.trim(), ageBand: draft.ageBand })
  }

  const errors = { ...fieldErrorsOf(add.error), ...clientErrors }
  const limitReached = add.error instanceof ApiError && add.error.code === 'LIMIT_REACHED'

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4 rounded-card border border-line p-5">
      <h2 className="text-xl font-bold">{t('children.addTitle')}</h2>
      {limitReached && (
        <p role="alert" className="rounded-card bg-danger-soft p-4">
          {t('children.limitReached')}
        </p>
      )}
      <TextField
        label={t('children.firstName')}
        autoComplete="off"
        required
        maxLength={MAX_NAME}
        value={draft.firstName}
        onChange={(event) => {
          setDraft((current) => ({ ...current, firstName: event.target.value }))
          setClientErrors((current) => ({ ...current, firstName: undefined }))
        }}
        error={errors.firstName}
      />
      <SelectField
        label={t('children.ageBand')}
        required
        placeholder={t('children.chooseAgeBand')}
        options={ageBands}
        value={draft.ageBand}
        onChange={(event) => {
          setDraft((current) => ({ ...current, ageBand: event.target.value as AgeBand }))
          setClientErrors((current) => ({ ...current, ageBand: undefined }))
        }}
        error={errors.ageBand}
      />
      <Button type="submit" disabled={add.isPending}>
        {add.isPending ? t('children.adding') : t('children.add')}
      </Button>
    </form>
  )
}

function ChildRow({ child, onDone }: { child: Child; onDone: (notice: string) => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const ageBands = useAgeBandOptions()
  const [mode, setMode] = useState<'view' | 'edit' | 'confirm'>('view')
  const [draft, setDraft] = useState<Draft>({ firstName: child.firstName, ageBand: child.ageBand })
  const [clientErrors, setClientErrors] = useState<FieldErrors>({})
  const refresh = () => queryClient.invalidateQueries({ queryKey: CHILDREN_KEY })
  const update = useMutation({
    mutationFn: (body: components['schemas']['ChildUpdateRequest']) =>
      unwrap(
        apiClient.PATCH('/api/v1/me/children/{id}', { params: { path: { id: child.id } }, body }),
      ),
    onSuccess: () => {
      void refresh()
      setMode('view')
      onDone(t('children.saved'))
    },
  })
  const remove = useMutation({
    mutationFn: () =>
      unwrap(apiClient.DELETE('/api/v1/me/children/{id}', { params: { path: { id: child.id } } })),
    onSuccess: () => {
      void refresh()
      onDone(t('children.removed', { name: child.firstName }))
    },
    onError: (error) => {
      // Already gone (another device): just refresh the list.
      if (error instanceof ApiError && error.code === 'NOT_FOUND') void refresh()
    },
  })

  if (mode === 'edit') {
    const errors = { ...fieldErrorsOf(update.error), ...clientErrors }
    const onSubmit = (event: FormEvent) => {
      event.preventDefault()
      const found = validate(draft, t)
      setClientErrors(found)
      if (Object.keys(found).length > 0 || draft.ageBand === '') return
      update.mutate({ firstName: draft.firstName.trim(), ageBand: draft.ageBand })
    }
    return (
      <li className="rounded-card border border-line p-4">
        <form
          onSubmit={onSubmit}
          noValidate
          aria-label={t('children.editTitle', { name: child.firstName })}
          className="space-y-4"
        >
          <TextField
            label={t('children.firstName')}
            autoComplete="off"
            required
            maxLength={MAX_NAME}
            value={draft.firstName}
            onChange={(event) => {
              setDraft((current) => ({ ...current, firstName: event.target.value }))
              setClientErrors((current) => ({ ...current, firstName: undefined }))
            }}
            error={errors.firstName}
          />
          <SelectField
            label={t('children.ageBand')}
            required
            options={ageBands}
            value={draft.ageBand}
            onChange={(event) =>
              setDraft((current) => ({ ...current, ageBand: event.target.value as AgeBand }))
            }
            error={errors.ageBand}
          />
          <div className="flex gap-3">
            <Button type="submit" disabled={update.isPending}>
              {update.isPending ? t('children.saving') : t('children.save')}
            </Button>
            <Button variant="secondary" onClick={() => setMode('view')}>
              {t('children.cancel')}
            </Button>
          </div>
        </form>
      </li>
    )
  }

  return (
    <li className="flex flex-wrap items-center gap-3 rounded-card border border-line p-4">
      <div className="grow">
        <p className="text-lg font-semibold">{child.firstName}</p>
        <p className="text-ink-muted">
          {t('children.ageBandValue', { band: t(`children.ageBands.${child.ageBand}`) })}
        </p>
      </div>
      {mode === 'confirm' ? (
        <div className="flex flex-wrap items-center gap-3">
          <p>{t('children.confirmRemove', { name: child.firstName })}</p>
          <Button disabled={remove.isPending} onClick={() => remove.mutate()}>
            {t('children.confirmRemoveYes')}
          </Button>
          <Button variant="secondary" onClick={() => setMode('view')}>
            {t('children.cancel')}
          </Button>
        </div>
      ) : (
        <div className="flex gap-2">
          <Button
            variant="secondary"
            aria-label={t('children.edit', { name: child.firstName })}
            onClick={() => {
              setDraft({ firstName: child.firstName, ageBand: child.ageBand })
              setMode('edit')
            }}
          >
            {t('children.editShort')}
          </Button>
          <Button
            variant="ghost"
            aria-label={t('children.remove', { name: child.firstName })}
            onClick={() => setMode('confirm')}
          >
            {t('children.removeShort')}
          </Button>
        </div>
      )}
    </li>
  )
}
