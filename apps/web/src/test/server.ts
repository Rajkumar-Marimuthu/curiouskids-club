import { setupServer } from 'msw/node'
import { handlers } from './handlers'

/** Mock API for component tests. Override per test with server.use(...). */
export const server = setupServer(...handlers)
