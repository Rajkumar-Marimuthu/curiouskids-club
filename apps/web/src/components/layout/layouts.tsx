import { useTranslation } from 'react-i18next'
import { useMe, type Me } from '../../lib/auth'
import { AppShell, type NavItem } from './AppShell'
import { RequireRole } from './RequireRole'

/** Links to the areas a logged-in user can open, by role. */
function useAccountNav(me: Me | null | undefined): NavItem[] {
  const { t } = useTranslation()
  if (!me) return []
  const items: NavItem[] = [
    { to: '/my/reservations', label: t('nav.myReservations') },
    { to: '/account', label: t('nav.account') },
  ]
  if (me.role !== 'MEMBER') items.push({ to: '/staff/pick-list', label: t('nav.staffDesk') })
  if (me.role === 'ADMIN') items.push({ to: '/admin/windows', label: t('nav.admin') })
  return items
}

function usePublicNav(): NavItem[] {
  const { t } = useTranslation()
  const me = useMe()
  const account = useAccountNav(me.data)
  return [
    { to: '/', label: t('nav.home') },
    { to: '/books', label: t('nav.books') },
    ...(me.data
      ? account
      : me.isPending
        ? []
        : [
            { to: '/login', label: t('nav.login') },
            { to: '/register', label: t('nav.register') },
          ]),
  ]
}

/** Anyone: home, catalogue, sign-in and registration. */
export function PublicLayout() {
  return <AppShell nav={usePublicNav()} />
}

/** Logged-in families (and staff, who may also borrow). */
export function MemberLayout() {
  const { t } = useTranslation()
  return (
    <AppShell
      nav={[
        { to: '/books', label: t('nav.books') },
        { to: '/my/reservations', label: t('nav.myReservations') },
        { to: '/my/loans', label: t('nav.myLoans') },
        { to: '/account/children', label: t('nav.children') },
        { to: '/account', label: t('nav.account'), end: true },
      ]}
      guard={(page) => <RequireRole>{page}</RequireRole>}
    />
  )
}

/** Volunteers at the door: designed for one-handed phone use (NFR-07). */
export function StaffLayout() {
  const { t } = useTranslation()
  return (
    <AppShell
      area={t('areas.staff')}
      nav={[
        { to: '/staff/pick-list', label: t('nav.pickList') },
        { to: '/staff/scan', label: t('nav.scan') },
        { to: '/staff/overdue', label: t('nav.overdue') },
        { to: '/staff/titles', label: t('nav.titles') },
      ]}
      guard={(page) => <RequireRole roles={['VOLUNTEER', 'ADMIN']}>{page}</RequireRole>}
    />
  )
}

/** Admins: windows, closures, settings, users and audit. */
export function AdminLayout() {
  const { t } = useTranslation()
  return (
    <AppShell
      area={t('areas.admin')}
      nav={[
        { to: '/admin/windows', label: t('nav.windows') },
        { to: '/admin/closures', label: t('nav.closures') },
        { to: '/admin/settings', label: t('nav.settings') },
        { to: '/admin/users', label: t('nav.users') },
        { to: '/admin/audit', label: t('nav.audit') },
      ]}
      guard={(page) => <RequireRole roles={['ADMIN']}>{page}</RequireRole>}
    />
  )
}
