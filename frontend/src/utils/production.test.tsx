// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ProductionItemStatusBadge, ProductionStatusBadge } from '../components/ProductionStatusBadge'
import type { Printer, ProductionOrder, ProductionOrderItem } from '../types'
import { assignablePrinters, availableItemTransitions, availableOrderTransitions, manuallyManagedPrinterStatuses, normalizedCompletedQuantity, openProductionOrderCount, orderProgress, productionOrderDueBucket } from './production'

function item(overrides: Partial<ProductionOrderItem> = {}): ProductionOrderItem {
  return {
    id: 1, quoteItemId: 2, name: 'Carcasa', quantity: 10, materialName: 'PLA', printerName: 'MK4', printerModel: null,
    weightGrams: 20, printTimeMinutes: 60, assignedPrinter: null, completedQuantity: 0, status: 'PENDING', notes: null, version: 0, ...overrides,
  }
}

function order(status: ProductionOrder['status'], items: ProductionOrderItem[]): Pick<ProductionOrder, 'status' | 'items'> {
  return { status, items }
}

describe('reglas de transición de producción', () => {
  it('no ofrece READY hasta que todas las piezas estén completas', () => {
    expect(availableOrderTransitions(order('IN_PRODUCTION', [item({ status: 'COMPLETED', completedQuantity: 10 }), item({ id: 2, status: 'IN_PROGRESS', completedQuantity: 4 })]))).toEqual(['CANCELLED'])
    expect(availableOrderTransitions(order('IN_PRODUCTION', [item({ status: 'COMPLETED', completedQuantity: 10 }), item({ id: 2, status: 'COMPLETED', completedQuantity: 10 })]))).toEqual(['READY', 'CANCELLED'])
  })

  it('cierra órdenes terminales y conserva el avance ponderado por unidades', () => {
    expect(availableOrderTransitions(order('DELIVERED', [item()]))).toEqual([])
    expect(availableOrderTransitions(order('CANCELLED', [item()]))).toEqual([])
    expect(orderProgress(order('IN_PRODUCTION', [item({ quantity: 8, completedQuantity: 4 }), item({ id: 2, quantity: 2, completedQuantity: 2 })]))).toBe(60)
  })

  it('no redondea a 100 mientras quede alguna unidad pendiente', () => {
    expect(orderProgress(order('IN_PRODUCTION', [item({ quantity: 200, completedQuantity: 199 })]))).toBe(99)
    expect(orderProgress(order('IN_PRODUCTION', [item({ quantity: 200, completedQuantity: 200 })]))).toBe(100)
  })

  it('considera vencimientos y carga únicamente para órdenes abiertas', () => {
    const today = new Date(2026, 7, 24)
    expect(productionOrderDueBucket({ status: 'PENDING', dueDate: '2026-08-23' }, today)).toBe('overdue')
    expect(productionOrderDueBucket({ status: 'DELIVERED', dueDate: '2026-08-23' }, today)).toBe('none')
    expect(productionOrderDueBucket({ status: 'CANCELLED', dueDate: '2026-08-25' }, today)).toBe('none')
    expect(openProductionOrderCount({ PENDING: 2, IN_PRODUCTION: 3, READY: 1, DELIVERED: 8, CANCELLED: 5 })).toBe(6)
  })

  it('normaliza cantidades al cambiar a estados que imponen límites backend', () => {
    expect(normalizedCompletedQuantity('PENDING', 8, 5)).toBe(0)
    expect(normalizedCompletedQuantity('COMPLETED', 8, 5)).toBe(8)
    expect(normalizedCompletedQuantity('BLOCKED', 8, 8)).toBe(7)
  })

  it('expone únicamente las transiciones permitidas por estado de pieza', () => {
    expect(availableItemTransitions('PENDING')).toEqual(['PENDING', 'IN_PROGRESS', 'BLOCKED'])
    expect(availableItemTransitions('IN_PROGRESS')).toEqual(['IN_PROGRESS', 'BLOCKED', 'COMPLETED'])
    expect(availableItemTransitions('BLOCKED')).toEqual(['BLOCKED', 'PENDING', 'IN_PROGRESS'])
    expect(availableItemTransitions('COMPLETED')).toEqual(['COMPLETED'])
  })
})

describe('etiquetas accesibles de producción', () => {
  it('presenta estados de orden y pieza con labels localizados y clases semánticas', () => {
    render(<><ProductionStatusBadge status="IN_PRODUCTION" /><ProductionItemStatusBadge status="BLOCKED" /></>)
    expect(screen.getByText('En producción')).toHaveClass('production-status--in_production')
    expect(screen.getByText('Bloqueada')).toHaveClass('production-item-status--blocked')
  })
})

describe('asignación de impresoras', () => {
  it('ofrece disponibles y ocupadas, y conserva una asignación actual no operativa', () => {
    const printer = (id: number, operationalStatus: Printer['operationalStatus'], active = true): Printer => ({
      id, name: `Printer ${id}`, model: null, costPerHour: 10, operationalStatus, notes: null,
      active, createdAt: '2026-08-24T00:00:00Z',
    })
    const printers = [printer(1, 'AVAILABLE'), printer(2, 'BUSY'), printer(3, 'MAINTENANCE'), printer(4, 'OUT_OF_SERVICE'), printer(5, 'AVAILABLE', false)]

    expect(assignablePrinters(printers).map(({ id }) => id)).toEqual([1, 2])
    expect(assignablePrinters(printers, 3).map(({ id }) => id)).toEqual([1, 2, 3])
    expect(manuallyManagedPrinterStatuses).toEqual(['AVAILABLE', 'MAINTENANCE', 'OUT_OF_SERVICE'])
  })
})
