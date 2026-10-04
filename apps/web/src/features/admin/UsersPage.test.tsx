import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'jest-axe'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { loggedInAs, problem } from '../../test/handlers'
import { renderRoute } from '../../test/render'
import { server } from '../../test/server'

describe('Users page', () => {
  beforeEach(() => {
    server.use(loggedInAs('ADMIN'))
  })

  it('FR-ID-07: an admin invites a volunteer by email', async () => {
    const bodies: unknown[] = []
    server.use(
      http.post('*/api/v1/admin/invitations', async ({ request }) => {
        const body = (await request.json()) as { email: string; role: string }
        bodies.push(body)
        return HttpResponse.json(
          { id: 'inv-1', ...body, expiresAt: '2026-10-11T12:00:00Z' },
          { status: 201 },
        )
      }),
    )
    const user = userEvent.setup()
    const { container } = renderRoute('/admin/users')

    await user.type(await screen.findByLabelText('Email address'), ' helper@example.com ')
    expect(screen.getByLabelText('Role')).toHaveValue('VOLUNTEER')
    expect(await axe(container)).toHaveNoViolations()
    await user.click(screen.getByRole('button', { name: 'Send invitation' }))

    expect(await screen.findByRole('status')).toHaveTextContent(
      /Invitation sent to helper@example.com\. The link works until .*2026/,
    )
    expect(bodies).toEqual([{ email: 'helper@example.com', role: 'VOLUNTEER' }])
    expect(screen.getByLabelText('Email address')).toHaveValue('')
  })

  it('FR-ID-07: can invite another admin', async () => {
    const bodies: unknown[] = []
    server.use(
      http.post('*/api/v1/admin/invitations', async ({ request }) => {
        const body = (await request.json()) as { email: string; role: string }
        bodies.push(body)
        return HttpResponse.json(
          { id: 'inv-2', ...body, expiresAt: '2026-10-11T12:00:00Z' },
          { status: 201 },
        )
      }),
    )
    const user = userEvent.setup()
    renderRoute('/admin/users')

    await user.type(await screen.findByLabelText('Email address'), 'boss@example.com')
    await user.selectOptions(screen.getByLabelText('Role'), 'Admin')
    await user.click(screen.getByRole('button', { name: 'Send invitation' }))

    await screen.findByText(/Invitation sent to boss@example.com/)
    expect(bodies).toEqual([{ email: 'boss@example.com', role: 'ADMIN' }])
  })

  it('FR-ID-07: an email that already has an account gets a field error', async () => {
    server.use(http.post('*/api/v1/admin/invitations', () => problem(409, 'CONFLICT')))
    const user = userEvent.setup()
    renderRoute('/admin/users')

    await user.type(await screen.findByLabelText('Email address'), 'member@example.com')
    await user.click(screen.getByRole('button', { name: 'Send invitation' }))

    expect(await screen.findByText('This email already has an account.')).toBeInTheDocument()
    expect(screen.getByLabelText('Email address')).toHaveAccessibleDescription(
      'This email already has an account.',
    )
  })

  it('FR-ID-07: asks for an email before calling the API', async () => {
    let called = false
    server.use(
      http.post('*/api/v1/admin/invitations', () => {
        called = true
        return new HttpResponse(null, { status: 500 })
      }),
    )
    const user = userEvent.setup()
    renderRoute('/admin/users')

    await user.click(await screen.findByRole('button', { name: 'Send invitation' }))

    expect(screen.getByLabelText('Email address')).toHaveAccessibleDescription(
      'Please fill this in',
    )
    expect(called).toBe(false)
  })
})
