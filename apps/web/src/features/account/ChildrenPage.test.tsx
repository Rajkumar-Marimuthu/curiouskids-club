import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'jest-axe'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import type { components } from '../../api/schema'
import { loggedInAs, problem } from '../../test/handlers'
import { renderRoute } from '../../test/render'
import { server } from '../../test/server'

type Child = components['schemas']['ChildResponse']

/** An in-memory family, so the page's refetches see its own changes. */
function familyWith(initial: Child[]) {
  let children = [...initial]
  const requests: { method: string; body?: unknown }[] = []
  server.use(
    http.get('*/api/v1/me/children', () => HttpResponse.json(children)),
    http.post('*/api/v1/me/children', async ({ request }) => {
      const body = (await request.json()) as Omit<Child, 'id'>
      requests.push({ method: 'POST', body })
      const child = { id: `id-${children.length + 1}`, ...body }
      children = [...children, child]
      return HttpResponse.json(child, { status: 201 })
    }),
    http.patch('*/api/v1/me/children/:id', async ({ params, request }) => {
      const body = (await request.json()) as Partial<Child>
      requests.push({ method: 'PATCH', body })
      children = children.map((c) => (c.id === params.id ? { ...c, ...body } : c))
      return HttpResponse.json(children.find((c) => c.id === params.id))
    }),
    http.delete('*/api/v1/me/children/:id', ({ params }) => {
      requests.push({ method: 'DELETE' })
      children = children.filter((c) => c.id !== params.id)
      return new HttpResponse(null, { status: 204 })
    }),
  )
  return requests
}

describe('Your children page', () => {
  beforeEach(() => {
    server.use(loggedInAs('MEMBER'))
  })

  it('FR-ID-05: adds a child with only a first name and an age band', async () => {
    const requests = familyWith([])
    const user = userEvent.setup()
    renderRoute('/account/children')

    expect(await screen.findByText("You haven't added any children yet.")).toBeInTheDocument()
    await user.type(screen.getByLabelText('First name or nickname'), ' Ada ')
    await user.selectOptions(screen.getByLabelText('Age band'), '6 to 8')
    await user.click(screen.getByRole('button', { name: 'Add child' }))

    expect(await screen.findByText('Ada has been added.')).toBeInTheDocument()
    const list = await screen.findByRole('list', { name: 'Your children' })
    expect(within(list).getByText('Ada')).toBeInTheDocument()
    expect(within(list).getByText('Age 6 to 8')).toBeInTheDocument()
    expect(requests).toEqual([{ method: 'POST', body: { firstName: 'Ada', ageBand: '6-8' } }])
    expect(screen.getByLabelText('First name or nickname')).toHaveValue('')
  })

  it('FR-ID-05: asks for both fields before calling the API', async () => {
    const requests = familyWith([])
    const user = userEvent.setup()
    renderRoute('/account/children')

    await user.click(await screen.findByRole('button', { name: 'Add child' }))

    expect(screen.getByLabelText('First name or nickname')).toHaveAccessibleDescription(
      'Please fill this in',
    )
    expect(screen.getByLabelText('Age band')).toHaveAccessibleDescription('Choose an age band')
    expect(requests).toEqual([])
  })

  it('FR-ID-05: explains the limit when the family already has the most children', async () => {
    familyWith([])
    server.use(http.post('*/api/v1/me/children', () => problem(422, 'LIMIT_REACHED')))
    const user = userEvent.setup()
    renderRoute('/account/children')

    await user.type(await screen.findByLabelText('First name or nickname'), 'Seven')
    await user.selectOptions(screen.getByLabelText('Age band'), '3-5')
    await user.click(screen.getByRole('button', { name: 'Add child' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'You have added as many children as a family can have.',
    )
  })

  it('FR-ID-05: changes a child, sending only the name and age band', async () => {
    const requests = familyWith([{ id: 'c1', firstName: 'Ada', ageBand: '6-8' }])
    const user = userEvent.setup()
    renderRoute('/account/children')

    await user.click(await screen.findByRole('button', { name: 'Edit Ada' }))
    const form = screen.getByRole('form', { name: 'Edit Ada' })
    await user.clear(within(form).getByLabelText('First name or nickname'))
    await user.type(within(form).getByLabelText('First name or nickname'), 'Addie')
    await user.selectOptions(within(form).getByLabelText('Age band'), '9-12')
    await user.click(within(form).getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Changes saved.')).toBeInTheDocument()
    expect(await screen.findByText('Addie')).toBeInTheDocument()
    expect(screen.getByText('Age 9 to 12')).toBeInTheDocument()
    expect(requests).toEqual([{ method: 'PATCH', body: { firstName: 'Addie', ageBand: '9-12' } }])
  })

  it('FR-ID-05: removes a child after confirming', async () => {
    const requests = familyWith([
      { id: 'c1', firstName: 'Ada', ageBand: '6-8' },
      { id: 'c2', firstName: 'Bo', ageBand: '0-2' },
    ])
    const user = userEvent.setup()
    renderRoute('/account/children')

    await user.click(await screen.findByRole('button', { name: 'Remove Bo' }))
    expect(screen.getByText('Remove Bo from your family?')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Yes, remove' }))

    expect(await screen.findByText('Bo has been removed.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Remove Bo' })).not.toBeInTheDocument()
    expect(screen.getByText('Ada')).toBeInTheDocument()
    expect(requests).toEqual([{ method: 'DELETE' }])
  })

  it('NFR-07: has no accessibility violations with children listed', async () => {
    familyWith([{ id: 'c1', firstName: 'Ada', ageBand: '6-8' }])
    const { container } = renderRoute('/account/children')
    await screen.findByRole('list', { name: 'Your children' })

    expect(await axe(container)).toHaveNoViolations()
  })
})
