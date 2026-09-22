import { ArrowLeft, CalendarDays, CheckCircle2, Factory, Printer as PrinterIcon, Save } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { printersApi, productionApi } from '../api'
import { ApiError } from '../api/http'
import { ErrorState, LoadingState } from '../components/Feedback'
import { FormField } from '../components/FormField'
import { Modal } from '../components/Modal'
import { ProductionItemStatusBadge, ProductionStatusBadge } from '../components/ProductionStatusBadge'
import { useToast } from '../context/ToastContext'
import type { Printer, ProductionItemStatus, ProductionItemUpdateRequest, ProductionOrder, ProductionOrderItem, ProductionOrderStatus } from '../types'
import { formatDate, number } from '../utils/format'
import { assignablePrinters, availableItemTransitions, availableOrderTransitions, normalizedCompletedQuantity, orderProgress, printerOperationalStatusLabels, productionItemStatusLabels, productionPriorityLabels, productionStatusLabels } from '../utils/production'

interface PageData { order: ProductionOrder; printers: Printer[] }

export function ProductionOrderPage() {
  const id = Number(useParams().id)
  const [data, setData] = useState<PageData | null>(null)
  const [editing, setEditing] = useState<ProductionOrderItem | null>(null)
  const [openingItemId, setOpeningItemId] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [reload, setReload] = useState(0)
  const printerRefreshId = useRef(0)
  const toast = useToast()

  useEffect(() => {
    if (!Number.isInteger(id) || id <= 0) { setError('El identificador de la orden no es válido.'); return }
    let active = true
    async function load() {
      try { setError(''); const [order, printers] = await Promise.all([productionApi.get(id), printersApi.list()]); if (active) setData({ order, printers }) }
      catch (caught) { if (active) setError(caught instanceof ApiError ? caught.message : 'No fue posible abrir la orden.') }
    }
    void load(); return () => { active = false }
  }, [id, reload])

  async function changeStatus(status: ProductionOrderStatus) {
    if (!data || busy || !window.confirm(`¿Cambiar la orden a “${productionStatusLabels[status]}”?`)) return
    setBusy(true)
    try { const order = await productionApi.setStatus(id, status); setData((current) => current ? { ...current, order } : current); toast.success(`Orden actualizada: ${productionStatusLabels[status]}.`) }
    catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : 'No fue posible actualizar la orden.')
      try { await refreshProductionData() } catch { /* Keep the original operation error. */ }
    } finally { setBusy(false) }
  }

  async function openItemEditor(item: ProductionOrderItem) {
    if (item.status === 'COMPLETED' || openingItemId !== null) return
    setOpeningItemId(item.id)
    try {
      const order = await refreshProductionData()
      const currentItem = order.items.find((value) => value.id === item.id)
      if (!currentItem || currentItem.status === 'COMPLETED'
          || order.status !== 'PENDING' && order.status !== 'IN_PRODUCTION') {
        toast.error('La orden cambió mientras estaba abierta. Se actualizaron sus datos.')
        return
      }
      setEditing(currentItem)
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : 'No fue posible actualizar la disponibilidad de impresoras.')
    } finally { setOpeningItemId(null) }
  }

  async function refreshProductionData() {
    const refreshId = ++printerRefreshId.current
    const [order, printers] = await Promise.all([productionApi.get(id), printersApi.list()])
    if (refreshId === printerRefreshId.current) {
      setData((current) => current?.order.id === id ? { order, printers } : current)
    }
    return order
  }

  async function refreshPrinters() {
    const refreshId = ++printerRefreshId.current
    const printers = await printersApi.list()
    if (refreshId === printerRefreshId.current) {
      setData((current) => current?.order.id === id ? { ...current, printers } : current)
    }
  }

  async function saveItem(itemId: number, payload: ProductionItemUpdateRequest) {
    let order: ProductionOrder
    try {
      order = await productionApi.updateItem(id, itemId, payload)
    } catch (caught) {
      if (!(caught instanceof ApiError)) throw caught
      if (!caught.detail.startsWith('Production order item changed after it was loaded')) {
        try {
          const currentOrder = await refreshProductionData()
          const currentItem = currentOrder.items.find((item) => item.id === itemId)
          if (!currentItem || currentItem.status === 'COMPLETED'
              || currentItem.version !== payload.expectedVersion
              || currentOrder.status !== 'PENDING' && currentOrder.status !== 'IN_PRODUCTION') {
            setEditing(null)
            toast.error(caught.message)
            return
          }
        } catch { /* Preserve the form and the original operation error. */ }
        throw caught
      }
      try {
        await refreshProductionData()
      } catch {
        setEditing(null)
        setData(null)
        setError('La pieza cambió en otra sesión y no fue posible recargar la orden.')
        return
      }
      setEditing(null)
      toast.error(caught.message)
      return
    }
    setData((current) => current?.order.id === id ? { ...current, order } : current)
    setEditing(null)
    toast.success('Pieza de producción actualizada.')
    try {
      await refreshPrinters()
    } catch {
      toast.error('La pieza se actualizó, pero no fue posible refrescar la disponibilidad de impresoras.')
    }
  }

  if ((!data || data.order.id !== id) && !error) return <LoadingState label="Abriendo orden y recursos del taller…" />
  if (error) return <ErrorState message={error} retry={() => { setError(''); setData(null); setReload((value) => value + 1) }} />
  if (!data) return null

  const { order, printers } = data
  const progress = orderProgress(order)
  const transitions = availableOrderTransitions(order)
  const editable = order.status === 'PENDING' || order.status === 'IN_PRODUCTION'
  const timeline = [['Creada', order.createdAt], ['Iniciada', order.startedAt], ['Lista', order.readyAt], ['Entregada', order.deliveredAt], ['Cancelada', order.cancelledAt]] as const

  return (
    <div className="page production-order-page">
      <Link to="/production" className="back-link"><ArrowLeft size={17} /> Volver a producción</Link>
      <header className="production-order-header"><div><p className="eyebrow">Orden de producción</p><div className="document-title"><h1>{order.orderNumber}</h1><ProductionStatusBadge status={order.status} /></div><p>{productionPriorityLabels[order.priority]} · actualizada {formatDate(order.updatedAt, true)}</p></div><div className="page-actions">{transitions.map((status) => <button key={status} className={`button ${status === 'CANCELLED' ? 'button--danger' : 'button--accent'}`} disabled={busy} onClick={() => void changeStatus(status)}>{status === 'IN_PRODUCTION' ? <Factory size={17} /> : <CheckCircle2 size={17} />}{productionStatusLabels[status]}</button>)}</div></header>

      <section className="production-overview">
        <div><span className="meta-label">Cliente y cotización</span><strong>{order.quote.customer.name}</strong><Link to={`/quotes/${order.quote.id}`}>{order.quote.quoteNumber}{order.quote.title ? ` · ${order.quote.title}` : ''}</Link><a href={`tel:${order.quote.customer.phone}`}>{order.quote.customer.phone}</a></div>
        <div><span className="meta-label">Compromiso</span><strong><CalendarDays size={17} /> {formatDate(order.dueDate)}</strong><small>{order.notes || 'Sin notas generales de producción.'}</small></div>
        <div className="production-progress-summary"><span className="meta-label">Progreso autoritativo</span><strong>{progress}%</strong><div className="progress-track"><i style={{ width: `${progress}%` }} /></div><small>{order.items.reduce((sum, item) => sum + item.completedQuantity, 0)} de {order.items.reduce((sum, item) => sum + item.quantity, 0)} unidades</small></div>
      </section>

      <section className="production-timeline" aria-label="Línea de tiempo de producción">{timeline.map(([label, value]) => <div className={value ? 'complete' : ''} key={label}><i /><span>{label}</span><strong>{value ? formatDate(value, true) : 'Pendiente'}</strong></div>)}</section>

      <section className="panel production-items-panel"><div className="section-heading"><div><p className="eyebrow">Ejecución</p><h2>Piezas de la orden</h2><p>{editable ? 'Actualiza asignación, avance y bloqueos por pieza.' : 'La orden está cerrada para edición de piezas.'}</p></div></div><div className="production-item-list">{order.items.map((item) => <article className="production-item" key={item.id}><div><div className="production-item-heading"><h3>{item.name}</h3><ProductionItemStatusBadge status={item.status} /></div><p>{item.materialName} · referencia: {item.printerName}{item.printerModel ? ` · ${item.printerModel}` : ''}</p><div className="production-item-facts"><span><PrinterIcon size={15} /> {item.assignedPrinter ? `${item.assignedPrinter.name}${item.assignedPrinter.model ? ` · ${item.assignedPrinter.model}` : ''}` : 'Sin impresora asignada'}</span><span>{number.format(item.weightGrams)} g c/u</span><span>{Math.floor(item.printTimeMinutes / 60)} h {item.printTimeMinutes % 60} min c/u</span></div>{item.notes && <p className="production-item-notes">{item.notes}</p>}</div><div className="item-progress"><strong>{item.completedQuantity} / {item.quantity}</strong><span>unidades</span><div className="progress-track"><i style={{ width: `${item.quantity ? item.completedQuantity / item.quantity * 100 : 0}%` }} /></div></div><button className="button button--secondary button--compact" disabled={!editable || busy || openingItemId !== null || item.status === 'COMPLETED'} onClick={() => void openItemEditor(item)}>{openingItemId === item.id ? 'Actualizando…' : item.status === 'COMPLETED' ? 'Pieza completa' : 'Editar pieza'}</button></article>)}</div></section>

      {editing && <ProductionItemEditor item={editing} orderStatus={order.status} printers={printers} onClose={() => setEditing(null)} onSave={(payload) => saveItem(editing.id, payload)} />}
    </div>
  )
}

function ProductionItemEditor({ item, orderStatus, printers, onClose, onSave }: { item: ProductionOrderItem; orderStatus: ProductionOrderStatus; printers: Printer[]; onClose: () => void; onSave: (payload: ProductionItemUpdateRequest) => Promise<void> }) {
  const [printerId, setPrinterId] = useState(item.assignedPrinter?.id || 0)
  const [status, setStatus] = useState(item.status)
  const [completed, setCompleted] = useState(item.completedQuantity)
  const [notes, setNotes] = useState(item.notes || '')
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const availablePrinters = assignablePrinters(printers, item.assignedPrinter?.id)
  const printerLocked = item.status === 'IN_PROGRESS'
  const statuses = availableItemTransitions(item.status).filter((value) => value !== 'IN_PROGRESS' || orderStatus === 'IN_PRODUCTION' || item.status === 'IN_PROGRESS')

  function setItemStatus(value: ProductionItemStatus) { setStatus(value); setCompleted(normalizedCompletedQuantity(value, item.quantity, completed)); setMessage(''); setErrors((current) => ({ ...current, status: '', completedQuantity: '' })) }
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (saving) return
    if (status === 'IN_PROGRESS' && !printerId) { setErrors((current) => ({ ...current, assignedPrinterId: 'Selecciona una impresora antes de iniciar la pieza.' })); setMessage('La pieza necesita una impresora para quedar en proceso.'); return }
    const selectedPrinter = printers.find((printer) => printer.id === printerId)
    if (status === 'IN_PROGRESS' && item.status !== 'IN_PROGRESS' && selectedPrinter?.operationalStatus !== 'AVAILABLE') { setErrors((current) => ({ ...current, assignedPrinterId: 'Selecciona una impresora disponible para iniciar la pieza.' })); setMessage('Una impresora ocupada puede quedar asignada en cola, pero no iniciar otra pieza.'); return }
    if (completed < 0 || completed > item.quantity) { setMessage(`La cantidad debe estar entre 0 y ${item.quantity}.`); return }
    if (status === 'PENDING' && completed !== 0 || status === 'COMPLETED' && completed !== item.quantity || status !== 'COMPLETED' && completed === item.quantity) { setMessage('El estado y la cantidad completada no son coherentes.'); return }
    setSaving(true); setMessage(''); setErrors({})
    try { await onSave({ ...(printerId && printerId !== item.assignedPrinter?.id ? { assignedPrinterId: printerId } : {}), status, completedQuantity: completed, notes, expectedVersion: item.version }) }
    catch (caught) { if (caught instanceof ApiError) setErrors(caught.errors); setMessage(caught instanceof ApiError ? caught.message : 'No fue posible guardar la pieza.'); setSaving(false) }
  }

  return <Modal title={item.name} description="La cantidad y el estado deben representar el mismo avance real." onClose={() => !saving && onClose()}><form className="form-stack" onSubmit={submit}>{message && <div className="inline-error" role="alert">{message}</div>}<FormField label="Impresora asignada" htmlFor="production-printer" error={errors.assignedPrinterId} hint={printerLocked ? 'Bloquea la pieza y guarda antes de cambiar de impresora.' : 'Disponible u ocupada admiten asignación; otras condiciones sólo conservan la asignación actual.'}><select id="production-printer" value={printerId} disabled={printerLocked} onChange={(event) => { setPrinterId(Number(event.target.value)); setMessage(''); setErrors((current) => ({ ...current, assignedPrinterId: '' })) }}>{!item.assignedPrinter && <option value={0}>Sin asignar</option>}{availablePrinters.map((printer) => <option key={printer.id} value={printer.id}>{printer.name}{printer.model ? ` · ${printer.model}` : ''} · {printerOperationalStatusLabels[printer.operationalStatus]}{!printer.active ? ' (asignación actual archivada)' : printer.operationalStatus === 'MAINTENANCE' || printer.operationalStatus === 'OUT_OF_SERVICE' ? ' (asignación actual)' : ''}</option>)}</select></FormField><div className="form-grid form-grid--2"><FormField label="Estado" htmlFor="production-item-status" error={errors.status}><select id="production-item-status" value={status} onChange={(event) => setItemStatus(event.target.value as ProductionItemStatus)}>{statuses.map((value) => <option key={value} value={value}>{productionItemStatusLabels[value]}</option>)}</select></FormField><FormField label={`Cantidad completada (máx. ${item.quantity})`} htmlFor="production-completed" error={errors.completedQuantity}><input id="production-completed" type="number" min="0" max={item.quantity} step="1" value={completed} disabled={status === 'PENDING' || status === 'COMPLETED'} onChange={(event) => { setCompleted(Number(event.target.value)); setMessage(''); setErrors((current) => ({ ...current, completedQuantity: '' })) }} /></FormField></div><FormField label="Notas de producción" htmlFor="production-item-notes" error={errors.notes}><textarea id="production-item-notes" rows={4} maxLength={2000} value={notes} onChange={(event) => { setNotes(event.target.value); setErrors((current) => ({ ...current, notes: '' })) }} placeholder="Bloqueo, color, reimpresión o indicaciones…" /></FormField><div className="modal-actions"><button type="button" className="button button--secondary" disabled={saving} onClick={onClose}>Cancelar</button><button className="button button--primary" disabled={saving}><Save size={17} /> {saving ? 'Guardando…' : 'Guardar avance'}</button></div></form></Modal>
}
