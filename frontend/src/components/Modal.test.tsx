// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { Modal } from './Modal'

describe('foco del modal', () => {
  afterEach(cleanup)

  it('mueve el foco al primer control al abrirse', () => {
    const background = document.createElement('button')
    document.body.appendChild(background)
    background.focus()

    render(<Modal title="Editar" onClose={() => undefined}><select aria-label="Primer campo"><option>Uno</option></select></Modal>)

    expect(screen.getByLabelText('Primer campo')).toHaveFocus()
    background.remove()
  })
})
