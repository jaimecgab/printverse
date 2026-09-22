import { X } from 'lucide-react'
import { useEffect, useId, useRef, type ReactNode } from 'react'

export function Modal({ title, description, onClose, children, wide = false }: { title: string; description?: string; onClose: () => void; children: ReactNode; wide?: boolean }) {
  const modalRef = useRef<HTMLElement>(null)
  const previousFocusRef = useRef(document.activeElement instanceof HTMLElement ? document.activeElement : null)
  const onCloseRef = useRef(onClose)
  const descriptionId = useId()
  onCloseRef.current = onClose

  useEffect(() => {
    const modal = modalRef.current
    const previousFocus = previousFocusRef.current

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onCloseRef.current()
        return
      }
      if (event.key !== 'Tab' || !modal) return
      const focusable = Array.from(modal.querySelectorAll<HTMLElement>('button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href]'))
      if (!focusable.length) return
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    document.body.classList.add('modal-open')
    const firstFocusable = modal?.querySelector<HTMLElement>('.modal-body button:not([disabled]), .modal-body input:not([disabled]), .modal-body select:not([disabled]), .modal-body textarea:not([disabled]), .modal-body a[href]')
      || modal?.querySelector<HTMLElement>('button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href]')
    if (modal && !modal.contains(document.activeElement)) firstFocusable?.focus()
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.body.classList.remove('modal-open')
      if (!modal?.isConnected) previousFocus?.focus()
    }
  }, [])

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section ref={modalRef} className={`modal ${wide ? 'modal--wide' : ''}`} role="dialog" aria-modal="true" aria-labelledby="modal-title" aria-describedby={description ? descriptionId : undefined}>
        <header className="modal-header">
          <div>
            <p className="eyebrow">Editor</p>
            <h2 id="modal-title">{title}</h2>
            {description && <p id={descriptionId}>{description}</p>}
          </div>
          <button className="icon-button" onClick={onClose} aria-label="Cerrar ventana">
            <X />
          </button>
        </header>
        <div className="modal-body">{children}</div>
      </section>
    </div>
  )
}
