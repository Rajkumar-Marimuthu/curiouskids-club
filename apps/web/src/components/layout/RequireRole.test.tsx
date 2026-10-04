import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { loggedInAs } from '../../test/handlers'
import { renderRoute } from '../../test/render'
import { server } from '../../test/server'

describe('route guards (FR-ID-03, FR-ID-07)', () => {
  it.each(['/my/reservations', '/staff/scan', '/admin/users'])(
    'sends an anonymous visitor from %s to log in, keeping the page to return to',
    async (path) => {
      const { router } = renderRoute(`${path}?tab=1`)

      expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeInTheDocument()
      expect(router.state.location.pathname).toBe('/login')
      expect(router.state.location.search).toBe(`?next=${encodeURIComponent(`${path}?tab=1`)}`)
    },
  )

  it.each([
    ['MEMBER', '/staff/scan'],
    ['MEMBER', '/admin/users'],
    ['VOLUNTEER', '/admin/users'],
  ] as const)('tells a %s that %s is not for them', async (role, path) => {
    server.use(loggedInAs(role))
    renderRoute(path)

    expect(
      await screen.findByRole('heading', { level: 1, name: "You can't open this page" }),
    ).toBeInTheDocument()
  })

  it.each([
    ['MEMBER', '/my/reservations', 'My reservations'],
    ['VOLUNTEER', '/staff/scan', 'Scan books'],
    ['ADMIN', '/staff/scan', 'Scan books'],
    ['ADMIN', '/admin/users', 'Users'],
  ] as const)('lets a %s open %s', async (role, path, title) => {
    server.use(loggedInAs(role))
    renderRoute(path)

    expect(await screen.findByRole('heading', { level: 1, name: title })).toBeInTheDocument()
  })

  it('shows links by role and logs out back to the home page', async () => {
    server.use(loggedInAs('VOLUNTEER'))
    const user = userEvent.setup()
    const { router } = renderRoute('/books')

    expect(await screen.findByRole('link', { name: 'Volunteer desk' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Administration' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Log in' })).not.toBeInTheDocument()

    server.use(loggedInAs(null))
    await user.click(screen.getByRole('button', { name: 'Log out' }))

    expect(await screen.findByRole('link', { name: 'Log in' })).toBeInTheDocument()
    await vi.waitFor(() => expect(router.state.location.pathname).toBe('/'))
    expect(screen.queryByRole('button', { name: 'Log out' })).not.toBeInTheDocument()
  })
})
