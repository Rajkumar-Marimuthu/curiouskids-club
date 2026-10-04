import { screen } from '@testing-library/react'
import { axe } from 'jest-axe'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { problem } from '../../test/handlers'
import { renderRoute } from '../../test/render'
import { server } from '../../test/server'

describe('Confirm email page', () => {
  it('FR-ID-06: confirms the token from the link once and says the email changed', async () => {
    const tokens: unknown[] = []
    server.use(
      http.post('*/api/v1/auth/email-change/confirm', async ({ request }) => {
        tokens.push(await request.json())
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderRoute('/confirm-email?token=abc123')

    expect(
      await screen.findByText('Your email address has been changed. Use it to log in from now on.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to your account' })).toHaveAttribute(
      'href',
      '/account',
    )
    expect(tokens).toEqual([{ token: 'abc123' }])
    expect(document.title).toBe('Confirm your new email | Curiouskids Club')
  })

  it('FR-ID-06: an expired, used or contested link explains what to do', async () => {
    server.use(http.post('*/api/v1/auth/email-change/confirm', () => problem(400, 'TOKEN_INVALID')))
    renderRoute('/confirm-email?token=old')

    expect(await screen.findByRole('alert')).toHaveTextContent('This link has expired')
  })

  it('FR-ID-06: a link without a token is explained without calling the API', async () => {
    let called = false
    server.use(
      http.post('*/api/v1/auth/email-change/confirm', () => {
        called = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { container } = renderRoute('/confirm-email')

    expect(await screen.findByRole('alert')).toHaveTextContent('This link has expired')
    expect(called).toBe(false)
    expect(await axe(container)).toHaveNoViolations()
  })
})
