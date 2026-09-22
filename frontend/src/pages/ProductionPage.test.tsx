// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { productionApi } from '../api'
import type { ProductionOrder } from '../types'
import { ProductionPage } from './ProductionPage'

vi.mock('../api', () => ({ productionApi: { list: vi.fn() } }))

const order: ProductionOrder = {
  id: 1, orderNumber: 'OP-1', quote: { id: 2, quoteNumber: 'PV-2', title: 'Carcasa', customer: { id: 3, name: 'Cliente', phone: '555-0100', email: null } },
  status: 'PENDING', priority: 'NORMAL', dueDate: null, notes: null, createdAt: '2026-08-24T00:00:00Z', updatedAt: '2026-08-24T00:00:00Z',
  startedAt: null, readyAt: null, deliveredAt: null, cancelledAt: null, items: [],
}

function renderPage() {
  return render(<MemoryRouter><ProductionPage /></MemoryRouter>)
}

describe('filtros de producción', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(cleanup)

  it('limpia todos los filtros sin resultados y vuelve a mostrar las órdenes', async () => {
    vi.mocked(productionApi.list).mockResolvedValue([order])
    renderPage()

    expect(await screen.findByText('OP-1')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /^Pendientes/ }))
    expect(screen.queryByRole('button', { name: 'Limpiar filtros' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /^Canceladas/ }))
    fireEvent.change(screen.getByLabelText('Prioridad'), { target: { value: 'URGENT' } })
    fireEvent.click(screen.getByRole('button', { name: 'Vencidas' }))
    fireEvent.change(screen.getByRole('textbox', { name: 'Buscar orden' }), { target: { value: 'sin coincidencias' } })
    fireEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }))

    expect(screen.getByText('OP-1')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^Todas/ })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByLabelText('Prioridad')).toHaveValue('ALL')
    expect(screen.getByRole('button', { name: 'Cualquier fecha' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('textbox', { name: 'Buscar orden' })).toHaveValue('')
    expect(screen.queryByRole('button', { name: 'Limpiar filtros' })).not.toBeInTheDocument()
  })

  it('no ofrece limpiar filtros cuando no existen órdenes', async () => {
    vi.mocked(productionApi.list).mockResolvedValue([])
    renderPage()

    expect(await screen.findByText('Producción está vacía')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /^Canceladas/ }))
    expect(screen.queryByRole('button', { name: 'Limpiar filtros' })).not.toBeInTheDocument()
  })
})
