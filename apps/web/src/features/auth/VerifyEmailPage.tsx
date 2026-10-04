import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import { Button } from '../../components/ui/button'
import { ApiError, apiClient, unwrap } from '../../lib/api'
import { useDocumentTitle } from '../../lib/useDocumentTitle'
import { ResendVerificationForm } from './ResendVerificationForm'

/** FR-ID-02: the page the emailed link opens. It verifies the token once, then shows the result. */
export function Component() {
  const { t } = useTranslation()
  useDocumentTitle(t('pages.verifyEmail'))
  const token = useSearchParams()[0].get('token')

  // A query (not a mutation) so the token is sent once even when React re-runs effects.
  const verification = useQuery<null, ApiError>({
    queryKey: ['verify-email', token],
    queryFn: () =>
      unwrap(apiClient.POST('/api/v1/auth/verify-email', { body: { token: token ?? '' } })).then(
        () => null,
      ),
    enabled: token !== null,
    retry: false,
    staleTime: Infinity,
  })

  return (
    <section className="mx-auto max-w-lg space-y-6">
      <h1 className="text-3xl font-bold">{t('pages.verifyEmail')}</h1>
      {token === null ? (
        <>
          <p>{t('verifyEmail.noToken')}</p>
          <ResendVerificationForm />
        </>
      ) : verification.isPending ? (
        <p role="status">{t('verifyEmail.checking')}</p>
      ) : verification.isSuccess ? (
        <div role="status" className="space-y-4 rounded-card bg-brand-soft p-6">
          <p className="text-lg font-semibold">{t('verifyEmail.done')}</p>
          <Button asChild>
            <Link to="/books">{t('verifyEmail.browse')}</Link>
          </Button>
        </div>
      ) : verification.error.code === 'TOKEN_INVALID' ||
        verification.error.code === 'VALIDATION_FAILED' ? (
        <>
          <p role="alert" className="rounded-card bg-danger-soft p-4">
            {t('verifyEmail.invalid')}
          </p>
          <ResendVerificationForm />
        </>
      ) : (
        <p role="alert">{t('errors.unexpected')}</p>
      )}
    </section>
  )
}
