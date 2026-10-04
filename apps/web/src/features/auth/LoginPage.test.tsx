import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'jest-axe'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { memberMe, problem } from '../../test/handlers'
import { renderRoute } from '../../test/render'
import { server } from '../../test/server'

async function logIn(email: string, password: string) {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('Email address'), email)
  await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: 'Log in' }))
}

describe('Login page', () => {
  it('FR-ID-03: logs in with the CSRF token and returns to the page that asked', async () => {
    const sent: { body: unknown; csrf: string | null }[] = []
    server.use(
      http.post('*/api/v1/auth/login', async ({ request }) => {
        sent.push({ body: await request.json(), csrf: request.headers.get('X-XSRF-TOKEN') })
        return HttpResponse.json(memberMe)
      }),
    )
    const { router } = renderRoute('/login?next=%2Fmy%2Floans')

    await logIn(' sam@example.com ', 'purple giraffes read slowly')

    expect(await screen.findByRole('heading', { level: 1, name: 'My loans' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/my/loans')
    expect(sent).toEqual([
      {
        body: { email: 'sam@example.com', password: 'purple giraffes read slowly' },
        csrf: 'test-csrf-token',
      },
    ])
    expect(await screen.findByRole('button', { name: 'Log out' })).toBeInTheDocument()
  })

  it.each([
    ['MEMBER', '/books'],
    ['VOLUNTEER', '/staff/pick-list'],
    ['ADMIN', '/admin/windows'],
  ] as const)('FR-ID-03: a %s without a page to return to lands on %s', async (role, path) => {
    server.use(
      http.post('*/api/v1/auth/login', () =>
        HttpResponse.json({ ...memberMe, role, familyId: undefined }),
      ),
    )
    const { router } = renderRoute('/login')

    await logIn('sam@example.com', 'purple giraffes read slowly')

    await vi.waitFor(() => expect(router.state.location.pathname).toBe(path))
  })

  it('ignores a return address outside the app', async () => {
    const { router } = renderRoute('/login?next=%2F%2Fevil.example%2Fsteal')

    await logIn('sam@example.com', 'purple giraffes read slowly')

    await vi.waitFor(() => expect(router.state.location.pathname).toBe('/books'))
  })

  it('FR-ID-03: shows one generic message when the email or password is wrong', async () => {
    server.use(http.post('*/api/v1/auth/login', () => problem(401, 'UNAUTHENTICATED')))
    renderRoute('/login')

    await logIn('sam@example.com', 'wrong password')

    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect.')
  })

  it('FR-ID-03: says how long to wait when attempts are blocked', async () => {
    server.use(
      http.post('*/api/v1/auth/login', () => {
        const response = problem(429, 'RATE_LIMITED')
        response.headers.set('Retry-After', '840')
        return response
      }),
    )
    renderRoute('/login')

    await logIn('sam@example.com', 'wrong password')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Too many attempts. Please wait 14 minutes and try again.',
    )
  })

  it('asks for both fields before calling the API', async () => {
    const user = userEvent.setup()
    renderRoute('/login')

    await user.click(await screen.findByRole('button', { name: 'Log in' }))

    expect(screen.getByLabelText('Email address')).toHaveAccessibleDescription(
      'Please fill this in',
    )
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription('Please fill this in')
  })

  it('NFR-07: has no accessibility violations', async () => {
    const { container } = renderRoute('/login')
    await screen.findByRole('heading', { level: 1, name: 'Log in' })

    expect(await axe(container)).toHaveNoViolations()
  })
})
