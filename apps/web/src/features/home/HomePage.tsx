import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { Button } from '../../components/ui/button'
import { useApi } from '../../lib/api'
import { formatDateTime } from '../../lib/dates'
import { useDocumentTitle } from '../../lib/useDocumentTitle'

export function Component() {
  const { t } = useTranslation()
  useDocumentTitle(t('nav.home'))
  const ping = useApi(['ping'], (api) => api.GET('/api/v1/ping'))

  return (
    <section className="space-y-8">
      <div className="rounded-card bg-brand-soft p-8">
        <h1 className="text-3xl font-extrabold text-brand-strong">{t('home.title')}</h1>
        <p className="mt-3 max-w-prose text-lg">{t('home.intro')}</p>
        <div className="mt-6 flex flex-wrap gap-3">
          <Button asChild>
            <Link to="/books">{t('nav.books')}</Link>
          </Button>
          <Button variant="secondary" asChild>
            <Link to="/register">{t('nav.register')}</Link>
          </Button>
        </div>
      </div>

      <p role="status" className="text-sm text-ink-muted">
        {t('home.apiStatus')}:{' '}
        {ping.isPending ? (
          t('home.apiChecking')
        ) : ping.isSuccess && ping.data.status === 'ok' ? (
          <>
            <strong className="text-success">{t('home.apiOk')}</strong>{' '}
            <span>{t('home.serverTime', { time: formatDateTime(ping.data.time) })}</span>
          </>
        ) : (
          <strong className="text-danger">{t('home.apiDown')}</strong>
        )}
      </p>
    </section>
  )
}
