import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import type { components } from '../api/schema'
import { apiClient, ApiError, unwrap } from './api'

export type Me = components['schemas']['MeResponse']
export type Role = Me['role']

const ME_KEY = ['me'] as const

/** Where each role lands after logging in when no page was asked for. */
export const homeFor: Record<Role, string> = {
  MEMBER: '/books',
  VOLUNTEER: '/staff/pick-list',
  ADMIN: '/admin/windows',
}

/** The logged-in account, or `null` when nobody is logged in (FR-ID-03). */
export function useMe() {
  return useQuery<Me | null, ApiError>({
    queryKey: ME_KEY,
    queryFn: async () => {
      try {
        return await unwrap(apiClient.GET('/api/v1/auth/me'))
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null
        throw error
      }
    },
    staleTime: 5 * 60_000,
  })
}

export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: components['schemas']['LoginRequest']) =>
      unwrap(apiClient.POST('/api/v1/auth/login', { body })),
    onSuccess: (me) => {
      forgetCachedData(queryClient)
      queryClient.setQueryData(ME_KEY, me)
    },
  })
}

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => unwrap(apiClient.POST('/api/v1/auth/logout')),
    onSuccess: () => {
      forgetCachedData(queryClient)
      queryClient.setQueryData(ME_KEY, null)
    },
  })
}

/**
 * Nothing cached for the previous visitor survives a login or logout. The `me` query is kept (and
 * then set) so the components watching it update.
 */
function forgetCachedData(queryClient: QueryClient) {
  queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== ME_KEY[0] })
}

/** A `next` path from the login link, only if it stays inside this app. */
export function safeNext(next: string | null): string | undefined {
  return next && next.startsWith('/') && !next.startsWith('//') && !next.startsWith('/\\')
    ? next
    : undefined
}
