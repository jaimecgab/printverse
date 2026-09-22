import { ArrowRight, Factory, Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { productionApi } from '../api'
import { ApiError } from '../api/http'
import { EmptyState, ErrorState, LoadingState } from '../components/Feedback'
import { PageHeader } from '../components/PageHeader'
import { ProductionStatusBadge } from '../components/ProductionStatusBadge'
import type { ProductionOrder, ProductionOrderStatus, ProductionPriority } from '../types'
import { formatDate } from '../utils/format'
import { orderProgress, productionOrderDueBucket, productionPriorityLabels } from '../utils/production'

type DueFilter = 'ALL' | 'OVERDUE' | 'SOON'

export function ProductionPage() {
  const [orders, setOrders] = useState<ProductionOrder[] | null>(null)
  const [status, setStatus] = useState<'ALL' | ProductionOrderStatus>('ALL')
  const [priority, setPriority] = useState<'ALL' | ProductionPriority>('ALL')
  const [due, setDue] = useState<DueFilter>('ALL')
  const [search, setSearch] = useState('')
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    setError('')
    productionApi.list().then((response) => { if (active) setOrders(response) }).catch((caught: unknown) => { if (active) setError(caught instanceof ApiError ? caught.message : 'No fue posible cargar producción.') })
    return () => { active = false }
  }, [reload])

  if (!orders && !error) return <LoadingState label="Consultando la cola de producción…" />
  if (error) return <ErrorState message={error} retry={() => { setError(''); setOrders(null); setReload((value) => value + 1) }} />
  if (!orders) return null

  const query = search.toLocaleLowerCase('es-MX')
  const filtered = orders.filter((order) => {
    const bucket = productionOrderDueBucket(order)
    return (status === 'ALL' || order.status === status)
      && (priority === 'ALL' || order.priority === priority)
      && (due === 'ALL' || due === 'OVERDUE' && bucket === 'overdue' || due === 'SOON' && bucket === 'soon')
      && [order.orderNumber, order.quote.quoteNumber, order.quote.title || '', order.quote.customer.name].some((value) => value.toLocaleLowerCase('es-MX').includes(query))
  })
  const statuses: Array<'ALL' | ProductionOrderStatus> = ['ALL', 'PENDING', 'IN_PRODUCTION', 'READY', 'DELIVERED', 'CANCELLED']
  const statusLabels = { ALL: 'Todas', PENDING: 'Pendientes', IN_PRODUCTION: 'En producción', READY: 'Listas', DELIVERED: 'Entregadas', CANCELLED: 'Canceladas' }

  return (
    <div className="page production-page">
      <PageHeader eyebrow="Operación del taller" title="Producción" description="Órdenes aceptadas, carga activa y avance por pieza." />
      <div className="production-toolbar">
        <div className="filter-tabs" role="group" aria-label="Filtrar producción por estado">{statuses.map((value) => <button key={value} aria-pressed={status === value} className={status === value ? 'active' : ''} onClick={() => setStatus(value)}>{statusLabels[value]}<span>{value === 'ALL' ? orders.length : orders.filter((order) => order.status === value).length}</span></button>)}</div>
        <div className="production-secondary-filters"><label>Prioridad<select value={priority} onChange={(event) => setPriority(event.target.value as typeof priority)}><option value="ALL">Todas</option>{(['LOW', 'NORMAL', 'HIGH', 'URGENT'] as ProductionPriority[]).map((value) => <option key={value} value={value}>{productionPriorityLabels[value]}</option>)}</select></label><div className="segmented-filter" role="group" aria-label="Filtrar por vencimiento">{([['ALL', 'Cualquier fecha'], ['OVERDUE', 'Vencidas'], ['SOON', 'Próximas 7 días']] as [DueFilter, string][]).map(([value, label]) => <button key={value} aria-pressed={due === value} className={due === value ? 'active' : ''} onClick={() => setDue(value)}>{label}</button>)}</div><label className="search-box"><Search size={18} /><span className="sr-only">Buscar orden</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Orden, cotización o cliente…" /></label></div>
      </div>

      {filtered.length === 0 ? <EmptyState icon={<Factory />} title={orders.length ? 'No hay órdenes con estos filtros' : 'Producción está vacía'} description={orders.length ? 'Ajusta estado, prioridad, fecha o búsqueda.' : 'Las cotizaciones aceptadas se convierten a producción desde su detalle.'} /> : <section className="production-list">{filtered.map((order) => { const progress = orderProgress(order); const bucket = productionOrderDueBucket(order); return <Link className="production-card" to={`/production/${order.id}`} key={order.id}><div><div className="production-card-heading"><strong>{order.orderNumber}</strong><ProductionStatusBadge status={order.status} /><span className={`priority priority--${order.priority.toLowerCase()}`}>{productionPriorityLabels[order.priority]}</span></div><h2>{order.quote.title || order.quote.quoteNumber}</h2><p>{order.quote.customer.name} · {order.quote.quoteNumber}</p></div><div className={`production-due production-due--${bucket}`}><span>Entrega</span><strong>{formatDate(order.dueDate)}</strong></div><div className="progress-cell"><span>{progress}% completo</span><div className="progress-track" aria-label={`Progreso ${progress}%`}><i style={{ width: `${progress}%` }} /></div><small>{order.items.filter((item) => item.status === 'COMPLETED').length} de {order.items.length} piezas completas</small></div><ArrowRight /></Link>})}</section>}
    </div>
  )
}
