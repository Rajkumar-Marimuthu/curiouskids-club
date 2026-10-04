import { expect, test } from '@playwright/test'

// Smoke test against the full stack: web dev server, API and database (testing-strategy.md).
test('home page loads and shows the API is OK', async ({ page }) => {
  await page.goto('/')

  await expect(page).toHaveTitle('Home | Curiouskids Club')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Welcome to Curiouskids Club')
  await expect(page.getByRole('status')).toContainText('API status: OK')
})

test('navigation reaches a page from the route map', async ({ page }) => {
  await page.goto('/')

  await page.getByRole('navigation').getByRole('link', { name: 'Books' }).click()

  await expect(page).toHaveURL('/books')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Books')
})
