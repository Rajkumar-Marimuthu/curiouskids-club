import { screen } from '@testing-library/react'
import { axe } from 'jest-axe'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { problem } from '../../test/handlers'
import { renderRoute } from '../../test/render'
import { server } from '../../test/server'

describe('Home page', () => {
  it('shows the API status from GET /api/v1/ping in the club timezone', async () => {
    renderRoute('/')

    expect(await screen.findByText('OK')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Server time: 21 Sept 2026, 17:00')
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(
      'Welcome to Curiouskids Club',
    )
    expect(document.title).toBe('Home | Curiouskids Club')
  })

  it('shows the API as unavailable and raises one error toast on a server fault', async () => {
    server.use(http.get('*/api/v1/ping', () => problem(500, 'INTERNAL_ERROR', 'trace-500')))
    renderRoute('/')

    expect(await screen.findByText('Unavailable')).toBeInTheDocument()
    expect(await screen.findByText('Reference: trace-500')).toBeInTheDocument()
  })

  it('has no accessibility violations (NFR-07)', async () => {
    const { container } = renderRoute('/')
    await screen.findByText('OK')

    expect(await axe(container)).toHaveNoViolations()
  })
})
