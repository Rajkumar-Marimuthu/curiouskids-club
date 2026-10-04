import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'

/** Sets the browser tab title, so screen-reader users hear where they are after navigating. */
export function useDocumentTitle(title: string) {
  const { t } = useTranslation()
  useEffect(() => {
    document.title = `${title} | ${t('app.name')}`
  }, [title, t])
}
