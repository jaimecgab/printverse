import type { Printer, PrinterOperationalStatus, ProductionItemStatus, ProductionOrder, ProductionOrderStatus, ProductionPriority } from '../types'

export const productionStatusLabels: Record<ProductionOrderStatus, string> = {
  PENDING: 'Pendiente', IN_PRODUCTION: 'En producción', READY: 'Lista', DELIVERED: 'Entregada', CANCELLED: 'Cancelada',
}

export const productionItemStatusLabels: Record<ProductionItemStatus, string> = {
  PENDING: 'Pendiente', IN_PROGRESS: 'En proceso', COMPLETED: 'Completa', BLOCKED: 'Bloqueada',
}

export const productionPriorityLabels: Record<ProductionPriority, string> = {
  LOW: 'Baja', NORMAL: 'Normal', HIGH: 'Alta', URGENT: 'Urgente',
}

export const printerOperationalStatusLabels: Record<PrinterOperationalStatus, string> = {
  AVAILABLE: 'Disponible', BUSY: 'Ocupada', MAINTENANCE: 'Mantenimiento', OUT_OF_SERVICE: 'Fuera de servicio',
}

export const manuallyManagedPrinterStatuses: PrinterOperationalStatus[] = ['AVAILABLE', 'MAINTENANCE', 'OUT_OF_SERVICE']

const productionItemTransitions: Record<ProductionItemStatus, ProductionItemStatus[]> = {
  PENDING: ['PENDING', 'IN_PROGRESS', 'BLOCKED'],
  IN_PROGRESS: ['IN_PROGRESS', 'BLOCKED', 'COMPLETED'],
  BLOCKED: ['BLOCKED', 'PENDING', 'IN_PROGRESS'],
  COMPLETED: ['COMPLETED'],
}

export function availableItemTransitions(status: ProductionItemStatus): ProductionItemStatus[] {
  return productionItemTransitions[status]
}

export function assignablePrinters(printers: Printer[], currentPrinterId?: number): Printer[] {
  return printers.filter((printer) => printer.id === currentPrinterId
    || printer.active && (printer.operationalStatus === 'AVAILABLE' || printer.operationalStatus === 'BUSY'))
}

export function orderProgress(order: Pick<ProductionOrder, 'items'>): number {
  const total = order.items.reduce((sum, item) => sum + item.quantity, 0)
  if (!total) return 0
  const completed = order.items.reduce((sum, item) => sum + item.completedQuantity, 0)
  return completed >= total ? 100 : Math.floor(completed / total * 100)
}

export function isOpenProductionOrder(status: ProductionOrderStatus): boolean {
  return status !== 'DELIVERED' && status !== 'CANCELLED'
}

export function openProductionOrderCount(counts: Record<ProductionOrderStatus, number>): number {
  return counts.PENDING + counts.IN_PRODUCTION + counts.READY
}

export function availableOrderTransitions(order: Pick<ProductionOrder, 'status' | 'items'>): ProductionOrderStatus[] {
  if (order.status === 'PENDING') return ['IN_PRODUCTION', 'CANCELLED']
  if (order.status === 'IN_PRODUCTION') {
    const allComplete = order.items.length > 0 && order.items.every((item) => item.status === 'COMPLETED')
    return allComplete ? ['READY', 'CANCELLED'] : ['CANCELLED']
  }
  if (order.status === 'READY') return ['DELIVERED', 'CANCELLED']
  return []
}

export function normalizedCompletedQuantity(status: ProductionItemStatus, quantity: number, current: number): number {
  if (status === 'PENDING') return 0
  if (status === 'COMPLETED') return quantity
  return Math.min(current, Math.max(0, quantity - 1))
}

export function dueBucket(dueDate: string | null | undefined, today = new Date()): 'overdue' | 'soon' | 'later' | 'none' {
  if (!dueDate) return 'none'
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const due = new Date(`${dueDate}T00:00:00`)
  const days = Math.round((due.getTime() - start.getTime()) / 86_400_000)
  if (days < 0) return 'overdue'
  return days <= 7 ? 'soon' : 'later'
}

export function productionOrderDueBucket(order: Pick<ProductionOrder, 'status' | 'dueDate'>, today = new Date()): ReturnType<typeof dueBucket> {
  return isOpenProductionOrder(order.status) ? dueBucket(order.dueDate, today) : 'none'
}
