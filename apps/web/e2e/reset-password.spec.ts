import { expect, test } from '@playwright/test'
import { emailTo } from './mailpit.ts'

// FR-ID-04 end to end: request a reset, follow the emailed link, set a new password; the old
// session ends and only the new password works.
test('a parent resets a forgotten password', async ({ page, browser, request }) => {
  const email = `e2e-reset-${Date.now()}@example.com`
  const oldPassword = 'purple giraffes read slowly'
  const newPassword = 'orange penguins dance quietly'

  await page.goto('/register')
  await page.getByLabel('Your name').fill('Sam Parent')
  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password', { exact: true }).fill(oldPassword)
  await page.getByLabel('I am 18 or older').check()
  await page.getByLabel('I accept the terms and privacy notice').check()
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: 'Check your email' })).toBeVisible()

  // Logged in on another device before the reset.
  const other = await browser.newContext()
  const otherPage = await other.newPage()
  await otherPage.goto('/login?next=%2Fmy%2Floans')
  await otherPage.getByLabel('Email address').fill(email)
  await otherPage.getByLabel('Password').fill(oldPassword)
  await otherPage.getByRole('button', { name: 'Log in' }).click()
  await expect(otherPage.getByRole('heading', { level: 1, name: 'My loans' })).toBeVisible()

  await page.goto('/login')
  await page.getByRole('link', { name: 'Forgot your password?' }).click()
  // Wait for the page to change: the login page has an "Email address" field too.
  await expect(page.getByRole('heading', { level: 1, name: 'Reset your password' })).toBeVisible()
  await page.getByLabel('Email address').fill(email)
  await page.getByRole('button', { name: 'Send reset link' }).click()
  await expect(page.getByRole('status')).toContainText("If that email has an account, we've sent")

  const text = await emailTo(request, email, 'Reset your Curiouskids Club password')
  const link = /https?:\/\/\S+\/reset-password\?token=[\w-]+/.exec(text)?.[0]
  expect(link).toBeDefined()
  await page.goto(new URL(link!).pathname + new URL(link!).search)
  await page.getByLabel('New password').fill(newPassword)
  await page.getByRole('button', { name: 'Save new password' }).click()

  await expect(page.getByRole('status')).toContainText('Your password has been changed')
  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password').fill(oldPassword)
  await page.getByRole('button', { name: 'Log in' }).click()
  await expect(page.getByRole('alert')).toContainText('Email or password is incorrect.')
  await page.getByLabel('Password').fill(newPassword)
  await page.getByRole('button', { name: 'Log in' }).click()
  await expect(page).toHaveURL(/\/books$/)

  // The other device was logged out by the reset.
  await otherPage.goto('/my/loans')
  await expect(otherPage).toHaveURL(/\/login/)
  await other.close()
})
