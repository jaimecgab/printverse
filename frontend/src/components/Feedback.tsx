import { AlertTriangle, LoaderCircle, RotateCcw } from 'lucide-react'
import type { ReactNode } from 'react'

export function LoadingState({ label = 'Cargando información…' }: { label?: string }) {
  return (
    <div className="state-panel" role="status">
      <LoaderCircle className="spin" size={30} />
      <p>{label}</p>
    </div>
  )
}

export function ErrorState({ message, retry }: { message: string; retry?: () => void }) {
  return (
    <div className="state-panel state-panel--error" role="alert">
      <AlertTriangle size={30} />
      <div>
        <strong>No pudimos cargar esta sección</strong>
        <p>{message}</p>
      </div>
      {retry && (
        <button className="button button--secondary" onClick={retry}>
          <RotateCcw size={17} /> Reintentar
        </button>
      )}
    </div>
  )
}

export function EmptyState({ icon, title, description, action }: { icon: ReactNode; title: string; description: string; action?: ReactNode }) {
  return (
    <div className="empty-state">
      <span className="empty-icon">{icon}</span>
      <h2>{title}</h2>
      <p>{description}</p>
      {action}
    </div>
  )
}
