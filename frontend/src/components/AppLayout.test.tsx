// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AppLayout } from './AppLayout'

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { displayName: 'Ada Operadora', role: 'OPERATOR' },
    logout: vi.fn(),
  }),
}))

describe('app shell', () => {
  afterEach(cleanup)

  it('mantiene la navegación y presenta la marca aprobada', () => {
    render(
      <MemoryRouter initialEntries={['/quotes']}>
        <Routes>
          <Route element={<AppLayout />}>
            <Route path="/quotes" element={<h1>Cotizaciones</h1>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    )

    const navigation = screen.getByRole('navigation', { name: 'Navegación principal' })
    expect(within(navigation).getByText('Navegación')).toBeInTheDocument()
    expect(within(navigation).getByRole('link', { name: 'Cotizaciones' })).toHaveClass('active')
    expect(within(navigation).getByRole('link', { name: 'Nueva cotización' })).toHaveAttribute('href', '/quotes/new')
    expect(screen.getAllByText('GESTIÓN DE IMPRESIÓN 3D')).toHaveLength(2)
    expect(screen.getAllByText('Ada Operadora')).toHaveLength(2)
    expect(screen.getByText('Operador')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Cerrar sesión' })).toHaveLength(2)
    expect(screen.queryByText('Sesión protegida')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Cotizaciones' })).toBeInTheDocument()
  })
})
