import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'jest-axe'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { problem } from '../../test/handlers'
import { renderRoute } from '../../test/render'
import { server } from '../../test/server'

describe('Reset password page', () => {
  it('FR-ID-04: the login page links to it, and it sends a link without saying if the email exists', async () => {
    let sentTo: unknown
    server.use(
      http.post('*/api/v1/auth/password-reset/request', async ({ request }) => {
        sentTo = await request.json()
        return new HttpResponse(null, { status: 202 })
      }),
    )
    const user = userEvent.setup()
    renderRoute('/login')

    await user.click(await screen.findByRole('link', { name: 'Forgot your password?' }))
    await screen.findByRole('heading', { level: 1, name: 'Reset your password' })
    await user.type(screen.getByLabelText('Email address'), ' sam@example.com ')
    await user.click(screen.getByRole('button', { name: 'Send reset link' }))

    expect(await screen.findByRole('status')).toHaveTextContent(
      "If that email has an account, we've sent a link",
    )
    expect(sentTo).toEqual({ email: 'sam@example.com' })
  })

  it('FR-ID-04: says how long to wait when too many links were asked for', async () => {
    server.use(
      http.post('*/api/v1/auth/password-reset/request', () =>
        problem(429, 'RATE_LIMITED', 'trace-1', undefined, { 'Retry-After': '1500' }),
      ),
    )
    const user = userEvent.setup()
    renderRoute('/reset-password')

    await user.type(await screen.findByLabelText('Email address'), 'sam@example.com')
    await user.click(screen.getByRole('button', { name: 'Send reset link' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Too many requests. Please wait 25 minutes and try again.',
    )
  })

  it('FR-ID-04: from the emailed link, sets the new password and sends the user to log in', async () => {
    let body: unknown
    server.use(
      http.post('*/api/v1/auth/password-reset/confirm', async ({ request }) => {
        body = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const user = userEvent.setup()
    const { router } = renderRoute('/reset-password?token=abc123')

    await user.type(await screen.findByLabelText('New password'), 'orange penguins dance')
    await user.click(screen.getByRole('button', { name: 'Save new password' }))

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Your password has been changed. Log in with your new password.',
    )
    await vi.waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(body).toEqual({ token: 'abc123', password: 'orange penguins dance' })
  })

  it('FR-ID-04: shows the API field error for a weak password', async () => {
    server.use(
      http.post('*/api/v1/auth/password-reset/confirm', () =>
        problem(400, 'VALIDATION_FAILED', 'trace-1', [
          { field: 'password', message: 'This password is too common' },
        ]),
      ),
    )
    const user = userEvent.setup()
    renderRoute('/reset-password?token=abc123')

    await user.type(await screen.findByLabelText('New password'), 'password1234')
    await user.click(screen.getByRole('button', { name: 'Save new password' }))

    expect(await screen.findByText('This password is too common')).toBeInTheDocument()
  })

  it('FR-ID-04: an expired or used link offers a new one', async () => {
    server.use(
      http.post('*/api/v1/auth/password-reset/confirm', () => problem(400, 'TOKEN_INVALID')),
    )
    const user = userEvent.setup()
    renderRoute('/reset-password?token=old')

    await user.type(await screen.findByLabelText('New password'), 'orange penguins dance')
    await user.click(screen.getByRole('button', { name: 'Save new password' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('This link has expired')
    expect(screen.getByRole('button', { name: 'Send reset link' })).toBeInTheDocument()
  })

  it('checks the length before calling the API', async () => {
    const user = userEvent.setup()
    renderRoute('/reset-password?token=abc123')

    await user.type(await screen.findByLabelText('New password'), 'short')
    await user.click(screen.getByRole('button', { name: 'Save new password' }))

    expect(screen.getByLabelText('New password')).toHaveAttribute('aria-invalid', 'true')
  })

  it.each(['/reset-password', '/reset-password?token=abc123'])(
    'NFR-07: %s has no accessibility violations',
    async (path) => {
      const { container } = renderRoute(path)
      await screen.findByRole('heading', { level: 1, name: 'Reset your password' })

      expect(await axe(container)).toHaveNoViolations()
    },
  )
})
