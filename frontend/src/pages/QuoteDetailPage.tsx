import { ArrowLeft, Check, Download, Edit3, MailCheck, Phone, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { quotesApi } from '../api'
import { ApiError } from '../api/http'
import { ErrorState, LoadingState } from '../components/Feedback'
import { ItemBreakdown, QuoteFinancials } from '../components/QuoteFinancials'
import { StatusBadge } from '../components/StatusBadge'
import { useToast } from '../context/ToastContext'
import type { Quote, QuoteStatus } from '../types'
import { currency, formatDate, number } from '../utils/format'

export function QuoteDetailPage() {
  const id = Number(useParams().id)
  const [quote, setQuote] = useState<Quote | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [reload, setReload] = useState(0)
  const toast = useToast()

  useEffect(() => {
    let active = true
    quotesApi.get(id).then((response) => { if (active) setQuote(response) }).catch((caught: unknown) => { if (active) setError(caught instanceof ApiError ? caught.message : 'No fue posible cargar la cotización.') })
    return () => { active = false }
  }, [id, reload])

  async function changeStatus(status: QuoteStatus) {
    if (busy) return
    const messages: Partial<Record<QuoteStatus, string>> = { SENT: '¿Marcar esta cotización como enviada? Después ya no podrá editarse.', ACCEPTED: '¿Confirmar que el cliente aceptó la cotización?', REJECTED: '¿Confirmar que el cliente rechazó la cotización?' }
    if (!window.confirm(messages[status])) return
    setBusy(true)
    try { const response = await quotesApi.setStatus(id, status); setQuote(response); toast.success(`Estado actualizado: ${status === 'SENT' ? 'enviada' : status === 'ACCEPTED' ? 'aceptada' : 'rechazada'}.`) } catch (caught) { toast.error(caught instanceof ApiError ? caught.message : 'No fue posible cambiar el estado.') } finally { setBusy(false) }
  }

  if ((!quote || quote.id !== id) && !error) return <LoadingState label="Abriendo cotización…" />
  if (error) return <ErrorState message={error} retry={() => { setError(''); setReload((value) => value + 1) }} />
  if (!quote) return null

  return (
    <div className="page quote-detail-page">
      <Link to="/quotes" className="back-link"><ArrowLeft size={17} /> Volver a cotizaciones</Link>
      <header className="quote-document-header">
        <div><p className="eyebrow">Cotización</p><div className="document-title"><h1>{quote.quoteNumber}</h1><StatusBadge status={quote.status} /></div><p>Creada el {formatDate(quote.createdAt, true)}</p></div>
        <div className="page-actions">
          <button className="button button--secondary" disabled title="Próximamente"><Download size={17} /> PDF · Próximamente</button>
          {quote.status === 'DRAFT' && <Link to={`/quotes/${quote.id}/edit`} className="button button--primary"><Edit3 size={17} /> Editar</Link>}
        </div>
      </header>

      <section className="document-meta">
        <div><span className="meta-label">Cliente</span><strong>{quote.customer.name}</strong><a href={`tel:${quote.customer.phone}`}><Phone size={15} /> {quote.customer.phone}</a>{quote.customer.email && <a href={`mailto:${quote.customer.email}`}>{quote.customer.email}</a>}</div>
        <div><span className="meta-label">Vigencia</span><strong>{formatDate(quote.validUntil)}</strong><small>Entrega: {formatDate(quote.estimatedDeliveryDate)}</small></div>
         <div><span className="meta-label">Condiciones</span><strong>{quote.depositPercentage != null ? `${number.format(quote.depositPercentage)}% de anticipo` : 'Sin anticipo definido'}</strong><small>Markup configurado: {number.format(quote.markupPercentage)}%</small></div>
      </section>

      <QuoteFinancials quote={quote} />

      <section className="panel quote-items-section">
        <div className="section-heading"><div><p className="eyebrow">Desglose técnico</p><h2>{quote.items.length} {quote.items.length === 1 ? 'pieza' : 'piezas'}</h2></div></div>
        {quote.items.length === 0 ? <div className="inline-notice">Este borrador todavía no tiene piezas. Edítalo para agregar la primera.</div> : quote.items.map((item, index) => (
          <article className="detail-item" key={item.id}>
            <header><span className="item-index">{String(index + 1).padStart(2, '0')}</span><div><h3>{item.name}</h3><p>{item.quantity} {item.quantity === 1 ? 'unidad' : 'unidades'} · {number.format(item.weightGrams)} g c/u · {Math.floor(item.printTimeMinutes / 60)} h {item.printTimeMinutes % 60} min c/u</p></div><div className="item-price"><span>Subtotal</span><strong>{currency.format(item.itemSubtotal)}</strong></div></header>
            <div className="technical-band"><span><small>Material</small>{item.material.name}</span><span><small>Instantánea material</small>{currency.format(item.materialPricePerKgSnapshot)} / kg</span><span><small>Impresora</small>{item.printer.name}{item.printer.model ? ` · ${item.printer.model}` : ''}</span><span><small>Instantánea máquina</small>{currency.format(item.printerCostPerHourSnapshot)} / h</span><span><small>Riesgo</small>{number.format(item.failureRiskPercentage)}%</span></div>
            <ItemBreakdown item={item} />
            {item.manualUnitPrice != null && <div className="manual-notice">Precio manual aplicado: {currency.format(item.manualUnitPrice)} por unidad. El sugerido era {currency.format(item.suggestedPriceUnit)}.</div>}
            {item.additionalCharges.length > 0 && <div className="charges-readonly"><strong>Cargos por unidad</strong>{item.additionalCharges.map((charge) => <span key={charge.id}>{charge.description}<b>{currency.format(charge.amount)}</b></span>)}</div>}
          </article>
        ))}
      </section>

      <div className="detail-lower-grid">
        <section className="panel totals-ledger"><h2>Cálculo final</h2><dl><div><dt>Subtotal final</dt><dd>{currency.format(quote.finalSubtotal)}</dd></div><div><dt>Descuento ({number.format(quote.discountPercentage)}%)</dt><dd>− {currency.format(quote.discountAmount)}</dd></div><div><dt>Subtotal con descuento</dt><dd>{currency.format(quote.subtotalAfterDiscount)}</dd></div><div><dt>IVA {quote.taxEnabled ? `(${number.format(quote.taxPercentage)}%)` : '(no aplicado)'}</dt><dd>{currency.format(quote.taxAmount)}</dd></div><div className="ledger-total"><dt>Total cotizado</dt><dd>{currency.format(quote.total)}</dd></div></dl></section>
        <section className="panel notes-panel"><h2>Notas</h2><p>{quote.notes || 'No se agregaron notas a esta cotización.'}</p></section>
      </div>

      {quote.status === 'DRAFT' && <section className="status-action-bar"><div><MailCheck /><span><strong>¿Lista para el cliente?</strong><small>Al enviarla se bloquean términos, piezas y costos.</small></span></div><button className="button button--accent" disabled={busy || quote.items.length === 0} onClick={() => void changeStatus('SENT')}>{busy ? 'Actualizando…' : 'Marcar como enviada'}</button></section>}
      {quote.status === 'SENT' && <section className="status-action-bar"><div><MailCheck /><span><strong>Esperando decisión</strong><small>Registra la respuesta del cliente.</small></span></div><div className="page-actions"><button className="button button--danger" disabled={busy} onClick={() => void changeStatus('REJECTED')}><X size={18} /> Rechazada</button><button className="button button--accent" disabled={busy} onClick={() => void changeStatus('ACCEPTED')}><Check size={18} /> Aceptada</button></div></section>}
    </div>
  )
}
