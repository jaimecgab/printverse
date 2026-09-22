// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useUnsavedChangesWarning } from './useUnsavedChangesWarning'

function Harness({ dirty }: { dirty: boolean }) {
  useUnsavedChangesWarning(dirty, '¿Salir?')
  return <><a href="/otra" onClick={(event) => event.preventDefault()}>Interno</a><a href="https://example.com/otra" onClick={(event) => event.preventDefault()}>Externo</a></>
}

describe('protección de cambios sin guardar', () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks() })

  it('cancela clics internos cuando el usuario decide permanecer', () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    render(<Harness dirty />)

    const allowed = fireEvent.click(screen.getByRole('link', { name: 'Interno' }))

    expect(allowed).toBe(false)
    expect(confirm).toHaveBeenCalledWith('¿Salir?')
  })

  it('no intercepta enlaces externos ni enlaces internos cuando el formulario está limpio', () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const { rerender } = render(<Harness dirty />)

    fireEvent.click(screen.getByRole('link', { name: 'Externo' }))
    rerender(<Harness dirty={false} />)
    fireEvent.click(screen.getByRole('link', { name: 'Interno' }))

    expect(confirm).not.toHaveBeenCalled()
  })
})
