import { Factory, FilePlus2, FileText, Gauge, Package, Printer, Users } from 'lucide-react'
import { NavLink, Outlet } from 'react-router-dom'
import { Brand } from './Brand'

const links = [
  { to: '/', label: 'Dashboard', icon: Gauge, end: true },
  { to: '/quotes', label: 'Cotizaciones', icon: FileText },
  { to: '/customers', label: 'Clientes', icon: Users },
  { to: '/materials', label: 'Materiales', icon: Package },
  { to: '/printers', label: 'Impresoras', icon: Printer },
]

export function AppLayout() {
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
          <NavLink to="/quotes/new" className="nav-link nav-link--create">
            <FilePlus2 size={19} />
            Nueva cotización
          </NavLink>
          <div className="nav-link nav-link--disabled" aria-disabled="true">
            <Factory size={19} />
            Producción
            <small>Próximamente</small>
          </div>
        </nav>
        <div className="sidebar-footer">
          <span className="live-dot" /> API configurada
          <small>Iteración 2</small>
        </div>
      </aside>

      <div className="mobile-topbar">
        <Brand />
        <NavLink to="/quotes/new" className="button button--accent button--compact">
          <FilePlus2 size={18} /> Nueva
        </NavLink>
      </div>

      <main className="main-content">
        <Outlet />
      </main>

      <nav className="mobile-nav" aria-label="Navegación móvil">
        {links.map(({ to, label, icon: Icon, end }) => (
          <NavLink key={to} to={to} end={end} className={({ isActive }) => (isActive ? 'active' : '')}>
            <Icon size={20} />
            <span>{label}</span>
          </NavLink>
        ))}
        <span className="mobile-nav-disabled" aria-disabled="true">
          <Factory size={20} />
          <span>Producción</span>
        </span>
      </nav>
    </div>
  )
}
