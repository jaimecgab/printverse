import { Factory, FilePlus2, FileText, Gauge, LogOut, Package, Printer, ShieldCheck, Users } from 'lucide-react'
import { NavLink, Outlet } from 'react-router-dom'
import { Brand } from './Brand'
import { useAuth } from '../context/AuthContext'

const links = [
  { to: '/', label: 'Dashboard', icon: Gauge, end: true },
  { to: '/quotes', label: 'Cotizaciones', icon: FileText, end: true },
  { to: '/production', label: 'Producción', icon: Factory },
  { to: '/customers', label: 'Clientes', icon: Users },
  { to: '/materials', label: 'Materiales', icon: Package },
  { to: '/printers', label: 'Impresoras', icon: Printer },
]

const mobileLinks = links.filter(({ to }) => ['/', '/quotes', '/production', '/customers'].includes(to))

export function AppLayout() {
  const { user, logout } = useAuth()

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <Brand />
        <nav className="sidebar-nav" aria-label="Navegación principal">
          <p className="nav-caption">Taller</p>
          {links.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
              <Icon size={19} />
              {label}
            </NavLink>
          ))}
          <NavLink to="/quotes/new" end className={({ isActive }) => `nav-link nav-link--create${isActive ? ' active' : ''}`}>
            <FilePlus2 size={19} />
            Nueva cotización
          </NavLink>
        </nav>
        <div className="sidebar-user">
          <ShieldCheck size={18} />
          <span><strong>{user?.displayName}</strong><small>{user?.role === 'ADMIN' ? 'Administrador' : 'Operador'}</small></span>
          <button className="icon-button" onClick={logout} aria-label="Cerrar sesión" title="Cerrar sesión"><LogOut size={17} /></button>
        </div>
        <div className="sidebar-footer">
          PrintVerse <small>Sesión protegida</small>
        </div>
      </aside>

      <div className="mobile-topbar">
        <Brand />
        <div className="mobile-topbar-actions">
          <span className="mobile-user-name">{user?.displayName}</span>
          <NavLink to="/quotes/new" className="button button--accent button--compact"><FilePlus2 size={18} /> Nueva</NavLink>
          <button className="icon-button mobile-logout" onClick={logout} aria-label="Cerrar sesión"><LogOut size={18} /></button>
        </div>
      </div>

      <main className="main-content">
        <Outlet />
      </main>

      <nav className="mobile-nav" aria-label="Navegación móvil">
        {mobileLinks.map(({ to, label, icon: Icon, end }) => (
          <NavLink key={to} to={to} end={end} className={({ isActive }) => (isActive ? 'active' : '')}>
            <Icon size={20} />
            <span>{label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
