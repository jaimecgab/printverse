// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { printersApi, productionApi } from '../api'
import { ApiError } from '../api/http'
import { ToastProvider } from '../context/ToastContext'
import type { Printer, ProductionOrder, ProductionOrderItem } from '../types'
import { ProductionOrderPage } from './ProductionOrderPage'

vi.mock('../api', () => ({
  printersApi: { list: vi.fn() },
  productionApi: { get: vi.fn(), setStatus: vi.fn(), updateItem: vi.fn() },
}))

function printer(id: number, operationalStatus: Printer['operationalStatus'], active = true): Printer {
  return { id, name: `Printer ${id}`, model: null, costPerHour: 10, operationalStatus, notes: null, active, createdAt: '2026-08-24T00:00:00Z' }
}

function item(overrides: Partial<ProductionOrderItem> = {}): ProductionOrderItem {
  return {
    id: 10, quoteItemId: 20, name: 'Carcasa', quantity: 4, materialName: 'PLA', printerName: 'Referencia', printerModel: null,
    weightGrams: 20, printTimeMinutes: 60, assignedPrinter: null, completedQuantity: 0, status: 'PENDING', notes: null, version: 0, ...overrides,
  }
}

function order(items: ProductionOrderItem[], status: ProductionOrder['status'] = 'IN_PRODUCTION'): ProductionOrder {
  return {
    id: 1, orderNumber: 'OP-1', quote: { id: 2, quoteNumber: 'PV-2', title: null, customer: { id: 3, name: 'Cliente', phone: '555-0100', email: null } },
    status, priority: 'NORMAL', dueDate: null, notes: null, createdAt: '2026-08-24T00:00:00Z', updatedAt: '2026-08-24T00:00:00Z',
    startedAt: status === 'IN_PRODUCTION' ? '2026-08-24T00:30:00Z' : null, readyAt: null, deliveredAt: null, cancelledAt: null, items,
  }
}

function renderPage() {
  return render(<MemoryRouter initialEntries={['/production/1']}><ToastProvider><Routes><Route path="/production/:id" element={<ProductionOrderPage />} /></Routes></ToastProvider></MemoryRouter>)
}

describe('editor de piezas de producción', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(cleanup)

  it('refresca disponibilidad, exige impresora para iniciar y no revierte una actualización si falla el refresco posterior', async () => {
    const available = printer(1, 'AVAILABLE')
    const occupied = printer(2, 'BUSY')
    const maintenance = printer(3, 'MAINTENANCE')
    const current = order([item()])
    const updated = order([item({ status: 'IN_PROGRESS', assignedPrinter: { id: 1, name: 'Printer 1', model: null } })])
    vi.mocked(productionApi.get).mockResolvedValue(current)
    vi.mocked(productionApi.updateItem).mockResolvedValue(updated)
    vi.mocked(printersApi.list)
      .mockResolvedValueOnce([available])
      .mockResolvedValueOnce([available, occupied, maintenance])
      .mockRejectedValueOnce(new Error('refresh failed'))

    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Editar pieza' }))

    const printerSelect = await screen.findByLabelText('Impresora asignada')
    expect(printersApi.list).toHaveBeenCalledTimes(2)
    expect(within(printerSelect).getByRole('option', { name: 'Printer 1 · Disponible' })).toBeInTheDocument()
    expect(within(printerSelect).getByRole('option', { name: 'Printer 2 · Ocupada' })).toBeInTheDocument()
    expect(within(printerSelect).queryByText(/Printer 3/)).not.toBeInTheDocument()

    const statusSelect = screen.getByLabelText('Estado')
    expect(within(statusSelect).getAllByRole('option').map((option) => option.textContent)).toEqual(['Pendiente', 'En proceso', 'Bloqueada'])
    fireEvent.change(statusSelect, { target: { value: 'IN_PROGRESS' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar avance' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('La pieza necesita una impresora')
    expect(productionApi.updateItem).not.toHaveBeenCalled()

    fireEvent.change(printerSelect, { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar avance' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Una impresora ocupada puede quedar asignada en cola')
    expect(productionApi.updateItem).not.toHaveBeenCalled()

    fireEvent.change(printerSelect, { target: { value: '1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar avance' }))

    await waitFor(() => expect(productionApi.updateItem).toHaveBeenCalledWith(1, 10, {
      assignedPrinterId: 1, status: 'IN_PROGRESS', completedQuantity: 0, notes: '', expectedVersion: 0,
    }))
    expect(await screen.findByText('La pieza se actualizó, pero no fue posible refrescar la disponibilidad de impresoras.')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByText('En proceso')).toBeInTheDocument()
  })

  it('bloquea el cambio de impresora en proceso y deja las piezas completas en solo lectura', async () => {
    const assigned = printer(1, 'BUSY')
    vi.mocked(productionApi.get).mockResolvedValue(order([
      item({ status: 'IN_PROGRESS', assignedPrinter: { id: 1, name: 'Printer 1', model: null }, completedQuantity: 1 }),
      item({ id: 11, name: 'Terminada', status: 'COMPLETED', completedQuantity: 4 }),
    ]))
    vi.mocked(printersApi.list).mockResolvedValue([assigned, printer(2, 'AVAILABLE')])

    renderPage()
    expect(await screen.findByRole('button', { name: 'Pieza completa' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Editar pieza' }))

    expect(await screen.findByLabelText('Impresora asignada')).toBeDisabled()
    expect(within(screen.getByLabelText('Estado')).getAllByRole('option').map((option) => option.textContent)).toEqual(['En proceso', 'Bloqueada', 'Completa'])
    expect(screen.getByText('Bloquea la pieza y guarda antes de cambiar de impresora.')).toBeInTheDocument()
  })

  it('no ofrece iniciar una pieza mientras la orden sigue pendiente', async () => {
    vi.mocked(productionApi.get).mockResolvedValue(order([item()], 'PENDING'))
    vi.mocked(printersApi.list).mockResolvedValue([printer(1, 'AVAILABLE')])

    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Editar pieza' }))

    expect(within(await screen.findByLabelText('Estado')).getAllByRole('option').map((option) => option.textContent)).toEqual(['Pendiente', 'Bloqueada'])
  })

  it('recarga la orden y las impresoras cuando la pieza cambió en otra sesión', async () => {
    const current = order([item()])
    const refreshed = order([item({ status: 'BLOCKED', version: 1, notes: 'Cambio concurrente' })])
    vi.mocked(productionApi.get)
      .mockResolvedValueOnce(current)
      .mockResolvedValueOnce(current)
      .mockResolvedValueOnce(refreshed)
    vi.mocked(productionApi.updateItem).mockRejectedValue(new ApiError(409, {
      detail: 'Production order item changed after it was loaded; reload and retry',
    }))
    vi.mocked(printersApi.list).mockResolvedValue([printer(1, 'AVAILABLE')])

    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Editar pieza' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Guardar avance' }))

    await waitFor(() => expect(productionApi.get).toHaveBeenCalledTimes(3))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByText('Cambio concurrente')).toBeInTheDocument()
    expect(screen.getByText('La pieza cambió en otra sesión. Actualiza la orden antes de intentarlo de nuevo.')).toBeInTheDocument()
    expect(printersApi.list).toHaveBeenCalledTimes(3)
  })

  it('conserva el editor cuando otra pieza ocupa la impresora seleccionada', async () => {
    vi.mocked(productionApi.get).mockResolvedValue(order([item()]))
    vi.mocked(productionApi.updateItem).mockRejectedValue(new ApiError(409, {
      detail: 'Printer 1 is already occupied',
    }))
    vi.mocked(printersApi.list)
      .mockResolvedValueOnce([printer(1, 'AVAILABLE')])
      .mockResolvedValueOnce([printer(1, 'AVAILABLE')])
      .mockResolvedValueOnce([printer(1, 'BUSY')])

    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Editar pieza' }))
    fireEvent.change(await screen.findByLabelText('Impresora asignada'), { target: { value: '1' } })
    fireEvent.change(screen.getByLabelText('Estado'), { target: { value: 'IN_PROGRESS' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar avance' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('La impresora ya está ocupada por otra pieza en proceso.')
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(productionApi.get).toHaveBeenCalledTimes(3)
    expect(within(screen.getByLabelText('Impresora asignada')).getByRole('option', { name: 'Printer 1 · Ocupada' })).toBeInTheDocument()
  })

  it('recarga impresoras no disponibles y conserva el formulario para corregirlo', async () => {
    vi.mocked(productionApi.get).mockResolvedValue(order([item()]))
    vi.mocked(productionApi.updateItem).mockRejectedValue(new ApiError(400, {
      detail: 'Printer 1 is MAINTENANCE and cannot start production',
    }))
    vi.mocked(printersApi.list)
      .mockResolvedValueOnce([printer(1, 'AVAILABLE')])
      .mockResolvedValueOnce([printer(1, 'AVAILABLE')])
      .mockResolvedValueOnce([printer(1, 'MAINTENANCE'), printer(2, 'AVAILABLE')])

    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Editar pieza' }))
    fireEvent.change(await screen.findByLabelText('Impresora asignada'), { target: { value: '1' } })
    fireEvent.change(screen.getByLabelText('Estado'), { target: { value: 'IN_PROGRESS' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar avance' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('La impresora ya no está disponible')
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(within(screen.getByLabelText('Impresora asignada')).queryByText(/Printer 1/)).not.toBeInTheDocument()
    expect(within(screen.getByLabelText('Impresora asignada')).getByText(/Printer 2/)).toBeInTheDocument()
  })

  it('cierra el editor y actualiza la página si la orden se cerró en otra sesión', async () => {
    const current = order([item()])
    const ready = order([item({ status: 'COMPLETED', completedQuantity: 4, version: 1 })], 'READY')
    vi.mocked(productionApi.get)
      .mockResolvedValueOnce(current)
      .mockResolvedValueOnce(current)
      .mockResolvedValueOnce(ready)
    vi.mocked(productionApi.updateItem).mockRejectedValue(new ApiError(400, {
      detail: 'Items cannot be modified when the production order is READY',
    }))
    vi.mocked(printersApi.list).mockResolvedValue([printer(1, 'AVAILABLE')])

    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Editar pieza' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Guardar avance' }))

    await waitFor(() => expect(productionApi.get).toHaveBeenCalledTimes(3))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByText('La orden está cerrada para edición de piezas.')).toBeInTheDocument()
  })

  it('sale del estado obsoleto si falla la recarga posterior a un conflicto de versión', async () => {
    vi.mocked(productionApi.get)
      .mockResolvedValueOnce(order([item()]))
      .mockResolvedValueOnce(order([item()]))
      .mockRejectedValueOnce(new Error('refresh failed'))
    vi.mocked(productionApi.updateItem).mockRejectedValue(new ApiError(409, {
      detail: 'Production order item changed after it was loaded; reload and retry',
    }))
    vi.mocked(printersApi.list).mockResolvedValue([printer(1, 'AVAILABLE')])

    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Editar pieza' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Guardar avance' }))

    expect(await screen.findByText('La pieza cambió en otra sesión y no fue posible recargar la orden.')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })
})
