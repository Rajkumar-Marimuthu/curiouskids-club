import { useTranslation } from 'react-i18next'
import { AppShell, type NavItem } from './AppShell'

function usePublicNav(): NavItem[] {
  const { t } = useTranslation()
  return [
    { to: '/', label: t('nav.home') },
    { to: '/books', label: t('nav.books') },
    { to: '/login', label: t('nav.login') },
    { to: '/register', label: t('nav.register') },
  ]
}

/** Anyone: home, catalogue, sign-in and registration. */
export function PublicLayout() {
  return <AppShell nav={usePublicNav()} />
}

/** Signed-in families. Access control arrives with authentication (T-012). */
export function MemberLayout() {
  const { t } = useTranslation()
  return (
    <AppShell
      nav={[
        { to: '/books', label: t('nav.books') },
        { to: '/my/reservations', label: t('nav.myReservations') },
        { to: '/my/loans', label: t('nav.myLoans') },
        { to: '/account', label: t('nav.account') },
      ]}
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
    />
  )
}
