import { createContext, useContext, useRef, useState, type ReactNode } from 'react'
import { CheckCircle2, X, XCircle } from 'lucide-react'

interface Toast {
  id: number
  message: string
  type: 'success' | 'error'
}

interface ToastContextValue {
  success: (message: string) => void
  error: (message: string) => void
}

const ToastContext = createContext<ToastContextValue | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const nextId = useRef(1)

  function add(message: string, type: Toast['type']) {
    const id = nextId.current++
    setToasts((current) => [...current, { id, message, type }])
    window.setTimeout(() => setToasts((current) => current.filter((toast) => toast.id !== id)), 4500)
  }

  function remove(id: number) {
    setToasts((current) => current.filter((toast) => toast.id !== id))
  }

  return (
    <ToastContext.Provider value={{ success: (message) => add(message, 'success'), error: (message) => add(message, 'error') }}>
      {children}
      <div className="toast-region" role="region" aria-label="Notificaciones" aria-live="polite">
        {toasts.map((toast) => (
          <div className={`toast toast--${toast.type}`} key={toast.id}>
            {toast.type === 'success' ? <CheckCircle2 size={19} /> : <XCircle size={19} />}
            <span>{toast.message}</span>
            <button className="icon-button" onClick={() => remove(toast.id)} aria-label="Cerrar notificación">
              <X size={17} />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

// The provider and its hook intentionally share this small module.
// eslint-disable-next-line react-refresh/only-export-components
export function useToast(): ToastContextValue {
  const context = useContext(ToastContext)
  if (!context) throw new Error('useToast debe usarse dentro de ToastProvider')
  return context
}
