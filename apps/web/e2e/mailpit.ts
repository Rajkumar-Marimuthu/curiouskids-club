import { expect, type APIRequestContext } from '@playwright/test'

// Mailpit's web API (testing-strategy.md); the local stack and CI both run it.
const MAILPIT = process.env.MAILPIT_URL ?? 'http://localhost:8025'

type MailpitList = { messages: { ID: string; Subject: string; To: { Address: string }[] }[] }

/**
 * Waits for the outbox job to deliver an email to this address (with this subject, if given), then
 * returns its text body.
 */
export async function emailTo(
  request: APIRequestContext,
  address: string,
  subject?: string,
): Promise<string> {
  let id: string | undefined
  await expect
    .poll(
      async () => {
        const list = (await (
          await request.get(`${MAILPIT}/api/v1/messages?limit=200`)
        ).json()) as MailpitList
        id = list.messages.find(
          (m) =>
            m.To.some((to) => to.Address === address) &&
            (subject === undefined || m.Subject === subject),
        )?.ID
        return id
      },
      { timeout: 60_000, intervals: [1_000] },
    )
    .toBeTruthy()
  const message = (await (await request.get(`${MAILPIT}/api/v1/message/${id}`)).json()) as {
    Text: string
  }
  return message.Text
}
