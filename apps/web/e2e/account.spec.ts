import { expect, test } from '@playwright/test'
import { emailTo } from './mailpit.ts'

// FR-ID-06 end to end: a member changes details and reminders, then moves to a new email address
// with the link sent to it, and logs in with that address.
test('a parent manages their account and changes email', async ({ page, request }) => {
  const stamp = Date.now()
  const email = `e2e-account-${stamp}@example.com`
  const newEmail = `e2e-account-new-${stamp}@example.com`
  const password = 'purple giraffes read slowly'

  await page.goto('/register')
  await page.getByLabel('Your name').fill('Sam Parent')
  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByLabel('I am 18 or older').check()
  await page.getByLabel('I accept the terms and privacy notice').check()
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: 'Check your email' })).toBeVisible()

  await page.goto('/account')
  await expect(page).toHaveURL(/\/login/)
  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Log in' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Your account' })).toBeVisible()

  await page.getByLabel('Your name').fill('Alex Parent')
  await page.getByLabel('Phone (optional)').fill('0123 456')
  await page.getByRole('button', { name: 'Save details' }).click()
  await expect(page.getByText('Your details have been saved.')).toBeVisible()
  await page.getByLabel('When a book is due back soon').uncheck()
  await page.getByRole('button', { name: 'Save reminders' }).click()
  await expect(page.getByText('Your reminder choices have been saved.')).toBeVisible()

  // Saved on the server, not only on screen.
  await page.reload()
  await expect(page.getByLabel('Your name')).toHaveValue('Alex Parent')
  await expect(page.getByLabel('When a book is due back soon')).not.toBeChecked()

  await page.getByLabel('New email address').fill(newEmail)
  const emailCard = page.getByRole('heading', { level: 2, name: 'Login email' }).locator('..')
  await emailCard.getByLabel('Current password').fill(password)
  await page.getByRole('button', { name: 'Send confirmation link' }).click()
  await expect(page.getByText(`We sent a confirmation link to ${newEmail}.`)).toBeVisible()

  const text = await emailTo(request, newEmail, 'Confirm your new email for Curiouskids Club')
  const link = /https?:\/\/\S+\/confirm-email\?token=[A-Za-z0-9_-]+/.exec(text)?.[0]
  expect(link).toBeDefined()
  await page.goto(new URL(link!).pathname + new URL(link!).search)
  await expect(page.getByText('Your email address has been changed.')).toBeVisible()

  await page.getByRole('link', { name: 'Go to your account' }).click()
  await expect(page.getByText(`You log in with ${newEmail}.`)).toBeVisible()
  await emailTo(request, email, 'Your Curiouskids Club email address was changed')

  await page.getByRole('button', { name: 'Log out' }).click()
  await page.goto('/login')
  await page.getByLabel('Email address').fill(newEmail)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Log in' }).click()
  await expect(page).toHaveURL(/\/books/)
})
