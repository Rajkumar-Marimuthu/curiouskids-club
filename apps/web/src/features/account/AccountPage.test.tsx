import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'jest-axe'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import type { components } from '../../api/schema'
import { loggedInAs, memberProfile, problem } from '../../test/handlers'
import { renderRoute } from '../../test/render'
import { server } from '../../test/server'

type Profile = components['schemas']['ProfileResponse']

/** An in-memory account, so the page's refetches see its own changes. */
function accountWith(initial: Profile) {
  let profile = { ...initial }
  const requests: { path: string; body: unknown }[] = []
  server.use(
    http.get('*/api/v1/me/profile', () => HttpResponse.json(profile)),
    http.patch('*/api/v1/me/profile', async ({ request }) => {
      const body = (await request.json()) as Partial<Profile>
      requests.push({ path: 'profile', body })
      profile = { ...profile, ...body }
      if (profile.phone === '') delete profile.phone
      return HttpResponse.json(profile)
    }),
    http.post('*/api/v1/me/email', async ({ request }) => {
      const body = (await request.json()) as { newEmail: string }
      requests.push({ path: 'email', body })
      profile = { ...profile, pendingEmail: body.newEmail }
      return new HttpResponse(null, { status: 202 })
    }),
    http.post('*/api/v1/me/password', async ({ request }) => {
      requests.push({ path: 'password', body: await request.json() })
      return new HttpResponse(null, { status: 204 })
    }),
  )
  return requests
}

function section(name: string) {
  return screen.getByRole('heading', { level: 2, name }).parentElement as HTMLElement
}

describe('Your account page', () => {
  beforeEach(() => {
    server.use(loggedInAs('MEMBER'))
  })

  it('FR-ID-06: shows the details, login email and reminder choices', async () => {
    accountWith({ ...memberProfile, phone: '0123 456', remindDueSoon: false })
    const { container } = renderRoute('/account')

    expect(await screen.findByLabelText('Your name')).toHaveValue('Sam Parent')
    expect(screen.getByLabelText('Phone (optional)')).toHaveValue('0123 456')
    expect(screen.getByText('You log in with sam@example.com.')).toBeInTheDocument()
    expect(screen.getByLabelText('Before my pickup window')).toBeChecked()
    expect(screen.getByLabelText('When a book is due back soon')).not.toBeChecked()
    expect(screen.getByText('Reminders about overdue books are always sent.')).toBeInTheDocument()
    expect(await axe(container)).toHaveNoViolations()
  })

  it('FR-ID-06: saves a new name and removes the phone', async () => {
    const requests = accountWith({ ...memberProfile, phone: '0123 456' })
    const user = userEvent.setup()
    renderRoute('/account')

    const name = await screen.findByLabelText('Your name')
    await user.clear(name)
    await user.type(name, ' Alex Parent ')
    await user.clear(screen.getByLabelText('Phone (optional)'))
    await user.click(screen.getByRole('button', { name: 'Save details' }))

    expect(await screen.findByText('Your details have been saved.')).toBeInTheDocument()
    expect(requests).toEqual([{ path: 'profile', body: { name: 'Alex Parent', phone: '' } }])
  })

  it('FR-ID-06: asks for a name before calling the API', async () => {
    const requests = accountWith(memberProfile)
    const user = userEvent.setup()
    renderRoute('/account')

    await user.clear(await screen.findByLabelText('Your name'))
    await user.click(screen.getByRole('button', { name: 'Save details' }))

    expect(screen.getByLabelText('Your name')).toHaveAccessibleDescription('Please fill this in')
    expect(requests).toEqual([])
  })

  it('BR-36: switches off pickup reminders', async () => {
    const requests = accountWith(memberProfile)
    const user = userEvent.setup()
    renderRoute('/account')

    await user.click(await screen.findByLabelText('Before my pickup window'))
    await user.click(screen.getByRole('button', { name: 'Save reminders' }))

    expect(await screen.findByText('Your reminder choices have been saved.')).toBeInTheDocument()
    expect(requests).toEqual([
      { path: 'profile', body: { remindPickup: false, remindDueSoon: true } },
    ])
  })

  it('FR-ID-06: asks for a new email and shows it as waiting for confirmation', async () => {
    const requests = accountWith(memberProfile)
    const user = userEvent.setup()
    renderRoute('/account')

    await user.type(await screen.findByLabelText('New email address'), 'new@example.com')
    await user.type(
      within(section('Login email')).getByLabelText('Current password'),
      'purple giraffes read slowly',
    )
    await user.click(screen.getByRole('button', { name: 'Send confirmation link' }))

    expect(
      await screen.findByText(
        'Check new@example.com for a link to confirm the change. It works for 24 hours.',
      ),
    ).toBeInTheDocument()
    expect(
      await screen.findByText(
        'We sent a confirmation link to new@example.com. Your login email changes when you use it.',
      ),
    ).toBeInTheDocument()
    expect(requests).toEqual([
      {
        path: 'email',
        body: { newEmail: 'new@example.com', currentPassword: 'purple giraffes read slowly' },
      },
    ])
    expect(screen.getByLabelText('New email address')).toHaveValue('')
  })

  it('FR-ID-06: shows the field error for a wrong current password', async () => {
    accountWith(memberProfile)
    server.use(
      http.post('*/api/v1/me/email', () =>
        problem(400, 'VALIDATION_FAILED', 'trace-1', [
          { field: 'currentPassword', message: 'Not your current password' },
        ]),
      ),
    )
    const user = userEvent.setup()
    renderRoute('/account')

    await user.type(await screen.findByLabelText('New email address'), 'new@example.com')
    const password = within(section('Login email')).getByLabelText('Current password')
    await user.type(password, 'wrong password')
    await user.click(screen.getByRole('button', { name: 'Send confirmation link' }))

    expect(
      await within(section('Login email')).findByText('Not your current password'),
    ).toBeInTheDocument()
    expect(password).toHaveAccessibleDescription('Not your current password')
  })

  it('FR-ID-06: changes the password and says other devices are logged out', async () => {
    const requests = accountWith(memberProfile)
    const user = userEvent.setup()
    renderRoute('/account')

    const card = await screen.findByRole('heading', { level: 2, name: 'Password' })
    const form = within(card.parentElement as HTMLElement)
    await user.type(form.getByLabelText('Current password'), 'purple giraffes read slowly')
    await user.type(form.getByLabelText('New password'), 'orange penguins dance quietly')
    await user.click(screen.getByRole('button', { name: 'Change password' }))

    expect(
      await screen.findByText(
        "Your password has been changed. You've been logged out on your other devices.",
      ),
    ).toBeInTheDocument()
    expect(requests).toEqual([
      {
        path: 'password',
        body: {
          currentPassword: 'purple giraffes read slowly',
          newPassword: 'orange penguins dance quietly',
        },
      },
    ])
  })

  it('FR-ID-03: too many wrong passwords asks the member to wait', async () => {
    accountWith(memberProfile)
    server.use(
      http.post('*/api/v1/me/password', () =>
        problem(429, 'RATE_LIMITED', 'trace-1', undefined, { 'Retry-After': '600' }),
      ),
    )
    const user = userEvent.setup()
    renderRoute('/account')

    const card = await screen.findByRole('heading', { level: 2, name: 'Password' })
    const form = within(card.parentElement as HTMLElement)
    await user.type(form.getByLabelText('Current password'), 'wrong password')
    await user.type(form.getByLabelText('New password'), 'orange penguins dance quietly')
    await user.click(screen.getByRole('button', { name: 'Change password' }))

    expect(await form.findByRole('alert')).toHaveTextContent('Please wait 10 minutes')
  })
})
