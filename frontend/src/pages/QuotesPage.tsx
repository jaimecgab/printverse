import { ArrowRight, FilePlus2, FileText, Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { quotesApi } from '../api'
import { ApiError } from '../api/http'
import { EmptyState, ErrorState, LoadingState } from '../components/Feedback'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import type { QuoteStatus, QuoteSummary } from '../types'
import { currency, formatDate, quoteValidity } from '../utils/format'

type Filter = 'ALL' | QuoteStatus

export function QuotesPage() {
  const [quotes, setQuotes] = useState<QuoteSummary[] | null>(null)
  const [filter, setFilter] = useState<Filter>('ALL')
  const [search, setSearch] = useState('')
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    async function load() {
      try {
        setError(''); const summaries = await quotesApi.list(); if (!active) return; setQuotes(summaries)
      } catch (caught) { if (active) setError(caught instanceof ApiError ? caught.message : 'No fue posible consultar las cotizaciones.') }
    }
    void load(); return () => { active = false }
  }, [reload])

  if (!quotes && !error) return <LoadingState label="Reuniendo cotizaciones y rentabilidad…" />
  if (error) return <ErrorState message={error} retry={() => { setError(''); setReload((value) => value + 1) }} />
  if (!quotes) return null

  const query = search.toLocaleLowerCase('es-MX')
  const filtered = quotes.filter((quote) => (filter === 'ALL' || quote.status === filter) && [quote.quoteNumber, quote.customer.name, quote.customer.phone].some((value) => value.toLocaleLowerCase('es-MX').includes(query)))
  const tabs: { value: Filter; label: string }[] = [{ value: 'ALL', label: 'Todas' }, { value: 'DRAFT', label: 'Borradores' }, { value: 'SENT', label: 'Enviadas' }, { value: 'ACCEPTED', label: 'Aceptadas' }, { value: 'REJECTED', label: 'Rechazadas' }]

  return (
    <div className="page">
      <PageHeader eyebrow="Flujo comercial" title="Cotizaciones" description="Del primer cálculo a la decisión del cliente." actions={<Link to="/quotes/new" className="button button--accent"><FilePlus2 size={18} /> Nueva cotización</Link>} />
      <div className="quote-toolbar"><div className="filter-tabs" role="group" aria-label="Filtrar por estado">{tabs.map((tab) => <button className={filter === tab.value ? 'active' : ''} key={tab.value} onClick={() => setFilter(tab.value)}>{tab.label}<span>{tab.value === 'ALL' ? quotes.length : quotes.filter((quote) => quote.status === tab.value).length}</span></button>)}</div><label className="search-box"><Search size={18} /><span className="sr-only">Buscar cotización</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Folio, cliente o teléfono…" /></label></div>
      {filtered.length === 0 ? <EmptyState icon={<FileText />} title={quotes.length ? 'Sin resultados en esta vista' : 'Aún no hay cotizaciones'} description={quotes.length ? 'Ajusta el estado o el texto de búsqueda.' : 'Crea un borrador usando clientes y costos reales.'} action={!quotes.length ? <Link className="button button--primary" to="/quotes/new">Crear primera cotización</Link> : undefined} /> : <section className="quotes-list">{filtered.map((quote) => { const validity = quoteValidity(quote.validUntil); return <Link className="quote-card" to={`/quotes/${quote.id}`} key={quote.id}><div className="quote-stripe" /><div className="quote-main"><div className="quote-heading"><span className="quote-number">{quote.quoteNumber}</span><StatusBadge status={quote.status} /></div><h2>{quote.customer.name}</h2><p>{quote.customer.phone} · Creada {formatDate(quote.createdAt)}</p></div><div className={`validity validity--${validity}`}><span>Vigencia</span><strong>{validity === 'expired' ? 'Vencida' : formatDate(quote.validUntil)}</strong></div><div className="quote-money"><span>Total cotizado</span><strong>{currency.format(quote.total)}</strong><small>Ganancia {currency.format(quote.estimatedProfit)}</small></div><ArrowRight className="quote-arrow" /></Link>})}</section>}
    </div>
  )
}
