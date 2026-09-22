// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { printersApi } from '../api'
import { ApiError } from '../api/http'
import type { Printer } from '../types'
import { PrintersPage } from './PrintersPage'

vi.mock('../api', () => ({ printersApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), setActive: vi.fn() } }))
vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ isAdmin: true }) }))
vi.mock('../context/ToastContext', () => ({ useToast: () => ({ success: vi.fn(), error: vi.fn() }) }))

const busyPrinter: Printer = {
  id: 1, name: 'MK4', model: null, costPerHour: 30, operationalStatus: 'BUSY', notes: null,
  active: true, createdAt: '2026-08-24T00:00:00Z',
}

describe('catálogo de impresoras gestionado por producción', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(cleanup)

  it('conserva BUSY al editar metadatos y bloquea estado y archivado', async () => {
    vi.mocked(printersApi.list).mockResolvedValue([busyPrinter])
    vi.mocked(printersApi.update).mockResolvedValue({ ...busyPrinter, name: 'MK4 actualizada' })
    render(<PrintersPage />)

    expect(await screen.findByRole('button', { name: 'Archivar MK4' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Editar' }))

    expect(screen.getByLabelText('Estado operativo *')).toBeDisabled()
    expect(within(screen.getByRole('dialog')).getByRole('checkbox')).toBeDisabled()
    fireEvent.change(screen.getByLabelText('Nombre *'), { target: { value: 'MK4 actualizada' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar impresora' }))

    await waitFor(() => expect(printersApi.update).toHaveBeenCalledWith(1, {
      name: 'MK4 actualizada', model: null, costPerHour: 30, operationalStatus: 'BUSY', notes: null, active: true,
    }))
  })

  it('no ofrece BUSY al crear una impresora manualmente', async () => {
    vi.mocked(printersApi.list).mockResolvedValue([])
    render(<PrintersPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Nueva impresora' }))

    const status = screen.getByLabelText('Estado operativo *')
    expect(within(status).getAllByRole('option').map((option) => option.textContent)).toEqual(['Disponible', 'Mantenimiento', 'Fuera de servicio'])
    expect(within(screen.getByRole('dialog')).getByRole('checkbox')).toBeEnabled()
  })

  it('recarga el catálogo si producción ocupa la impresora durante la edición', async () => {
    const availablePrinter = { ...busyPrinter, operationalStatus: 'AVAILABLE' as const }
    vi.mocked(printersApi.list)
      .mockResolvedValueOnce([availablePrinter])
      .mockResolvedValueOnce([busyPrinter])
    vi.mocked(printersApi.update).mockRejectedValue(new ApiError(409, {
      detail: 'A BUSY printer must remain BUSY while it has active production',
    }))
    render(<PrintersPage />)

    fireEvent.click(await screen.findByRole('button', { name: 'Editar' }))
    fireEvent.click(screen.getByRole('button', { name: 'Guardar impresora' }))

    await waitFor(() => expect(printersApi.list).toHaveBeenCalledTimes(2))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(await screen.findByText('Ocupada')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Archivar MK4' })).toBeDisabled()
  })

  it('recarga el catálogo si producción ocupa la impresora antes de archivarla', async () => {
    const availablePrinter = { ...busyPrinter, operationalStatus: 'AVAILABLE' as const }
    vi.mocked(printersApi.list)
      .mockResolvedValueOnce([availablePrinter])
      .mockResolvedValueOnce([busyPrinter])
    vi.mocked(printersApi.setActive).mockRejectedValue(new ApiError(409, {
      detail: 'A BUSY printer cannot be archived',
    }))
    render(<PrintersPage />)

    fireEvent.click(await screen.findByRole('button', { name: 'Archivar MK4' }))

    await waitFor(() => expect(printersApi.list).toHaveBeenCalledTimes(2))
    expect(await screen.findByText('Ocupada')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Archivar MK4' })).toBeDisabled()
  })
})
