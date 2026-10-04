import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'jest-axe'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { problem } from '../../test/handlers'
import { renderRoute } from '../../test/render'
import { server } from '../../test/server'

describe('Accept invitation page', () => {
  it('FR-ID-07: sets a password from the link, then sends the person to log in', async () => {
    const bodies: unknown[] = []
    server.use(
      http.post('*/api/v1/auth/invitations/accept', async ({ request }) => {
        bodies.push(await request.json())
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const user = userEvent.setup()
    const { container } = renderRoute('/accept-invitation?token=abc123')

    await user.type(
      await screen.findByLabelText('Choose a password'),
      'orange penguins dance quietly',
    )
    expect(await axe(container)).toHaveNoViolations()
    await user.click(screen.getByRole('button', { name: 'Create my account' }))

    expect(
      await screen.findByText('Your account is ready. Log in with your email and new password.'),
    ).toBeInTheDocument()
    expect(bodies).toEqual([{ token: 'abc123', password: 'orange penguins dance quietly' }])
  })

  it('FR-ID-07: a short password is caught before calling the API', async () => {
    let called = false
    server.use(
      http.post('*/api/v1/auth/invitations/accept', () => {
        called = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const user = userEvent.setup()
    renderRoute('/accept-invitation?token=abc123')

    await user.type(await screen.findByLabelText('Choose a password'), 'short')
    await user.click(screen.getByRole('button', { name: 'Create my account' }))

    expect(screen.getByLabelText('Choose a password')).toHaveAccessibleDescription(
      /At least 12 characters/,
    )
    expect(called).toBe(false)
  })

  it('FR-ID-07: an expired or used invitation says to ask an admin', async () => {
    server.use(http.post('*/api/v1/auth/invitations/accept', () => problem(400, 'TOKEN_INVALID')))
    const user = userEvent.setup()
    renderRoute('/accept-invitation?token=old')

    await user.type(
      await screen.findByLabelText('Choose a password'),
      'orange penguins dance quietly',
    )
    await user.click(screen.getByRole('button', { name: 'Create my account' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Ask an admin to send you a new one')
  })

  it('FR-ID-07: a link without a token is explained', async () => {
    renderRoute('/accept-invitation')

    expect(await screen.findByRole('alert')).toHaveTextContent('This invitation has expired')
  })
})
