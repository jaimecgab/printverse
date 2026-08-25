import { ArrowLeft, Check, Copy, Download, Edit3, Factory, MailCheck, Phone, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { productionApi, quotesApi } from '../api'
import { ApiError } from '../api/http'
import { ErrorState, LoadingState } from '../components/Feedback'
import { ItemBreakdown, QuoteFinancials } from '../components/QuoteFinancials'
import { StatusBadge } from '../components/StatusBadge'
import { useToast } from '../context/ToastContext'
import type { ProductionOrder, Quote, QuoteStatus } from '../types'
import { addDays, currency, formatDate, number } from '../utils/format'

export function QuoteDetailPage() {
  const id = Number(useParams().id)
  const [quote, setQuote] = useState<Quote | null>(null)
  const [order, setOrder] = useState<ProductionOrder | null>(null)
  const [orderChecked, setOrderChecked] = useState(false)
  const [productionError, setProductionError] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [pdfBusy, setPdfBusy] = useState(false)
  const [reload, setReload] = useState(0)
  const orderRequest = useRef(0)
  const navigate = useNavigate()
  const toast = useToast()

  async function loadOrder(quoteId: number) {
    const request = ++orderRequest.current
    setOrder(null); setOrderChecked(false); setProductionError('')
    try {
      const response = await productionApi.getByQuote(quoteId)
      if (request === orderRequest.current) setOrder(response)
    }
    catch (caught) {
      if (request !== orderRequest.current) return
      if (!(caught instanceof ApiError && caught.status === 404)) setProductionError(caught instanceof ApiError ? caught.message : 'No fue posible consultar la orden de producción.')
    } finally { if (request === orderRequest.current) setOrderChecked(true) }
  }

  useEffect(() => {
    if (!Number.isInteger(id) || id <= 0) { setError('El identificador de la cotización no es válido.'); return }
    let active = true
    orderRequest.current += 1
    setOrder(null); setOrderChecked(false); setProductionError('')
    async function load() {
      try {
        setError('')
        const response = await quotesApi.get(id)
        if (!active) return
        setQuote(response)
        if (response.status === 'ACCEPTED') await loadOrder(response.id)
        else { orderRequest.current += 1; setOrder(null); setOrderChecked(false); setProductionError('') }
      } catch (caught) { if (active) setError(caught instanceof ApiError ? caught.message : 'No fue posible cargar la cotización.') }
    }
    void load()
    return () => { active = false; orderRequest.current += 1 }
  }, [id, reload])

  async function changeStatus(status: QuoteStatus) {
    if (busy) return
    const messages: Partial<Record<QuoteStatus, string>> = { SENT: '¿Marcar esta cotización como enviada? Después ya no podrá editarse.', ACCEPTED: '¿Confirmar que el cliente aceptó la cotización?', REJECTED: '¿Confirmar que el cliente rechazó la cotización?' }
    if (!window.confirm(messages[status])) return
    setBusy(true)
    try {
      const response = await quotesApi.setStatus(id, status)
      setQuote(response)
      if (status === 'ACCEPTED') await loadOrder(id)
      toast.success(`Estado actualizado: ${status === 'SENT' ? 'enviada' : status === 'ACCEPTED' ? 'aceptada' : 'rechazada'}.`)
    } catch (caught) { toast.error(caught instanceof ApiError ? caught.message : 'No fue posible cambiar el estado.') } finally { setBusy(false) }
  }

  async function downloadPdf() {
    if (pdfBusy) return
    setPdfBusy(true)
    try {
      const file = await quotesApi.pdf(id)
      const url = URL.createObjectURL(file.blob)
      const anchor = document.createElement('a')
      anchor.href = url; anchor.download = file.fileName; document.body.appendChild(anchor); anchor.click(); anchor.remove()
      window.setTimeout(() => URL.revokeObjectURL(url), 1_000)
      toast.success('PDF descargado.')
    } catch (caught) { toast.error(caught instanceof ApiError ? caught.message : 'No fue posible descargar el PDF.') } finally { setPdfBusy(false) }
  }

  async function duplicate() {
    if (busy || !window.confirm('¿Crear un nuevo borrador con las mismas piezas y condiciones?')) return
    setBusy(true)
    try {
      const duplicated = await quotesApi.duplicate(id, { validUntil: addDays(15), estimatedDeliveryDate: quote?.estimatedDeliveryDate ? addDays(30) : null })
      toast.success('Cotización duplicada como borrador.')
      navigate(`/quotes/${duplicated.id}/edit`)
    } catch (caught) { toast.error(caught instanceof ApiError ? caught.message : 'No fue posible duplicar la cotización.') } finally { setBusy(false) }
  }

  async function convertToProduction() {
    if (busy || !window.confirm('¿Crear la orden de producción para esta cotización aceptada?')) return
    setBusy(true)
    try {
      const created = await productionApi.convert(id)
      toast.success('Orden de producción disponible.')
      navigate(`/production/${created.id}`)
    } catch (caught) { toast.error(caught instanceof ApiError ? caught.message : 'No fue posible crear la orden de producción.') } finally { setBusy(false) }
  }

  if ((!quote || quote.id !== id) && !error) return <LoadingState label="Abriendo cotización…" />
  if (error) return <ErrorState message={error} retry={() => { setError(''); setQuote(null); setReload((value) => value + 1) }} />
  if (!quote) return null

  return (
    <div className="page quote-detail-page">
      <Link to="/quotes" className="back-link"><ArrowLeft size={17} /> Volver a cotizaciones</Link>
      <header className="quote-document-header">
        <div><p className="eyebrow">Cotización</p><div className="document-title"><h1>{quote.quoteNumber}</h1><StatusBadge status={quote.status} /></div>{quote.title && <h2 className="quote-project-title">{quote.title}</h2>}<p>Creada el {formatDate(quote.createdAt, true)} · actualizada {formatDate(quote.updatedAt, true)}</p></div>
        <div className="page-actions">
          <button className="button button--secondary" disabled={pdfBusy} onClick={() => void downloadPdf()}><Download size={17} /> {pdfBusy ? 'Generando PDF…' : 'Descargar PDF'}</button>
          <button className="button button--secondary" disabled={busy} onClick={() => void duplicate()}><Copy size={17} /> Duplicar</button>
          {quote.status === 'DRAFT' && <Link to={`/quotes/${quote.id}/edit`} className="button button--primary"><Edit3 size={17} /> Editar</Link>}
        </div>
      </header>

      <section className="document-meta">
        <div><span className="meta-label">Cliente</span><strong>{quote.customer.name}</strong><a href={`tel:${quote.customer.phone}`}><Phone size={15} /> {quote.customer.phone}</a>{quote.customer.email && <a href={`mailto:${quote.customer.email}`}>{quote.customer.email}</a>}</div>
        <div><span className="meta-label">Vigencia</span><strong>{formatDate(quote.validUntil)}</strong><small>Entrega: {formatDate(quote.estimatedDeliveryDate)}</small><small>Moneda: {quote.currencyCode}</small></div>
        <div><span className="meta-label">Condiciones comerciales</span><strong>{quote.depositPercentage != null ? `${number.format(quote.depositPercentage)}% de anticipo` : 'Sin anticipo definido'}</strong><small>Anticipo: {currency.format(quote.depositAmount)}</small><small>Saldo: {currency.format(quote.remainingBalance)}</small></div>
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
        <section className="panel totals-ledger"><h2>Cálculo final</h2><dl><div><dt>Subtotal final</dt><dd>{currency.format(quote.finalSubtotal)}</dd></div><div><dt>Descuento ({number.format(quote.discountPercentage)}%)</dt><dd>− {currency.format(quote.discountAmount)}</dd></div><div><dt>Subtotal con descuento</dt><dd>{currency.format(quote.subtotalAfterDiscount)}</dd></div><div><dt>IVA {quote.taxEnabled ? `(${number.format(quote.taxPercentage)}%)` : '(no aplicado)'}</dt><dd>{currency.format(quote.taxAmount)}</dd></div><div className="ledger-total"><dt>Total cotizado</dt><dd>{currency.format(quote.total)}</dd></div><div><dt>Anticipo</dt><dd>{currency.format(quote.depositAmount)}</dd></div><div><dt>Saldo restante</dt><dd>{currency.format(quote.remainingBalance)}</dd></div></dl></section>
        <div className="notes-stack"><section className="panel notes-panel"><p className="eyebrow">Visible al cliente</p><h2>Notas comerciales</h2><p>{quote.notes || 'No se agregaron notas para el cliente.'}</p></section><section className="panel notes-panel notes-panel--internal"><p className="eyebrow">Uso interno · no aparece en PDF</p><h2>Notas internas</h2><p>{quote.internalNotes || 'No hay notas internas.'}</p></section></div>
      </div>

      {quote.status === 'DRAFT' && <section className="status-action-bar"><div><MailCheck /><span><strong>¿Lista para el cliente?</strong><small>Al enviarla se bloquean términos, piezas y costos.</small></span></div><button className="button button--accent" disabled={busy || quote.items.length === 0} onClick={() => void changeStatus('SENT')}>{busy ? 'Actualizando…' : 'Marcar como enviada'}</button></section>}
      {quote.status === 'SENT' && <section className="status-action-bar"><div><MailCheck /><span><strong>Esperando decisión</strong><small>Registra la respuesta del cliente.</small></span></div><div className="page-actions"><button className="button button--danger" disabled={busy} onClick={() => void changeStatus('REJECTED')}><X size={18} /> Rechazada</button><button className="button button--accent" disabled={busy} onClick={() => void changeStatus('ACCEPTED')}><Check size={18} /> Aceptada</button></div></section>}
      {quote.status === 'ACCEPTED' && <section className="status-action-bar"><div><Factory /><span><strong>Producción</strong><small>{!orderChecked ? 'Consultando orden…' : order ? `${order.orderNumber} ya está disponible.` : productionError || 'La cotización está lista para convertirse.'}</small></span></div>{order ? <Link className="button button--accent" to={`/production/${order.id}`}>Ver orden</Link> : orderChecked && !productionError ? <button className="button button--accent" disabled={busy} onClick={() => void convertToProduction()}>{busy ? 'Convirtiendo…' : 'Convertir a producción'}</button> : productionError ? <button className="button button--secondary" onClick={() => void loadOrder(id)}>Reintentar</button> : null}</section>}
      {(quote.sentAt || quote.acceptedAt || quote.rejectedAt) && <section className="commercial-timeline" aria-label="Historial comercial"><h2>Historial comercial</h2><div><span>Creada <strong>{formatDate(quote.createdAt, true)}</strong></span>{quote.sentAt && <span>Enviada <strong>{formatDate(quote.sentAt, true)}</strong></span>}{quote.acceptedAt && <span>Aceptada <strong>{formatDate(quote.acceptedAt, true)}</strong></span>}{quote.rejectedAt && <span>Rechazada <strong>{formatDate(quote.rejectedAt, true)}</strong></span>}</div></section>}
    </div>
  )
}
