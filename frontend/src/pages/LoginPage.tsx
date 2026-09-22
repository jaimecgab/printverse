import { LockKeyhole, LogIn } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/http'
import { Brand } from '../components/Brand'
import { FormField } from '../components/FormField'
import { useAuth } from '../context/AuthContext'

interface LoginLocationState {
  from?: string
}

export function LoginPage() {
  const { user, login } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const requestedPath = (location.state as LoginLocationState | null)?.from
  const destination = requestedPath?.startsWith('/') ? requestedPath : '/'

  if (user) return <Navigate to={destination} replace />

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    setError('')
    try {
      await login({ username, password })
      navigate(destination, { replace: true })
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'No fue posible iniciar sesión.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-brand-panel">
        <Brand />
        <div>
          <p className="eyebrow">Control de taller</p>
          <h1>Del presupuesto a la pieza terminada.</h1>
          <p>Acceso interno para cotización, producción y seguimiento operativo.</p>
        </div>
        <span className="login-grid-mark" aria-hidden="true" />
      </section>
      <section className="login-form-panel">
        <form className="login-form" onSubmit={submit}>
          <span className="login-lock"><LockKeyhole size={24} /></span>
          <p className="eyebrow">Sesión segura</p>
          <h2>Entrar a PrintVerse</h2>
          <p className="login-intro">Usa las credenciales asignadas para tu rol de taller.</p>
          {error && <div className="inline-error" role="alert">{error}</div>}
          <FormField label="Usuario" htmlFor="login-username">
            <input id="login-username" autoFocus autoComplete="username" value={username} maxLength={80} onChange={(event) => setUsername(event.target.value)} required />
          </FormField>
          <FormField label="Contraseña" htmlFor="login-password">
            <input id="login-password" type="password" autoComplete="current-password" value={password} maxLength={200} onChange={(event) => setPassword(event.target.value)} required />
          </FormField>
          <button className="button button--accent login-submit" disabled={busy}>
            <LogIn size={18} /> {busy ? 'Verificando…' : 'Iniciar sesión'}
          </button>
        </form>
      </section>
    </main>
  )
}
