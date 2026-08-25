// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { clearSession, readSession, writeSession } from './session'
import type { AuthSession } from '../types/auth'

const validSession: AuthSession = {
  accessToken: 'signed-token',
  tokenType: 'Bearer',
  expiresAt: '2099-01-01T00:00:00Z',
  user: { username: 'operator', displayName: 'Operador', role: 'OPERATOR' },
}

describe('auth session storage', () => {
  afterEach(clearSession)

  it('persists a valid session in sessionStorage', () => {
    writeSession(validSession)

    expect(readSession()).toEqual(validSession)
  })

  it('removes an expired session', () => {
    writeSession({ ...validSession, expiresAt: '2020-01-01T00:00:00Z' })

    expect(readSession()).toBeNull()
    expect(sessionStorage.length).toBe(0)
  })
})
