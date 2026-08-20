import { cloneElement, type ReactElement } from 'react'

interface FieldControlProps {
  'aria-describedby'?: string
  'aria-invalid'?: boolean
}

export function FormField({ label, htmlFor, error, hint, children }: { label: string; htmlFor: string; error?: string; hint?: string; children: ReactElement<FieldControlProps> }) {
  const descriptionId = error || hint ? `${htmlFor}-description` : undefined
  const control = cloneElement(children, {
    'aria-describedby': descriptionId,
    'aria-invalid': Boolean(error),
  })

  return (
    <div className={`form-field ${error ? 'has-error' : ''}`}>
      <label htmlFor={htmlFor}>{label}</label>
      {control}
      {error ? <small id={descriptionId} className="field-error">{error}</small> : hint ? <small id={descriptionId} className="field-hint">{hint}</small> : null}
    </div>
  )
}
