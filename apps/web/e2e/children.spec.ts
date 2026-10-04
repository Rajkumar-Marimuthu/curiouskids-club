import { expect, test } from '@playwright/test'

// FR-ID-05 end to end: a member adds, changes and removes a child profile.
test('a parent manages their children', async ({ page }) => {
  const email = `e2e-children-${Date.now()}@example.com`
  const password = 'purple giraffes read slowly'

  await page.goto('/register')
  await page.getByLabel('Your name').fill('Sam Parent')
  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByLabel('I am 18 or older').check()
  await page.getByLabel('I accept the terms and privacy notice').check()
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: 'Check your email' })).toBeVisible()

  await page.goto('/account/children')
  await expect(page).toHaveURL(/\/login/)
  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Log in' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Your children' })).toBeVisible()

  await page.getByLabel('First name or nickname').fill('Ada')
  await page.getByLabel('Age band').selectOption('6-8')
  await page.getByRole('button', { name: 'Add child' }).click()
  await expect(page.getByText('Ada has been added.')).toBeVisible()

  await page.getByRole('button', { name: 'Edit Ada' }).click()
  const form = page.getByRole('form', { name: 'Edit Ada' })
  await form.getByLabel('Age band').selectOption('9-12')
  await form.getByRole('button', { name: 'Save' }).click()
  await expect(page.getByText('Age 9 to 12')).toBeVisible()

  // Saved on the server, not only on screen.
  await page.reload()
  await expect(page.getByText('Age 9 to 12')).toBeVisible()

  await page.getByRole('button', { name: 'Remove Ada' }).click()
  await page.getByRole('button', { name: 'Yes, remove' }).click()
  await expect(page.getByText("You haven't added any children yet.")).toBeVisible()
})
