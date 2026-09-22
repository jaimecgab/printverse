import { describe, expect, it } from 'vitest'
import { ratioToPercentage } from './format'

describe('formato de tasas', () => {
  it('convierte la razón autoritativa del backend a porcentaje de UI', () => {
    expect(ratioToPercentage(0.5)).toBe(50)
    expect(ratioToPercentage(0)).toBe(0)
    expect(ratioToPercentage(1)).toBe(100)
  })
})
