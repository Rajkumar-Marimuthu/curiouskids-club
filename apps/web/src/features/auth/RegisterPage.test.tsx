import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'jest-axe'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { problem } from '../../test/handlers'
import { renderRoute } from '../../test/render'
import { server } from '../../test/server'

async function fillIn(user: ReturnType<typeof userEvent.setup>, password = 'purple giraffes read') {
  await screen.findByRole('heading', { name: 'Join the club' })
  await user.type(screen.getByLabelText('Your name'), 'Sam Parent')
  await user.type(screen.getByLabelText('Email address'), ' sam@example.com ')
  await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByLabelText('I am 18 or older'))
  await user.click(screen.getByLabelText('I accept the terms and privacy notice'))
}

describe('Register page', () => {
  it('FR-ID-01: sends the details and asks the parent to check their email', async () => {
    let sent: unknown
    server.use(
      http.post('*/api/v1/auth/register', async ({ request }) => {
        sent = await request.json()
        return new HttpResponse(null, { status: 202 })
      }),
    )
    const user = userEvent.setup()
    renderRoute('/register')

    await fillIn(user)
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByRole('heading', { name: 'Check your email' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent("We've sent a link to sam@example.com")
    expect(sent).toEqual({
      name: 'Sam Parent',
      email: 'sam@example.com',
      password: 'purple giraffes read',
      confirmAdult: true,
      acceptTerms: true,
    })
    expect(document.title).toBe('Join the club | Curiouskids Club')
  })

  it('FR-ID-01: requires both consent boxes and a 12-character password before sending (BR-28)', async () => {
    let calls = 0
    server.use(
      http.post('*/api/v1/auth/register', () => {
        calls++
        return new HttpResponse(null, { status: 202 })
      }),
    )
    const user = userEvent.setup()
    renderRoute('/register')
    await screen.findByRole('heading', { name: 'Join the club' })

    await user.type(screen.getByLabelText('Password'), 'short')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Please fix the highlighted fields.')
    expect(screen.getByLabelText('I am 18 or older')).toHaveAccessibleDescription(
      'Please confirm you are 18 or older',
    )
    expect(screen.getByLabelText('I accept the terms and privacy notice')).toBeInvalid()
    expect(screen.getByLabelText('Password')).toBeInvalid()
    expect(calls).toBe(0)
  })

  it('FR-ID-01: shows the API field error for a common password next to the field', async () => {
    server.use(
      http.post('*/api/v1/auth/register', () =>
        problem(400, 'VALIDATION_FAILED', 'trace-1', [
          {
            field: 'password',
            message: 'This password is too common. Choose a less predictable one',
          },
        ]),
      ),
    )
    const user = userEvent.setup()
    renderRoute('/register')

    await fillIn(user, 'passwordpassword')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    const password = screen.getByLabelText('Password')
    expect(await screen.findByText(/too common/)).toBeInTheDocument()
    expect(password).toBeInvalid()
    expect(password).toHaveAccessibleDescription(
      'At least 12 characters. A few random words make a good one. This password is too common. Choose a less predictable one',
    )
  })

  it('has no accessibility violations, with and without errors (NFR-07)', async () => {
    const user = userEvent.setup()
    const { container } = renderRoute('/register')
    await screen.findByRole('heading', { name: 'Join the club' })
    expect(await axe(container)).toHaveNoViolations()

    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await axe(container)).toHaveNoViolations()
  })
})
