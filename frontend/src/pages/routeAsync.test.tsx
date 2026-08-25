// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Link, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { customersApi, printersApi, productionApi, quotesApi } from '../api'
import { ToastProvider } from '../context/ToastContext'
import type { CustomerOverview, ProductionOrder, Quote } from '../types'
import { CustomerDetailPage } from './CustomerDetailPage'
import { ProductionOrderPage } from './ProductionOrderPage'
import { QuoteDetailPage } from './QuoteDetailPage'

vi.mock('../api', () => ({
  customersApi: { overview: vi.fn() },
  printersApi: { list: vi.fn() },
  productionApi: { get: vi.fn(), getByQuote: vi.fn() },
  quotesApi: { get: vi.fn() },
}))

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => { resolve = done })
  return { promise, resolve }
}

function customerOverview(id: number): CustomerOverview {
  return {
    customer: { id, name: `Cliente ${id}`, phone: '555-0100', email: null, notes: null, createdAt: '2026-08-24T00:00:00Z' },
    quoteCounts: { DRAFT: 0, SENT: 0, ACCEPTED: 0, REJECTED: 0 },
    totalAcceptedRevenue: 0, totalEstimatedProfit: 0, recentQuotes: [], productionOrderCount: 0,
  }
}

function quote(id: number): Quote {
  return {
    id, quoteNumber: `PV-${id}`, title: null,
    customer: { id, name: `Cliente ${id}`, phone: '555-0100', email: null },
    status: 'ACCEPTED', createdAt: '2026-08-24T00:00:00Z', updatedAt: '2026-08-24T00:00:00Z',
    validUntil: '2026-09-01', currencyCode: 'MXN', total: 116, estimatedProfit: 20, realMarginPercentage: 20,
    sentAt: '2026-08-24T00:00:00Z', acceptedAt: '2026-08-24T01:00:00Z', rejectedAt: null,
    estimatedDeliveryDate: null, depositPercentage: null, notes: null, internalNotes: null,
    markupPercentage: 30, discountPercentage: 0, taxEnabled: true, taxPercentage: 16,
    internalCost: 80, suggestedSubtotal: 100, finalSubtotal: 100, discountAmount: 0,
    subtotalAfterDiscount: 100, taxAmount: 16, depositAmount: 0, remainingBalance: 116, items: [],
  }
}

function productionOrder(id: number, quoteId = id): ProductionOrder {
  return {
    id, orderNumber: `OP-${id}`,
    quote: { id: quoteId, quoteNumber: `PV-${quoteId}`, title: null, customer: { id: quoteId, name: `Cliente ${quoteId}`, phone: '555-0100', email: null } },
    status: 'PENDING', priority: 'NORMAL', dueDate: null, notes: null,
    createdAt: '2026-08-24T00:00:00Z', updatedAt: '2026-08-24T00:00:00Z',
    startedAt: null, readyAt: null, deliveredAt: null, cancelledAt: null, items: [],
  }
}

function renderRoute(initial: string, next: string, pattern: string, element: React.ReactNode) {
  return render(
    <MemoryRouter initialEntries={[initial]}>
      <ToastProvider>
        <Link to={next}>Cambiar ruta</Link>
        <Routes><Route path={pattern} element={element} /></Routes>
      </ToastProvider>
    </MemoryRouter>,
  )
}

describe('cargas al cambiar identificadores de ruta', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(cleanup)

  it('mantiene un estado de carga al cambiar de cliente', async () => {
    const second = deferred<CustomerOverview>()
    vi.mocked(customersApi.overview).mockImplementation((id) => id === 1 ? Promise.resolve(customerOverview(1)) : second.promise)
    renderRoute('/customers/1', '/customers/2', '/customers/:id', <CustomerDetailPage />)
    expect(await screen.findByRole('heading', { name: 'Cliente 1' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('link', { name: 'Cambiar ruta' }))

    expect(await screen.findByText('Reuniendo actividad del cliente…')).toBeInTheDocument()
    await act(async () => second.resolve(customerOverview(2)))
    expect(await screen.findByRole('heading', { name: 'Cliente 2' })).toBeInTheDocument()
  })

  it('mantiene un estado de carga al cambiar de orden', async () => {
    const second = deferred<ProductionOrder>()
    vi.mocked(productionApi.get).mockImplementation((id) => id === 1 ? Promise.resolve(productionOrder(1)) : second.promise)
    vi.mocked(printersApi.list).mockResolvedValue([])
    renderRoute('/production/1', '/production/2', '/production/:id', <ProductionOrderPage />)
    expect(await screen.findByRole('heading', { name: 'OP-1' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('link', { name: 'Cambiar ruta' }))

    expect(await screen.findByText('Abriendo orden y recursos del taller…')).toBeInTheDocument()
    await act(async () => second.resolve(productionOrder(2)))
    expect(await screen.findByRole('heading', { name: 'OP-2' })).toBeInTheDocument()
  })

  it('ignora la orden obsoleta si dos cotizaciones aceptadas se cargan fuera de orden', async () => {
    const firstOrder = deferred<ProductionOrder>()
    const secondOrder = deferred<ProductionOrder>()
    vi.mocked(quotesApi.get).mockImplementation((id) => Promise.resolve(quote(id)))
    vi.mocked(productionApi.getByQuote).mockImplementation((id) => id === 1 ? firstOrder.promise : secondOrder.promise)
    renderRoute('/quotes/1', '/quotes/2', '/quotes/:id', <QuoteDetailPage />)
    await waitFor(() => expect(productionApi.getByQuote).toHaveBeenCalledWith(1))

    fireEvent.click(screen.getByRole('link', { name: 'Cambiar ruta' }))
    await waitFor(() => expect(productionApi.getByQuote).toHaveBeenCalledWith(2))
    await act(async () => secondOrder.resolve(productionOrder(2, 2)))
    expect(await screen.findByRole('link', { name: 'Ver orden' })).toHaveAttribute('href', '/production/2')

    await act(async () => firstOrder.resolve(productionOrder(1, 1)))

    expect(screen.getByRole('link', { name: 'Ver orden' })).toHaveAttribute('href', '/production/2')
    expect(screen.queryByText('OP-1')).not.toBeInTheDocument()
  })
})
