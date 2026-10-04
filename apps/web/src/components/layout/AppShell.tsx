import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, NavLink, Outlet, useNavigate } from 'react-router'
import { useLogout, useMe } from '../../lib/auth'
import { cn } from '../../lib/utils'
import { Button } from '../ui/button'
import { Toaster } from '../ui/toaster'

export type NavItem = {
  to: string
  label: string
  /** Highlight only on this exact path, not on pages below it. */
  end?: boolean
}

type AppShellProps = {
  nav: NavItem[]
  /** Name of the area (for example "Volunteer desk"), shown next to the club name. */
  area?: string
  /** Wraps the page, for example to require a role; the header stays visible either way. */
  guard?: (page: ReactNode) => ReactNode
  children?: ReactNode
}

/** Page frame shared by every layout: skip link, header with navigation, main landmark. */
export function AppShell({ nav, area, guard = (page) => page, children }: AppShellProps) {
  const { t } = useTranslation()

  return (
    <div className="min-h-dvh">
      <a
        href="#main-content"
        className="sr-only rounded-full bg-brand px-4 py-2 text-white focus:not-sr-only focus:absolute focus:top-2 focus:left-2"
      >
        {t('app.skipToContent')}
      </a>
      <header className="border-b border-line bg-surface-raised">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
          <Link to="/" className="text-lg font-extrabold text-brand">
            {t('app.name')}
          </Link>
          {area && (
            <span className="rounded-full bg-accent-soft px-3 py-1 text-sm font-semibold">
              {area}
            </span>
          )}
          <nav aria-label={t('nav.label')} className="w-full sm:w-auto">
            <ul className="flex flex-wrap gap-1">
              {nav.map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    end={item.end ?? item.to === '/'}
                    className={({ isActive }) =>
                      cn(
                        'inline-flex min-h-11 items-center rounded-full px-3 font-semibold text-ink-muted hover:bg-brand-soft',
                        isActive && 'bg-brand-soft text-brand-strong',
                      )
                    }
                  >
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
          <LogoutButton />
        </div>
      </header>
      <main id="main-content" tabIndex={-1} className="mx-auto max-w-5xl px-4 py-8">
        {guard(children ?? <Outlet />)}
      </main>
      <Toaster />
    </div>
  )
}

/** Shown to anyone logged in; ends the session and returns to the home page (FR-ID-03). */
function LogoutButton() {
  const { t } = useTranslation()
  const me = useMe()
  const logout = useLogout()
  const navigate = useNavigate()
  if (!me.data) return null
  return (
    <Button
      variant="ghost"
      className="sm:ml-auto"
      disabled={logout.isPending}
      onClick={() =>
        logout.mutate(undefined, { onSuccess: () => navigate('/', { replace: true }) })
      }
    >
      {t('nav.logout')}
    </Button>
  )
}
