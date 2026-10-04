import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import { Button } from '../../components/ui/button'
import { ApiError, apiClient, unwrap } from '../../lib/api'
import { useDocumentTitle } from '../../lib/useDocumentTitle'

/**
 * FR-ID-06: the page the link sent to a new email address opens. It confirms the token once, then
 * shows the result. Works whether or not the visitor is logged in on this device.
 */
export function Component() {
  const { t } = useTranslation()
  useDocumentTitle(t('pages.confirmEmail'))
  const queryClient = useQueryClient()
  const token = useSearchParams()[0].get('token')

  // A query (not a mutation) so the token is sent once even when React re-runs effects.
  const confirmation = useQuery<null, ApiError>({
    queryKey: ['confirm-email', token],
    queryFn: async () => {
      await unwrap(
        apiClient.POST('/api/v1/auth/email-change/confirm', { body: { token: token ?? '' } }),
      )
      void queryClient.invalidateQueries({ queryKey: ['me', 'profile'] })
      return null
    },
    enabled: token !== null,
    retry: false,
    staleTime: Infinity,
  })

  return (
    <section className="mx-auto max-w-lg space-y-6">
      <h1 className="text-3xl font-bold">{t('pages.confirmEmail')}</h1>
      {token === null ? (
        <p role="alert" className="rounded-card bg-danger-soft p-4">
          {t('confirmEmail.invalid')}
        </p>
      ) : confirmation.isPending ? (
        <p role="status">{t('confirmEmail.checking')}</p>
      ) : confirmation.isSuccess ? (
        <div role="status" className="space-y-4 rounded-card bg-brand-soft p-6">
          <p className="text-lg font-semibold">{t('confirmEmail.done')}</p>
          <Button asChild>
            <Link to="/account">{t('confirmEmail.toAccount')}</Link>
          </Button>
        </div>
      ) : confirmation.error.code === 'TOKEN_INVALID' ||
        confirmation.error.code === 'VALIDATION_FAILED' ? (
        <p role="alert" className="rounded-card bg-danger-soft p-4">
          {t('confirmEmail.invalid')}
        </p>
      ) : (
        <p role="alert">{t('errors.unexpected')}</p>
      )}
    </section>
  )
}
