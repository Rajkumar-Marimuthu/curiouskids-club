import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'jest-axe'
import { describe, expect, it, vi } from 'vitest'
import { Button } from './button'

describe('Button', () => {
  it('is a real button that responds to keyboard and click', async () => {
    const onClick = vi.fn()
    render(<Button onClick={onClick}>Save</Button>)

    const button = screen.getByRole('button', { name: 'Save' })
    expect(button).toHaveAttribute('type', 'button')
    button.focus()
    await userEvent.keyboard('{Enter}')
    await userEvent.click(button)

    expect(onClick).toHaveBeenCalledTimes(2)
  })

  it('can style a link as a button (NFR-07: no accessibility violations)', async () => {
    const { container } = render(
      <Button asChild variant="secondary">
        <a href="/books">Books</a>
      </Button>,
    )

    expect(screen.getByRole('link', { name: 'Books' })).not.toHaveAttribute('type')
    expect(await axe(container)).toHaveNoViolations()
  })
})
