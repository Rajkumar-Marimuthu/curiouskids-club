import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { RouteError } from './components/RouteError'
import { loggedInAs } from './test/handlers'
import { renderRoute } from './test/render'
import { server } from './test/server'

const routeMap: [path: string, title: string][] = [
  ['/books', 'Books'],
  ['/books/123', 'Book details'],
  ['/register', 'Join the club'],
  ['/login', 'Log in'],
  ['/verify-email', 'Verify your email'],
  ['/reset-password', 'Reset your password'],
  ['/confirm-email', 'Confirm your new email'],
  ['/account', 'Your account'],
  ['/account/children', 'Your children'],
  ['/my/reservations', 'My reservations'],
  ['/my/loans', 'My loans'],
  ['/staff/pick-list', 'Pick list'],
  ['/staff/scan', 'Scan books'],
  ['/staff/overdue', 'Overdue loans'],
  ['/staff/titles', 'Titles and copies'],
  ['/admin/windows', 'Handover windows'],
  ['/admin/closures', 'Closures'],
  ['/admin/settings', 'Settings'],
  ['/admin/users', 'Users'],
  ['/admin/audit', 'Audit log'],
]

describe('route map (docs/architecture/overview.md)', () => {
  it.each(routeMap)('%s renders its page', async (path, title) => {
    server.use(loggedInAs('ADMIN'))
    renderRoute(path)

    expect(await screen.findByRole('heading', { level: 1, name: title })).toBeInTheDocument()
    expect(screen.getByRole('main')).toBeInTheDocument()
  })

  it('shows the area name in staff and admin layouts', async () => {
    server.use(loggedInAs('VOLUNTEER'))
    renderRoute('/staff/scan')
    expect(await screen.findByText('Volunteer desk')).toBeInTheDocument()
  })

  it('shows a not-found page for unknown paths', async () => {
    renderRoute('/no-such-page')
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })

  it('contains a failing page in its own error boundary', async () => {
    function Broken(): never {
      throw new Error('boom')
    }
    renderRoute('/', [{ path: '/', Component: Broken, ErrorBoundary: RouteError }])

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
    expect(screen.getByRole('link', { name: 'Back to home' })).toBeInTheDocument()
  })
})
