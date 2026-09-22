import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { authApi } from '../api'
import {
  AUTH_SESSION_INVALIDATED_EVENT,
  clearSession,
  invalidateSession,
  readSession,
  writeSession,
} from '../auth/session'
import type { AuthSession, AuthUser, LoginRequest } from '../types/auth'

interface AuthContextValue {
  user: AuthUser | null
  login: (credentials: LoginRequest) => Promise<void>
  logout: () => void
  isAdmin: boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(() => readSession())

  useEffect(() => {
    const handleInvalidation = () => setSession(null)
    window.addEventListener(AUTH_SESSION_INVALIDATED_EVENT, handleInvalidation)
    return () => window.removeEventListener(AUTH_SESSION_INVALIDATED_EVENT, handleInvalidation)
  }, [])

  useEffect(() => {
    if (!session) return
    const remaining = Date.parse(session.expiresAt) - Date.now()
    if (remaining <= 0) {
      invalidateSession()
      return
    }
    const timer = window.setTimeout(invalidateSession, remaining)
    return () => window.clearTimeout(timer)
  }, [session])

  async function login(credentials: LoginRequest) {
    const response = await authApi.login(credentials)
    writeSession(response)
    setSession(response)
  }

  function logout() {
    clearSession()
    setSession(null)
  }

  return (
    <AuthContext.Provider value={{ user: session?.user || null, login, logout, isAdmin: session?.user.role === 'ADMIN' }}>
      {children}
    </AuthContext.Provider>
  )
}

// The provider and hook intentionally share this small module.
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth debe usarse dentro de AuthProvider')
  return context
}
