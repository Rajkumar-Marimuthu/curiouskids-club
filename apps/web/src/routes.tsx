import type { ComponentType } from 'react'
import type { RouteObject } from 'react-router'
import { AdminLayout, MemberLayout, PublicLayout, StaffLayout } from './components/layout/layouts'
import { NotFound, PageLoading, RouteError } from './components/RouteError'

/**
 * The route map from docs/architecture/overview.md. Every page is loaded on demand (route-level
 * code splitting) and every route has its own error boundary.
 */
const page = (path: string | undefined, load: () => Promise<{ Component: ComponentType }>) =>
  ({ path, index: path === undefined, lazy: load, ErrorBoundary: RouteError }) as RouteObject

export const routes: RouteObject[] = [
  {
    Component: PublicLayout,
    ErrorBoundary: RouteError,
    HydrateFallback: PageLoading,
    children: [
      page(undefined, () => import('./features/home/HomePage')),
      page('books', () => import('./features/catalogue/BooksPage')),
      page('books/:id', () => import('./features/catalogue/BookDetailPage')),
      page('register', () => import('./features/auth/RegisterPage')),
      page('login', () => import('./features/auth/LoginPage')),
      page('verify-email', () => import('./features/auth/VerifyEmailPage')),
      page('reset-password', () => import('./features/auth/ResetPasswordPage')),
      page('confirm-email', () => import('./features/auth/ConfirmEmailPage')),
      { path: '*', Component: NotFound },
    ],
  },
  {
    Component: MemberLayout,
    ErrorBoundary: RouteError,
    HydrateFallback: PageLoading,
    children: [
      page('account', () => import('./features/account/AccountPage')),
      page('account/children', () => import('./features/account/ChildrenPage')),
      page('my/reservations', () => import('./features/reservations/MyReservationsPage')),
      page('my/loans', () => import('./features/reservations/MyLoansPage')),
    ],
  },
  {
    path: 'staff',
    Component: StaffLayout,
    ErrorBoundary: RouteError,
    HydrateFallback: PageLoading,
    children: [
      page('pick-list', () => import('./features/volunteer/PickListPage')),
      page('scan', () => import('./features/volunteer/ScanPage')),
      page('overdue', () => import('./features/volunteer/OverduePage')),
      page('titles', () => import('./features/volunteer/StaffTitlesPage')),
    ],
  },
  {
    path: 'admin',
    Component: AdminLayout,
    ErrorBoundary: RouteError,
    HydrateFallback: PageLoading,
    children: [
      page('windows', () => import('./features/admin/WindowsPage')),
      page('closures', () => import('./features/admin/ClosuresPage')),
      page('settings', () => import('./features/admin/SettingsPage')),
      page('users', () => import('./features/admin/UsersPage')),
      page('audit', () => import('./features/admin/AuditPage')),
    ],
  },
]
