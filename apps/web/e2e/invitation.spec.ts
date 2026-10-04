import { expect, test, type APIRequestContext, type Page } from '@playwright/test'
import { emailTo } from './mailpit.ts'

// The local profile invites this address as the first admin at start-up (T-014).
const ADMIN_EMAIL = 'admin@curiouskids.local'
const ADMIN_PASSWORD = 'e2e admin password, local only'
const PASSWORD = 'orange penguins dance quietly'

async function logIn(page: Page, email: string, password: string) {
  await page.goto('/login')
  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Log in' }).click()
}

/** Follows an invitation link from the email and chooses a password. */
async function acceptInvitation(page: Page, text: string, password: string) {
  const link = /https?:\/\/\S+\/accept-invitation\?token=[\w-]+/.exec(text)?.[0]
  expect(link).toBeDefined()
  await page.goto(new URL(link!).pathname + new URL(link!).search)
  await page.getByLabel('Choose a password').fill(password)
  await page.getByRole('button', { name: 'Create my account' }).click()
  await expect(page.getByText('Your account is ready.')).toBeVisible()
}

/** Logs in as the first admin, accepting the start-up invitation the first time. */
async function logInAsFirstAdmin(page: Page, request: APIRequestContext) {
  await logIn(page, ADMIN_EMAIL, ADMIN_PASSWORD)
  const outcome = await Promise.race([
    page.waitForURL(/\/admin\//).then(() => 'in' as const),
    page
      .getByRole('alert')
      .waitFor()
      .then(() => 'refused' as const),
  ])
  if (outcome === 'in') return
  const text = await emailTo(request, ADMIN_EMAIL, "You're invited to help run Curiouskids Club")
  await acceptInvitation(page, text, ADMIN_PASSWORD)
  await logIn(page, ADMIN_EMAIL, ADMIN_PASSWORD)
  await expect(page).toHaveURL(/\/admin\//)
}

// FR-ID-07 end to end: the first admin invites a volunteer, who creates their account from the
// emailed link and logs in to the volunteer desk.
test('an admin invites a volunteer who then logs in', async ({ page, request }) => {
  const volunteer = `e2e-volunteer-${Date.now()}@example.com`

  await logInAsFirstAdmin(page, request)
  await page.goto('/admin/users')
  await page.getByLabel('Email address').fill(volunteer)
  await page.getByLabel('Role').selectOption('VOLUNTEER')
  await page.getByRole('button', { name: 'Send invitation' }).click()
  await expect(page.getByText(`Invitation sent to ${volunteer}.`)).toBeVisible()
  await page.getByRole('button', { name: 'Log out' }).click()

  const text = await emailTo(request, volunteer, "You're invited to help run Curiouskids Club")
  await acceptInvitation(page, text, PASSWORD)
  await page.getByLabel('Email address').fill(volunteer)
  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Log in' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Pick list' })).toBeVisible()

  // Volunteers cannot reach admin screens.
  await page.goto('/admin/users')
  await expect(
    page.getByRole('heading', { level: 1, name: "You can't open this page" }),
  ).toBeVisible()
})
