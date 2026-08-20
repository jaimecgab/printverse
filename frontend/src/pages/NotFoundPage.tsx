import { ArrowLeft, Box } from 'lucide-react'
import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return <div className="not-found"><Box size={48} /><p className="eyebrow">Error 404</p><h1>Esta pieza no encaja</h1><p>La ruta solicitada no existe dentro del taller.</p><Link to="/" className="button button--primary"><ArrowLeft size={17} /> Volver al panel</Link></div>
}
