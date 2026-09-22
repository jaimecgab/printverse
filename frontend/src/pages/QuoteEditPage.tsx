import { ArrowLeft, Eye, Plus, RefreshCw, Save, Trash2 } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { materialsApi, printersApi, quotesApi } from '../api'
import { ApiError } from '../api/http'
import { ErrorState, LoadingState } from '../components/Feedback'
import { FormField } from '../components/FormField'
import { PieceEditor } from '../components/PieceEditor'
import { QuoteFinancials } from '../components/QuoteFinancials'
import { StatusBadge } from '../components/StatusBadge'
import { useToast } from '../context/ToastContext'
import type { ItemRequest, Material, Printer, Quote, QuoteItem, QuoteUpdateRequest } from '../types'
import { currency, formatDate, number, toDateInput } from '../utils/format'
import { useUnsavedChangesWarning } from '../utils/useUnsavedChangesWarning'

interface EditorData { quote: Quote; materials: Material[]; printers: Printer[] }

export function QuoteEditPage() {
  const id = Number(useParams().id)
  const [data, setData] = useState<EditorData | null>(null)
  const [form, setForm] = useState<QuoteUpdateRequest | null>(null)
  const [savedForm, setSavedForm] = useState<QuoteUpdateRequest | null>(null)
  const [piece, setPiece] = useState<QuoteItem | 'new' | null>(null)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)
  const [busy, setBusy] = useState(false)
  const [reload, setReload] = useState(0)
  const toast = useToast()

  useEffect(() => {
    if (!Number.isInteger(id) || id <= 0) { setError('El identificador de la cotización no es válido.'); return }
    let active = true
    async function load() {
      try {
        setError('')
        const [quote, materials, printers] = await Promise.all([quotesApi.get(id), materialsApi.list(), printersApi.list()])
        if (!active) return
        setData({ quote, materials, printers })
        const terms = { title: quote.title ?? null, validUntil: quote.validUntil, estimatedDeliveryDate: quote.estimatedDeliveryDate ?? null, depositPercentage: quote.depositPercentage ?? null, notes: quote.notes ?? null, internalNotes: quote.internalNotes ?? null, markupPercentage: quote.markupPercentage, discountPercentage: quote.discountPercentage, taxEnabled: quote.taxEnabled, taxPercentage: quote.taxPercentage }
        setForm(terms); setSavedForm(terms); setError('')
      } catch (caught) { if (active) setError(caught instanceof ApiError ? caught.message : 'No fue posible abrir el editor.') }
    }
    void load(); return () => { active = false }
  }, [id, reload])

  const termsDirty = Boolean(form && savedForm && JSON.stringify(form) !== JSON.stringify(savedForm))

  useUnsavedChangesWarning(termsDirty, 'Hay cambios de las condiciones sin guardar. ¿Salir de todos modos?')

  async function refreshQuote() {
    const quote = await quotesApi.get(id)
    setData((current) => current ? { ...current, quote } : current)
    return quote
  }

  async function saveTerms(event: FormEvent) {
    event.preventDefault(); if (!form || saving) return; setSaving(true); setFieldErrors({})
    try { const payload = { ...form, title: form.title?.trim() || null, notes: form.notes?.trim() || null, internalNotes: form.internalNotes?.trim() || null }; const quote = await quotesApi.update(id, payload); setData((current) => current ? { ...current, quote } : current); setForm(payload); setSavedForm(payload); toast.success('Condiciones actualizadas con el cálculo del servidor.') } catch (caught) { if (caught instanceof ApiError) { setFieldErrors(caught.errors); toast.error(caught.message) } else toast.error('No fue posible guardar las condiciones.') } finally { setSaving(false) }
  }

  async function savePiece(payload: ItemRequest): Promise<void> {
    if (!data) return
    const response = piece === 'new' ? await quotesApi.addItem(id, payload) : await quotesApi.updateItem(id, piece!.id, payload)
    setData({ ...data, quote: response })
    toast.success(piece === 'new' ? 'Pieza y cargos agregados; cotización recalculada.' : 'Pieza y cargos actualizados; cotización recalculada.')
  }

  async function deleteItem(item: QuoteItem) {
    if (busy || !window.confirm(`¿Eliminar la pieza “${item.name}”?`)) return
    setBusy(true)
    try { await quotesApi.deleteItem(id, item.id); await refreshQuote(); toast.success('Pieza eliminada; cálculo actualizado.') } catch (caught) { toast.error(caught instanceof ApiError ? caught.message : 'No fue posible eliminar la pieza.') } finally { setBusy(false) }
  }

  async function recalculate() {
    if (busy) return; setBusy(true)
    try { const quote = await quotesApi.recalculate(id); setData((current) => current ? { ...current, quote } : current); toast.success('Cotización recalculada. Las instantáneas históricas se preservaron.') } catch (caught) { toast.error(caught instanceof ApiError ? caught.message : 'No fue posible recalcular.') } finally { setBusy(false) }
  }

  if (!data || data.quote.id !== id || !form) {
    if (error) return <ErrorState message={error} retry={() => { setError(''); setReload((value) => value + 1) }} />
    return <LoadingState label="Preparando editor y recursos activos…" />
  }

  const { quote, materials, printers } = data
  if (quote.status !== 'DRAFT') return <div className="page page--narrow"><Link to={`/quotes/${id}`} className="back-link"><ArrowLeft size={17} /> Volver al detalle</Link><div className="locked-panel"><StatusBadge status={quote.status} /><h1>Esta cotización ya no es editable</h1><p>Los términos y costos se congelan después de marcarla como enviada.</p><Link to={`/quotes/${id}`} className="button button--primary">Ver cotización</Link></div></div>

  const canAddPieces = materials.some((item) => item.active) && printers.some((item) => item.active)

  return (
    <div className="page quote-editor-page">
      <div className="editor-topbar"><div><Link to={`/quotes/${id}`} className="back-link"><ArrowLeft size={17} /> Salir del editor</Link><div className="document-title"><h1>{quote.quoteNumber}</h1><StatusBadge status={quote.status} /></div><p>{quote.customer.name} · creada {formatDate(quote.createdAt)}</p></div><div className="page-actions"><button className="button button--secondary" disabled={busy} onClick={() => void recalculate()}><RefreshCw size={17} className={busy ? 'spin' : ''} /> Recalcular</button><Link to={`/quotes/${id}`} className="button button--primary"><Eye size={17} /> Vista final</Link></div></div>

      <div className="editor-layout">
        <div className="editor-main">
          <form className="panel quote-terms-form" onSubmit={saveTerms}>
            <div className="section-heading"><div><p className="eyebrow">Condiciones</p><h2>Datos generales</h2>{termsDirty && <span className="unsaved-indicator" role="status">Cambios sin guardar</span>}</div><button className="button button--secondary button--compact" disabled={saving || !termsDirty}><Save size={16} /> {saving ? 'Guardando…' : 'Guardar condiciones'}</button></div>
            <FormField label="Título del proyecto" htmlFor="edit-title" error={fieldErrors.title}><input id="edit-title" maxLength={200} value={form.title || ''} onChange={(event) => setForm({ ...form, title: event.target.value || null })} placeholder="Ej. Trofeos para torneo de verano" /></FormField>
            <div className="form-grid form-grid--2"><FormField label="Válida hasta *" htmlFor="edit-valid" error={fieldErrors.validUntil}><input id="edit-valid" type="date" min={toDateInput(new Date())} required value={form.validUntil} onChange={(event) => setForm({ ...form, validUntil: event.target.value })} /></FormField><FormField label="Entrega estimada" htmlFor="edit-delivery" error={fieldErrors.estimatedDeliveryDate}><input id="edit-delivery" type="date" min={toDateInput(new Date())} value={form.estimatedDeliveryDate || ''} onChange={(event) => setForm({ ...form, estimatedDeliveryDate: event.target.value || null })} /></FormField><FormField label="Anticipo (%)" htmlFor="edit-deposit" error={fieldErrors.depositPercentage}><input id="edit-deposit" type="number" min="0" max="100" step="0.0001" value={form.depositPercentage ?? ''} onChange={(event) => setForm({ ...form, depositPercentage: event.target.value === '' ? null : Number(event.target.value) })} /></FormField><FormField label="Markup sobre costo (%) *" htmlFor="edit-markup" error={fieldErrors.markupPercentage}><input id="edit-markup" type="number" min="0" step="0.0001" required value={form.markupPercentage} onChange={(event) => setForm({ ...form, markupPercentage: Number(event.target.value) })} /></FormField><FormField label="Descuento (%) *" htmlFor="edit-discount" error={fieldErrors.discountPercentage}><input id="edit-discount" type="number" min="0" max="100" step="0.0001" required value={form.discountPercentage} onChange={(event) => setForm({ ...form, discountPercentage: Number(event.target.value) })} /></FormField><FormField label="IVA (%) *" htmlFor="edit-tax" error={fieldErrors.taxPercentage}><input id="edit-tax" type="number" min="0" max="100" step="0.0001" required disabled={!form.taxEnabled} value={form.taxPercentage} onChange={(event) => setForm({ ...form, taxPercentage: Number(event.target.value) })} /></FormField></div>
            <label className="check-row"><input type="checkbox" checked={form.taxEnabled} onChange={(event) => setForm({ ...form, taxEnabled: event.target.checked })} /><span><strong>Aplicar IVA</strong><small>Calculado después del descuento.</small></span></label>
            <div className="form-grid form-grid--2"><FormField label="Notas para el cliente" htmlFor="edit-notes" error={fieldErrors.notes} hint="Se incluyen en el documento comercial."><textarea id="edit-notes" rows={5} maxLength={3000} value={form.notes || ''} onChange={(event) => setForm({ ...form, notes: event.target.value || null })} /></FormField><FormField label="Notas internas" htmlFor="edit-internal-notes" error={fieldErrors.internalNotes} hint="Sólo visibles para el equipo."><textarea id="edit-internal-notes" rows={5} maxLength={3000} value={form.internalNotes || ''} onChange={(event) => setForm({ ...form, internalNotes: event.target.value || null })} /></FormField></div>
          </form>

          <section className="panel pieces-panel">
            <div className="section-heading"><div><p className="eyebrow">Producción</p><h2>Piezas del proyecto</h2><p>Agrega una o múltiples piezas; cada una recalcula el total.</p></div><button className="button button--accent button--compact" disabled={!canAddPieces} onClick={() => setPiece('new')}><Plus size={17} /> Agregar pieza</button></div>
            {!canAddPieces && <div className="inline-error">Necesitas al menos un material y una impresora activos para crear piezas nuevas.</div>}
            {quote.items.length === 0 ? <button className="add-piece-empty" disabled={!canAddPieces} onClick={() => setPiece('new')}><Plus /><strong>Agrega la primera pieza</strong><span>Configura material, máquina, tiempo, riesgo y extras.</span></button> : <div className="editor-item-list">{quote.items.map((item, index) => <article className="editor-item" key={item.id}><span className="item-index">{String(index + 1).padStart(2, '0')}</span><div className="editor-item-main"><h3>{item.name}</h3><p>{item.quantity} u. · {item.material.name} · {item.printer.name}</p><div><span>Costo u. <b>{currency.format(item.internalCostUnit)}</b></span><span>Precio u. <b>{currency.format(item.finalUnitPrice)}</b></span><span>Ganancia antes de descuento u. <b>{currency.format(item.finalUnitPrice - item.internalCostUnit)}</b></span></div></div><div className="editor-item-price"><span>Subtotal</span><strong>{currency.format(item.itemSubtotal)}</strong><small>{item.additionalCharges.length} {item.additionalCharges.length === 1 ? 'cargo' : 'cargos'}</small></div><div className="editor-item-actions"><button className="button button--secondary button--compact" onClick={() => setPiece(item)}>Editar</button><button className="icon-button icon-button--danger" disabled={busy} onClick={() => void deleteItem(item)} aria-label={`Eliminar ${item.name}`}><Trash2 size={17} /></button></div></article>)}</div>}
          </section>
        </div>

        <aside className="editor-summary"><p className="eyebrow">Cálculo vigente</p><h2>Resumen autoritativo</h2><p>Última respuesta recibida del backend.</p><dl><div><dt>Costo interno</dt><dd>{currency.format(quote.internalCost)}</dd></div><div><dt>Subtotal sugerido</dt><dd>{currency.format(quote.suggestedSubtotal)}</dd></div><div><dt>Subtotal final</dt><dd>{currency.format(quote.finalSubtotal)}</dd></div><div><dt>Descuento</dt><dd>− {currency.format(quote.discountAmount)}</dd></div><div><dt>IVA</dt><dd>{currency.format(quote.taxAmount)}</dd></div><div className="summary-total"><dt>Total</dt><dd>{currency.format(quote.total)}</dd></div></dl><div className="profit-box"><span>Ganancia estimada</span><strong>{currency.format(quote.estimatedProfit)}</strong><small>Margen real {number.format(quote.realMarginPercentage)}%</small></div><small className="snapshot-note">Recalcular conserva las instantáneas de material y máquina de cada pieza.</small></aside>
      </div>
      <div className="mobile-financials"><QuoteFinancials quote={quote} /></div>
      {piece && <PieceEditor key={piece === 'new' ? 'new' : piece.id} item={piece === 'new' ? undefined : piece} materials={materials} printers={printers} onClose={() => setPiece(null)} onSave={savePiece} />}
    </div>
  )
}
