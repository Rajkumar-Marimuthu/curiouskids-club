import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'jest-axe'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { problem } from '../../test/handlers'
import { renderRoute } from '../../test/render'
import { server } from '../../test/server'

describe('Verify email page', () => {
  it('FR-ID-02: verifies the token from the link once and confirms', async () => {
    const tokens: unknown[] = []
    server.use(
      http.post('*/api/v1/auth/verify-email', async ({ request }) => {
        tokens.push(await request.json())
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderRoute('/verify-email?token=abc123')

    expect(
      await screen.findByText('Your email is confirmed. Welcome to the club!'),
    ).toBeInTheDocument()
    expect(tokens).toEqual([{ token: 'abc123' }])
    expect(document.title).toBe('Verify your email | Curiouskids Club')
  })

  it('FR-ID-02: an expired or used link offers a new one', async () => {
    let resentTo: unknown
    server.use(
      http.post('*/api/v1/auth/verify-email', () => problem(400, 'TOKEN_INVALID')),
      http.post('*/api/v1/auth/verify-email/resend', async ({ request }) => {
        resentTo = await request.json()
        return new HttpResponse(null, { status: 202 })
      }),
    )
    const user = userEvent.setup()
    renderRoute('/verify-email?token=old')

    expect(await screen.findByRole('alert')).toHaveTextContent('This link has expired')
    await user.type(screen.getByLabelText('Email address'), 'sam@example.com')
    await user.click(screen.getByRole('button', { name: 'Send new link' }))

    expect(await screen.findByRole('status')).toHaveTextContent(
      "If that email needs confirming, we've sent a new link.",
    )
    expect(resentTo).toEqual({ email: 'sam@example.com' })
  })

  it('FR-ID-02: without a token, offers to send a new link', async () => {
    renderRoute('/verify-email')

    expect(await screen.findByText(/Open the link in the email/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Send new link' })).toBeDisabled()
  })

  it('has no accessibility violations (NFR-07)', async () => {
    server.use(http.post('*/api/v1/auth/verify-email', () => problem(400, 'TOKEN_INVALID')))
    const { container } = renderRoute('/verify-email?token=old')
    await screen.findByRole('alert')

    expect(await axe(container)).toHaveNoViolations()
  })
})
