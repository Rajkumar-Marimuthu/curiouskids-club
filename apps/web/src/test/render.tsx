import { render } from '@testing-library/react'
import { createMemoryRouter, type RouteObject } from 'react-router'
import { App } from '../App'
import { createQueryClient } from '../lib/queryClient'
import { routes } from '../routes'

/** Renders the real app (providers, layouts, routes) at a path, with retries off. */
export function renderRoute(path: string, routeList: RouteObject[] = routes) {
  const queryClient = createQueryClient()
  queryClient.setDefaultOptions({ queries: { retry: false } })
  const router = createMemoryRouter(routeList, { initialEntries: [path] })
  return { ...render(<App router={router} queryClient={queryClient} />), router }
}
