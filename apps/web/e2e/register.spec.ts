import { expect, test } from '@playwright/test'
import { emailTo } from './mailpit.ts'

// FR-ID-01, FR-ID-02 end to end: web, API, database, outbox job and Mailpit (testing-strategy.md).
test('a parent registers, gets the email and confirms their address', async ({ page, request }) => {
  const email = `e2e-${Date.now()}@example.com`

  await page.goto('/register')
  await page.getByLabel('Your name').fill('Sam Parent')
  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password', { exact: true }).fill('purple giraffes read slowly')
  await page.getByLabel('I am 18 or older').check()
  await page.getByLabel('I accept the terms and privacy notice').check()
  await page.getByRole('button', { name: 'Create account' }).click()

  await expect(page.getByRole('heading', { name: 'Check your email' })).toBeVisible()

  const text = await emailTo(request, email)
  const link = /https?:\/\/\S+\/verify-email\?token=[\w-]+/.exec(text)?.[0]
  expect(link).toBeDefined()
  await page.goto(new URL(link!).pathname + new URL(link!).search)

  await expect(page.getByText('Your email is confirmed. Welcome to the club!')).toBeVisible()

  // The link works once.
  await page.reload()
  await expect(page.getByRole('alert')).toContainText('This link has expired or was already used')
})
