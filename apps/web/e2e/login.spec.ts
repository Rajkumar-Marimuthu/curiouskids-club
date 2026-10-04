import { expect, test } from '@playwright/test'

// FR-ID-03 end to end: route guard, login with session cookie and CSRF token, logout.
test('a parent is sent to log in, comes back to the page, then logs out', async ({ page }) => {
  const email = `e2e-login-${Date.now()}@example.com`
  const password = 'purple giraffes read slowly'

  await page.goto('/register')
  await page.getByLabel('Your name').fill('Sam Parent')
  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByLabel('I am 18 or older').check()
  await page.getByLabel('I accept the terms and privacy notice').check()
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: 'Check your email' })).toBeVisible()

  await page.goto('/my/loans')
  await expect(page).toHaveURL(/\/login\?next=%2Fmy%2Floans$/)

  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password').fill('not my password')
  await page.getByRole('button', { name: 'Log in' }).click()
  await expect(page.getByRole('alert')).toContainText('Email or password is incorrect.')

  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Log in' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'My loans' })).toBeVisible()

  const cookies = await page.context().cookies()
  expect(cookies.find((c) => c.name === '__Host-SESSION')).toMatchObject({
    httpOnly: true,
    secure: true,
    sameSite: 'Lax',
  })

  // The session survives a reload.
  await page.reload()
  await expect(page.getByRole('heading', { level: 1, name: 'My loans' })).toBeVisible()

  await page.getByRole('button', { name: 'Log out' }).click()
  await expect(page.getByRole('link', { name: 'Log in' })).toBeVisible()
  await page.goto('/my/loans')
  await expect(page).toHaveURL(/\/login/)
})
