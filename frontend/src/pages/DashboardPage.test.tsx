// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { dashboardApi } from '../api'
import type { Dashboard } from '../types'
import { DashboardPage } from './DashboardPage'

vi.mock('../api', () => ({ dashboardApi: { get: vi.fn() } }))

const dashboard: Dashboard = {
  quoteCounts: { DRAFT: 2, SENT: 3, ACCEPTED: 4, REJECTED: 1 },
  sentPipelineAmount: 1200,
  acceptedRevenue: 800,
  acceptedEstimatedProfit: 240,
  acceptanceRate: 0.8,
  productionCounts: { PENDING: 1, IN_PRODUCTION: 2, READY: 1, DELIVERED: 3, CANCELLED: 0 },
  lowStockMaterials: [],
  printerCounts: { AVAILABLE: 2, BUSY: 1, MAINTENANCE: 0, OUT_OF_SERVICE: 0 },
  dueSoonOrders: [],
  overdueOrders: [],
  recentQuotes: [],
  updatedAt: '2026-09-22T12:00:00Z',
}

describe('dashboard', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(cleanup)

  it('muestra el loading aprobado mientras obtiene el resumen', () => {
    vi.mocked(dashboardApi.get).mockReturnValue(new Promise(() => undefined))
    render(<DashboardPage />)

    expect(screen.getByText('Cargando resumen de operaciones…')).toBeInTheDocument()
  })

  it('actualiza el encabezado y conserva los datos y enlaces operativos', async () => {
    vi.mocked(dashboardApi.get).mockResolvedValue(dashboard)
    render(<MemoryRouter><DashboardPage /></MemoryRouter>)

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(screen.getByText(/Resumen general de operaciones\. Actualizado .+\./)).toBeInTheDocument()
    expect(screen.queryByText('Control de operación')).not.toBeInTheDocument()
    expect(screen.queryByText('01')).not.toBeInTheDocument()
    expect(screen.getByText('Inventario')).toBeInTheDocument()
    expect(screen.getAllByText('Impresoras')).toHaveLength(2)
    expect(screen.getByText('Producción')).toBeInTheDocument()
    expect(screen.getByText('Cotizaciones', { selector: '.eyebrow' })).toBeInTheDocument()
    expect(screen.getByText('Vencimientos')).toBeInTheDocument()
    expect(screen.getByText('Sin vencimientos próximos')).toBeInTheDocument()
    expect(screen.getByText('3 cotizaciones esperando decisión')).toBeInTheDocument()
    expect(screen.getByText('80%')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Nueva cotización' })).toHaveAttribute('href', '/quotes/new')
  })
})
