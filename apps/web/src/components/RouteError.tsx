import { useTranslation } from 'react-i18next'
import { isRouteErrorResponse, Link, useRouteError } from 'react-router'
import { Button } from './ui/button'

/** Error boundary for every route: a failure in one page never blanks the whole app. */
export function RouteError() {
  const error = useRouteError()
  const { t } = useTranslation()

  if (isRouteErrorResponse(error) && error.status === 404) {
    return <NotFound />
  }

  return (
    <section role="alert" className="mx-auto max-w-xl px-4 py-12 text-center">
      <h1 className="text-2xl font-bold">{t('errors.title')}</h1>
      <p className="mt-3 text-ink-muted">{t('errors.body')}</p>
      <div className="mt-6 flex justify-center gap-3">
        <Button onClick={() => window.location.reload()}>{t('errors.retry')}</Button>
        <Button variant="secondary" asChild>
          <Link to="/">{t('errors.backHome')}</Link>
        </Button>
      </div>
    </section>
  )
}

export function NotFound() {
  const { t } = useTranslation()
  return (
    <section className="mx-auto max-w-xl px-4 py-12 text-center">
      <h1 className="text-2xl font-bold">{t('pages.notFound')}</h1>
      <p className="mt-3 text-ink-muted">{t('pages.notFoundBody')}</p>
      <Button variant="secondary" className="mt-6" asChild>
        <Link to="/">{t('errors.backHome')}</Link>
      </Button>
    </section>
  )
}

/** Shown while the first page of a visit is still loading its code. */
export function PageLoading() {
  const { t } = useTranslation()
  return (
    <p role="status" className="px-4 py-12 text-center text-ink-muted">
      {t('app.loading')}
    </p>
  )
}
