import { useSyncExternalStore } from 'react'

export type ToastMessage = {
  id: number
  title: string
  description?: string
  tone: 'error' | 'info'
}

let nextId = 1
let toasts: ToastMessage[] = []
const listeners = new Set<() => void>()

function emit() {
  for (const listener of listeners) listener()
}

/** Shows a toast. Text must already be translated. */
export function showToast(toast: Omit<ToastMessage, 'id'>): number {
  const id = nextId++
  toasts = [...toasts, { ...toast, id }]
  emit()
  return id
}

export function dismissToast(id: number) {
  toasts = toasts.filter((t) => t.id !== id)
  emit()
}

export function useToasts(): ToastMessage[] {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => toasts,
  )
}

/** Snapshot of the visible toasts, for code outside React. */
export function currentToasts(): ToastMessage[] {
  return toasts
}
