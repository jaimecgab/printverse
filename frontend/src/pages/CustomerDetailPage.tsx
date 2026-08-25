import { ArrowLeft, ArrowRight, FilePlus2, Mail, Phone, UserRound } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { customersApi } from '../api'
import { ApiError } from '../api/http'
import { EmptyState, ErrorState, LoadingState } from '../components/Feedback'
import { StatusBadge } from '../components/StatusBadge'
import type { CustomerOverview } from '../types'
import { currency, formatDate } from '../utils/format'

export function CustomerDetailPage() {
  const id = Number(useParams().id)
  const [overview, setOverview] = useState<CustomerOverview | null>(null)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)

  useEffect(() => {
    if (!Number.isInteger(id) || id <= 0) { setError('El identificador del cliente no es válido.'); return }
    let active = true
    setError('')
    customersApi.overview(id).then((response) => { if (active) setOverview(response) }).catch((caught: unknown) => { if (active) setError(caught instanceof ApiError ? caught.message : 'No fue posible cargar el cliente.') })
    return () => { active = false }
  }, [id, reload])

  if ((!overview || overview.customer.id !== id) && !error) return <LoadingState label="Reuniendo actividad del cliente…" />
  if (error) return <ErrorState message={error} retry={() => { setError(''); setOverview(null); setReload((value) => value + 1) }} />
  if (!overview) return null
  const { customer } = overview

  return <div className="page customer-detail-page"><Link to="/customers" className="back-link"><ArrowLeft size={17} /> Volver a clientes</Link><header className="customer-detail-header"><div className="customer-identity"><span className="avatar"><UserRound /></span><div><p className="eyebrow">Cliente desde {formatDate(customer.createdAt)}</p><h1>{customer.name}</h1><div><a href={`tel:${customer.phone}`}><Phone size={16} /> {customer.phone}</a>{customer.email && <a href={`mailto:${customer.email}`}><Mail size={16} /> {customer.email}</a>}</div></div></div><Link to="/quotes/new" className="button button--accent"><FilePlus2 size={18} /> Nueva cotización</Link></header>
    <section className="customer-metrics"><article><span>Ventas aceptadas</span><strong>{currency.format(overview.totalAcceptedRevenue)}</strong></article><article><span>Utilidad estimada</span><strong>{currency.format(overview.totalEstimatedProfit)}</strong></article><article><span>Órdenes de producción</span><strong>{overview.productionOrderCount}</strong></article></section>
    <section className="status-band" aria-label="Cotizaciones del cliente"><div><span>Borradores</span><strong>{overview.quoteCounts.DRAFT || 0}</strong></div><div><span>Enviadas</span><strong>{overview.quoteCounts.SENT || 0}</strong></div><div><span>Aceptadas</span><strong>{overview.quoteCounts.ACCEPTED || 0}</strong></div><div><span>Rechazadas</span><strong>{overview.quoteCounts.REJECTED || 0}</strong></div></section>
    <div className="customer-detail-grid"><section className="panel"><div className="section-heading"><div><p className="eyebrow">Actividad</p><h2>Cotizaciones recientes</h2></div></div>{overview.recentQuotes.length === 0 ? <EmptyState icon={<FilePlus2 />} title="Sin cotizaciones" description="Este cliente todavía no tiene actividad comercial." action={<Link to="/quotes/new" className="button button--primary">Crear cotización</Link>} /> : <div className="recent-list">{overview.recentQuotes.map((quote) => <Link to={`/quotes/${quote.id}`} key={quote.id} className="recent-row"><div><strong>{quote.title || quote.quoteNumber}</strong><span>{quote.quoteNumber} · {formatDate(quote.createdAt)}</span></div><StatusBadge status={quote.status} /><div className="recent-value"><strong>{currency.format(quote.total)}</strong></div><ArrowRight size={17} /></Link>)}</div>}</section><aside className="panel customer-contact-panel"><p className="eyebrow">Ficha interna</p><h2>Contacto y notas</h2><dl><div><dt>Teléfono</dt><dd>{customer.phone}</dd></div><div><dt>Correo</dt><dd>{customer.email || 'Sin correo'}</dd></div></dl><p>{customer.notes || 'No hay notas internas para este cliente.'}</p></aside></div>
  </div>
}
