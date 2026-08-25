// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { PieceEditor } from './PieceEditor'
import type { Material, Printer } from '../types'

const material: Material = {
  id: 1, name: 'PLA', pricePerKg: 400, materialType: 'PLA', brand: null, color: null,
  stockGrams: null, lowStockThresholdGrams: null, notes: null, active: true, createdAt: '2026-08-24T00:00:00Z',
}
const printer: Printer = {
  id: 2, name: 'MK4', model: null, costPerHour: 30, operationalStatus: 'AVAILABLE', notes: null,
  active: true, createdAt: '2026-08-24T00:00:00Z',
}

describe('editor atómico de piezas', () => {
  afterEach(cleanup)

  it('incluye un cargo escrito aunque no se pulse Añadir antes de guardar', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined)
    render(<PieceEditor materials={[material]} printers={[printer]} onClose={vi.fn()} onSave={onSave} />)
    fireEvent.change(screen.getByLabelText('Nombre de la pieza *'), { target: { value: 'Carcasa' } })
    fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: ' Pintura ' } })
    fireEvent.change(screen.getByLabelText('Importe'), { target: { value: '25.50' } })

    fireEvent.click(screen.getByRole('button', { name: 'Agregar pieza' }))

    await waitFor(() => expect(onSave).toHaveBeenCalledOnce())
    expect(onSave.mock.calls[0][0].additionalCharges).toEqual([{ description: 'Pintura', amount: 25.5 }])
  })

  it('no guarda un importe pendiente sin descripción', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined)
    render(<PieceEditor materials={[material]} printers={[printer]} onClose={vi.fn()} onSave={onSave} />)
    fireEvent.change(screen.getByLabelText('Nombre de la pieza *'), { target: { value: 'Carcasa' } })
    fireEvent.change(screen.getByLabelText('Importe'), { target: { value: '10' } })

    fireEvent.click(screen.getByRole('button', { name: 'Agregar pieza' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Escribe una descripción para el cargo pendiente.')
    expect(onSave).not.toHaveBeenCalled()
  })
})
