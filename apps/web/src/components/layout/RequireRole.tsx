import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, Navigate, useLocation } from 'react-router'
import { useMe, type Role } from '../../lib/auth'
import { useDocumentTitle } from '../../lib/useDocumentTitle'
import { PageLoading } from '../RouteError'
import { Button } from '../ui/button'

type RequireRoleProps = {
  /** Roles allowed in; omitted means anyone logged in. */
  roles?: Role[]
  children: ReactNode
}

/**
 * Keeps a layout's pages for the right people. Anonymous visitors go to the login page and come
 * back afterwards; logged-in users without the role see a short explanation. The API enforces the
 * same rules (FR-ID-07); this only avoids showing screens that would fail.
 */
export function RequireRole({ roles, children }: RequireRoleProps) {
  const me = useMe()
  const location = useLocation()

  if (me.isPending) return <PageLoading />
  if (me.isError) throw me.error
  if (me.data === null) {
    const next = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?next=${next}`} replace />
  }
  if (roles && !roles.includes(me.data.role)) return <NoAccess />
  return children
}

function NoAccess() {
  const { t } = useTranslation()
  useDocumentTitle(t('auth.noAccessTitle'))
  return (
    <section className="mx-auto max-w-xl py-12 text-center">
      <h1 className="text-2xl font-bold">{t('auth.noAccessTitle')}</h1>
      <p className="mt-3 text-ink-muted">{t('auth.noAccessBody')}</p>
      <Button variant="secondary" className="mt-6" asChild>
        <Link to="/">{t('errors.backHome')}</Link>
      </Button>
    </section>
  )
}
