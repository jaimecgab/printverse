import { AlertTriangle, ArrowRight, Factory, FilePlus2, FileText, Package, Printer } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { dashboardApi } from '../api'
import { ApiError } from '../api/http'
import { EmptyState, ErrorState, LoadingState } from '../components/Feedback'
import { PageHeader } from '../components/PageHeader'
import { ProductionStatusBadge } from '../components/ProductionStatusBadge'
import { StatusBadge } from '../components/StatusBadge'
import type { Dashboard } from '../types'
import { currency, formatDate, number, ratioToPercentage } from '../utils/format'
import { openProductionOrderCount, printerOperationalStatusLabels, productionPriorityLabels, productionStatusLabels } from '../utils/production'

export function DashboardPage() {
  const [data, setData] = useState<Dashboard | null>(null)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    setError('')
    dashboardApi.get().then((response) => { if (active) setData(response) }).catch((caught: unknown) => { if (active) setError(caught instanceof ApiError ? caught.message : 'Ocurrió un error inesperado.') })
    return () => { active = false }
  }, [reload])

  if (!data && !error) return <LoadingState label="Calculando el pulso del taller…" />
  if (error) return <ErrorState message={error} retry={() => { setError(''); setData(null); setReload((value) => value + 1) }} />
  if (!data) return null

  const productionTotal = openProductionOrderCount(data.productionCounts)
  const dueAlerts = [...data.overdueOrders.map((order) => ({ ...order, overdue: true })), ...data.dueSoonOrders.map((order) => ({ ...order, overdue: false }))]

  return (
    <div className="page dashboard-page">
      <PageHeader eyebrow="Control de operación" title="El taller, en perspectiva" description={`Métricas autoritativas actualizadas ${formatDate(data.updatedAt, true)}.`} actions={<Link className="button button--accent" to="/quotes/new"><FilePlus2 size={18} /> Nueva cotización</Link>} />

      <section className="metric-grid metric-grid--dashboard" aria-label="Métricas comerciales">
        <article className="metric-card metric-card--hero"><span className="metric-index">01</span><p>Pipeline enviado</p><strong>{currency.format(data.sentPipelineAmount)}</strong><small>{data.quoteCounts.SENT || 0} cotizaciones esperando decisión</small></article>
        <article className="metric-card"><span className="metric-index">02</span><p>Ventas aceptadas</p><strong>{currency.format(data.acceptedRevenue)}</strong><small>{data.quoteCounts.ACCEPTED || 0} cotizaciones aceptadas</small></article>
        <article className="metric-card metric-card--profit"><span className="metric-index">03</span><p>Utilidad aceptada</p><strong>{currency.format(data.acceptedEstimatedProfit)}</strong><small>Calculada por el backend</small></article>
        <article className="metric-card"><span className="metric-index">04</span><p>Tasa de aceptación</p><strong>{number.format(ratioToPercentage(data.acceptanceRate))}%</strong><small>Sobre decisiones registradas</small></article>
      </section>

      <section className="status-band" aria-label="Cotizaciones por estado"><div><span>Borradores</span><strong>{data.quoteCounts.DRAFT || 0}</strong></div><div><span>Enviadas</span><strong>{data.quoteCounts.SENT || 0}</strong></div><div><span>Aceptadas</span><strong>{data.quoteCounts.ACCEPTED || 0}</strong></div><div><span>Rechazadas</span><strong>{data.quoteCounts.REJECTED || 0}</strong></div></section>

      <section className="asset-overview" aria-label="Recursos del taller">
        <article className="asset-panel low-stock-panel">
          <div className="section-heading"><div><p className="eyebrow">Inventario manual</p><h2>Stock bajo</h2></div><Link to="/materials" className="text-link">Materiales <ArrowRight size={16} /></Link></div>
          {data.lowStockMaterials.length === 0 ? <p className="asset-empty"><Package size={18} /> Ningún material activo está en su umbral.</p> : <div className="low-stock-list">{data.lowStockMaterials.map((material) => <div key={material.id}><AlertTriangle size={17} /><span><strong>{material.name}</strong><small>{[material.materialType, material.brand, material.color].filter(Boolean).join(' · ') || 'Sin especificaciones'}</small></span><span><strong>≈ {number.format(material.stockGrams)} g</strong><small>umbral {number.format(material.lowStockThresholdGrams)} g</small></span></div>)}</div>}
        </article>
        <article className="asset-panel printer-summary">
          <div className="section-heading"><div><p className="eyebrow">Impresoras activas</p><h2>Disponibilidad</h2></div><Link to="/printers" className="text-link">Impresoras <ArrowRight size={16} /></Link></div>
          <div>{(['AVAILABLE', 'BUSY', 'MAINTENANCE', 'OUT_OF_SERVICE'] as const).map((status) => <div key={status}><Printer size={16} /><span>{printerOperationalStatusLabels[status]}</span><strong>{data.printerCounts[status] || 0}</strong></div>)}</div>
        </article>
      </section>

      <section className="production-pulse"><div className="section-heading"><div><p className="eyebrow">Carga del taller</p><h2>{productionTotal} órdenes abiertas</h2></div><Link to="/production" className="text-link">Abrir producción <ArrowRight size={16} /></Link></div><div>{(['PENDING', 'IN_PRODUCTION', 'READY', 'DELIVERED', 'CANCELLED'] as const).map((status) => <Link key={status} to="/production"><ProductionStatusBadge status={status} /><strong>{data.productionCounts[status] || 0}</strong><span>{productionStatusLabels[status]}</span></Link>)}</div></section>

      <div className="dashboard-columns">
        <section className="panel"><div className="section-heading"><div><p className="eyebrow">Actividad comercial</p><h2>Cotizaciones recientes</h2></div><Link to="/quotes" className="text-link">Ver todas <ArrowRight size={16} /></Link></div>{data.recentQuotes.length === 0 ? <EmptyState icon={<FileText />} title="Aún no hay cotizaciones" description="Crea el primer borrador para comenzar a medir tu operación." action={<Link className="button button--primary" to="/quotes/new">Crear cotización</Link>} /> : <div className="recent-list">{data.recentQuotes.map((quote) => <Link to={`/quotes/${quote.id}`} key={quote.id} className="recent-row"><div><strong>{quote.title || quote.quoteNumber}</strong><span>{quote.customer.name} · {quote.quoteNumber}</span></div><StatusBadge status={quote.status} /><div className="recent-value"><strong>{currency.format(quote.total)}</strong><span>{formatDate(quote.createdAt)}</span></div><ArrowRight size={17} /></Link>)}</div>}</section>

        <aside className="quick-panel due-panel"><p className="eyebrow">Atención operativa</p><h2>Vencidas y próximas</h2>{dueAlerts.length === 0 ? <div className="due-empty"><Factory /><strong>Sin compromisos inmediatos</strong><small>No hay órdenes vencidas ni próximas.</small></div> : <div className="due-list">{dueAlerts.map((order) => <Link to={`/production/${order.id}`} key={`${order.overdue}-${order.id}`} className={order.overdue ? 'overdue' : ''}><AlertTriangle /><span><strong>{order.orderNumber}</strong><small>{order.customerName} · {formatDate(order.dueDate)}</small><small>{productionPriorityLabels[order.priority]}</small></span><ArrowRight /></Link>)}</div>}</aside>
      </div>
    </div>
  )
}
