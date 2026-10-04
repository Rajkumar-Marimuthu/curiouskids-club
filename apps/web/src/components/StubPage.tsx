import { useTranslation } from 'react-i18next'
import { useDocumentTitle } from '../lib/useDocumentTitle'

/** Placeholder for a page from the route map; replaced by the task that builds the page. */
export function StubPage({ title }: { title: string }) {
  const { t } = useTranslation()
  useDocumentTitle(title)
  return (
    <section>
      <h1 className="text-3xl font-bold">{title}</h1>
      <p className="mt-3 text-ink-muted">{t('pages.comingSoon')}</p>
    </section>
  )
}
