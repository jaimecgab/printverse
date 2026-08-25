import type { AuthSession } from '../types/auth'

const STORAGE_KEY = 'printverse.auth'
export const AUTH_SESSION_INVALIDATED_EVENT = 'printverse:auth-session-invalidated'

export function readSession(): AuthSession | null {
  const stored = sessionStorage.getItem(STORAGE_KEY)
  if (!stored) return null
  try {
    const session = JSON.parse(stored) as AuthSession
    if (!session.accessToken || !session.user?.username || Date.parse(session.expiresAt) <= Date.now()) {
      sessionStorage.removeItem(STORAGE_KEY)
      return null
    }
    return session
  } catch {
    sessionStorage.removeItem(STORAGE_KEY)
    return null
  }
}

export function writeSession(session: AuthSession): void {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session))
}

export function clearSession(): void {
  sessionStorage.removeItem(STORAGE_KEY)
}

export function invalidateSession(): void {
  clearSession()
  window.dispatchEvent(new Event(AUTH_SESSION_INVALIDATED_EVENT))
}

export function getAccessToken(): string | null {
  return readSession()?.accessToken || null
}
