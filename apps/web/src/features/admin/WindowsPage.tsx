import { useTranslation } from 'react-i18next'
import { StubPage } from '../../components/StubPage'

export function Component() {
  const { t } = useTranslation()
  return <StubPage title={t('pages.windows')} />
}
