import type { QuoteStatus } from '../types'

export const currency = new Intl.NumberFormat('es-MX', {
  style: 'currency',
  currency: 'MXN',
  minimumFractionDigits: 2,
})

export const number = new Intl.NumberFormat('es-MX', { maximumFractionDigits: 2 })

export function formatDate(value: string | null | undefined, withTime = false): string {
  if (!value) return 'Sin definir'
  const date = value.length === 10 ? new Date(`${value}T12:00:00`) : new Date(value)
  return new Intl.DateTimeFormat('es-MX', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    ...(withTime ? { hour: '2-digit', minute: '2-digit' } : {}),
  }).format(date)
}

export function toDateInput(date: Date): string {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 10)
}

export function addDays(days: number): string {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return toDateInput(date)
}

export const statusLabels: Record<QuoteStatus, string> = {
  DRAFT: 'Borrador',
  SENT: 'Enviada',
  ACCEPTED: 'Aceptada',
  REJECTED: 'Rechazada',
}

export function quoteValidity(validUntil: string): 'current' | 'soon' | 'expired' {
  const today = new Date(`${toDateInput(new Date())}T00:00:00`)
  const valid = new Date(`${validUntil}T00:00:00`)
  const days = Math.round((valid.getTime() - today.getTime()) / 86_400_000)
  if (days < 0) return 'expired'
  if (days <= 3) return 'soon'
  return 'current'
}
