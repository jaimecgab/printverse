import { AlertCircle, ArrowRight, FilePlus2, FileText, Package, Printer, Users } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { customersApi, materialsApi, printersApi, quotesApi } from '../api'
import { ApiError } from '../api/http'
import { EmptyState, ErrorState, LoadingState } from '../components/Feedback'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import type { Customer, Material, Printer as PrinterType, Quote, QuoteSummary } from '../types'
import { currency, formatDate } from '../utils/format'

interface DashboardData {
  customers: Customer[]
  materials: Material[]
  printers: PrinterType[]
  summaries: QuoteSummary[]
  details: Quote[]
  detailFailures: number
}

export function DashboardPage() {
  const [data, setData] = useState<DashboardData | null>(null)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    async function load() {
      try {
        setError('')
        const [customers, materials, printers, summaries] = await Promise.all([
          customersApi.list(), materialsApi.list(), printersApi.list(), quotesApi.list(),
        ])
        const settled = await Promise.allSettled(summaries.map((quote) => quotesApi.get(quote.id)))
        if (!active) return
        const details = settled.flatMap((result) => result.status === 'fulfilled' ? [result.value] : [])
        setData({ customers, materials, printers, summaries, details, detailFailures: settled.length - details.length })
      } catch (caught) {
        if (active) setError(caught instanceof ApiError ? caught.message : 'Ocurrió un error inesperado.')
      }
    }
    void load()
    return () => { active = false }
  }, [reload])

  if (!data && !error) return <LoadingState label="Calculando el pulso del taller…" />
  if (error) return <ErrorState message={error} retry={() => setReload((value) => value + 1)} />
  if (!data) return null

  const counts = { DRAFT: 0, SENT: 0, ACCEPTED: 0, REJECTED: 0 }
  data.summaries.forEach((quote) => { counts[quote.status] += 1 })
  const quotedValue = data.summaries.reduce((sum, quote) => sum + quote.total, 0)
  const profit = data.details.reduce((sum, quote) => sum + quote.estimatedProfit, 0)
  const recent = data.summaries.slice(0, 5)
  const noMaterials = !data.materials.some((material) => material.active)
  const noPrinters = !data.printers.some((printer) => printer.active)

  return (
    <div className="page dashboard-page">
      <PageHeader
        eyebrow="Control de producción"
        title="El taller, en perspectiva"
        description="Cotizaciones, demanda y rentabilidad estimada con datos actuales de PrintVerse."
        actions={<Link className="button button--accent" to="/quotes/new"><FilePlus2 size={18} /> Nueva cotización</Link>}
      />

      {(noMaterials || noPrinters || data.detailFailures > 0) && (
        <section className="alerts-stack" aria-label="Alertas operativas">
          {noMaterials && <Link to="/materials" className="alert-band"><AlertCircle /><span><strong>Sin materiales activos.</strong> Activa uno para poder agregar piezas.</span><ArrowRight /></Link>}
          {noPrinters && <Link to="/printers" className="alert-band"><AlertCircle /><span><strong>Sin impresoras activas.</strong> La creación de piezas está detenida.</span><ArrowRight /></Link>}
          {data.detailFailures > 0 && <div className="alert-band alert-band--neutral"><AlertCircle /><span>No se pudo calcular la ganancia de {data.detailFailures} cotización(es). El total mostrado es parcial.</span></div>}
        </section>
      )}

      <section className="metric-grid" aria-label="Métricas principales">
        <article className="metric-card metric-card--hero">
          <span className="metric-index">01</span>
          <p>Valor total cotizado</p>
          <strong>{currency.format(quotedValue)}</strong>
          <small>{data.summaries.length} cotizaciones acumuladas</small>
        </article>
        <article className="metric-card">
          <span className="metric-index">02</span>
          <p>Ganancia estimada</p>
          <strong>{currency.format(profit)}</strong>
          <small>Calculada desde el detalle disponible</small>
        </article>
        <article className="metric-card">
          <span className="metric-index">03</span>
          <p>Clientes</p>
          <strong>{data.customers.length}</strong>
          <small>Contactos registrados</small>
        </article>
      </section>

      <section className="status-band" aria-label="Cotizaciones por estado">
        <div><span>Borradores</span><strong>{counts.DRAFT}</strong></div>
        <div><span>Enviadas</span><strong>{counts.SENT}</strong></div>
        <div><span>Aceptadas</span><strong>{counts.ACCEPTED}</strong></div>
        <div><span>Rechazadas</span><strong>{counts.REJECTED}</strong></div>
      </section>

      <div className="dashboard-columns">
        <section className="panel">
          <div className="section-heading">
            <div><p className="eyebrow">Actividad</p><h2>Cotizaciones recientes</h2></div>
            <Link to="/quotes" className="text-link">Ver todas <ArrowRight size={16} /></Link>
          </div>
          {recent.length === 0 ? (
            <EmptyState icon={<FileText />} title="Aún no hay cotizaciones" description="Crea el primer borrador para comenzar a medir tu operación." action={<Link className="button button--primary" to="/quotes/new">Crear cotización</Link>} />
          ) : (
            <div className="recent-list">
              {recent.map((quote) => (
                <Link to={`/quotes/${quote.id}`} key={quote.id} className="recent-row">
                  <div><strong>{quote.quoteNumber}</strong><span>{quote.customer.name}</span></div>
                  <StatusBadge status={quote.status} />
                  <div className="recent-value"><strong>{currency.format(quote.total)}</strong><span>{formatDate(quote.createdAt)}</span></div>
                  <ArrowRight size={17} />
                </Link>
              ))}
            </div>
          )}
        </section>

        <aside className="quick-panel">
          <p className="eyebrow">Accesos rápidos</p>
          <h2>Prepara el siguiente trabajo</h2>
          <div className="quick-links">
            <Link to="/quotes/new"><FilePlus2 /><span><strong>Cotizar proyecto</strong><small>Nuevo borrador</small></span></Link>
            <Link to="/customers"><Users /><span><strong>Gestionar clientes</strong><small>{data.customers.length} registrados</small></span></Link>
            <Link to="/materials"><Package /><span><strong>Costos de material</strong><small>{data.materials.filter((item) => item.active).length} activos</small></span></Link>
            <Link to="/printers"><Printer /><span><strong>Parque de máquinas</strong><small>{data.printers.filter((item) => item.active).length} activas</small></span></Link>
          </div>
        </aside>
      </div>
    </div>
  )
}
