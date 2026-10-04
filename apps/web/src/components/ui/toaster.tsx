import { Toast } from 'radix-ui'
import { useTranslation } from 'react-i18next'
import { dismissToast, useToasts } from '../../lib/toast'
import { cn } from '../../lib/utils'

/** Renders toasts raised with showToast(); Radix announces them to screen readers. */
export function Toaster() {
  const toasts = useToasts()
  const { t } = useTranslation()

  return (
    <Toast.Provider swipeDirection="right" label={t('toast.close')}>
      {toasts.map((toast) => (
        <Toast.Root
          key={toast.id}
          type={toast.tone === 'error' ? 'foreground' : 'background'}
          onOpenChange={(open) => {
            if (!open) dismissToast(toast.id)
          }}
          className={cn(
            'flex items-start gap-3 rounded-card border p-4 shadow-lg',
            toast.tone === 'error'
              ? 'border-danger bg-danger-soft text-danger'
              : 'border-line bg-surface-raised text-ink',
          )}
        >
          <div className="flex-1">
            <Toast.Title className="font-semibold">{toast.title}</Toast.Title>
            {toast.description && (
              <Toast.Description className="mt-1 text-sm">{toast.description}</Toast.Description>
            )}
          </div>
          <Toast.Close
            aria-label={t('toast.close')}
            className="rounded-full px-2 text-lg leading-none"
          >
            <span aria-hidden="true">×</span>
          </Toast.Close>
        </Toast.Root>
      ))}
      <Toast.Viewport className="fixed right-0 bottom-0 z-50 flex w-full max-w-sm flex-col gap-2 p-4" />
    </Toast.Provider>
  )
}
