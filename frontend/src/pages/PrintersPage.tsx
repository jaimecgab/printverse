import { Clock3, Pencil, Plus, Printer } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { printersApi } from '../api'
import { ApiError } from '../api/http'
import { EmptyState, ErrorState, LoadingState } from '../components/Feedback'
import { FormField } from '../components/FormField'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'
import type { Printer as PrinterType, PrinterRequest } from '../types'
import { currency, formatDate } from '../utils/format'
import { manuallyManagedPrinterStatuses, printerOperationalStatusLabels } from '../utils/production'

const emptyPrinter: PrinterRequest = {
  name: '',
  model: null,
  costPerHour: 0,
  operationalStatus: 'AVAILABLE',
  notes: null,
  active: true,
}

function isBusyStateConflict(detail: string) {
  return detail === 'BUSY is managed by production and cannot be set manually'
    || detail.startsWith('A BUSY printer ')
}

export function PrintersPage() {
  const [items, setItems] = useState<PrinterType[] | null>(null)
  const [editing, setEditing] = useState<PrinterType | 'new' | null>(null)
  const [form, setForm] = useState<PrinterRequest>(emptyPrinter)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState<number | null>(null)
  const [saving, setSaving] = useState(false)
  const [reload, setReload] = useState(0)
  const toast = useToast()
  const { isAdmin } = useAuth()

  useEffect(() => {
    let active = true
    printersApi.list().then((response) => { if (active) setItems(response) }).catch((caught: unknown) => { if (active) setError(caught instanceof ApiError ? caught.message : 'No fue posible cargar impresoras.') })
    return () => { active = false }
  }, [reload])

  function open(item?: PrinterType) {
    setEditing(item || 'new')
    setForm(item ? {
      name: item.name,
      model: item.model,
      costPerHour: item.costPerHour,
      operationalStatus: item.operationalStatus,
      notes: item.notes,
      active: item.active,
    } : emptyPrinter)
    setErrors({})
  }

  async function submit(event: FormEvent) {
    event.preventDefault(); if (saving) return; setSaving(true); setErrors({})
    try {
      const payload = { ...form, model: form.model?.trim() || null, notes: form.notes?.trim() || null }
      const response = editing === 'new' ? await printersApi.create(payload) : await printersApi.update(editing!.id, payload)
      setItems((current) => editing === 'new' ? [response, ...(current || [])] : (current || []).map((item) => item.id === response.id ? response : item))
      toast.success(editing === 'new' ? 'Impresora agregada.' : 'Impresora actualizada.'); setEditing(null)
    } catch (caught) {
      if (caught instanceof ApiError) {
        setErrors(caught.errors)
        if (!Object.keys(caught.errors).length) toast.error(caught.message)
        if (editing !== 'new' && isBusyStateConflict(caught.detail)) {
          try {
            setItems(await printersApi.list())
            setEditing(null)
          } catch {
            // Keep the original concurrency error visible if the refresh also fails.
          }
        }
      } else toast.error('No fue posible guardar la impresora.')
    } finally { setSaving(false) }
  }

  async function toggle(item: PrinterType) {
    if (busyId !== null || item.operationalStatus === 'BUSY') return; setBusyId(item.id)
    try { const response = await printersApi.setActive(item.id, !item.active); setItems((current) => (current || []).map((value) => value.id === item.id ? response : value)); toast.success(response.active ? 'Impresora agregada al catálogo.' : 'Impresora archivada.') }
    catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : 'No fue posible cambiar el estado.')
      if (caught instanceof ApiError && isBusyStateConflict(caught.detail)) {
        try { setItems(await printersApi.list()) } catch { /* Keep the original concurrency error. */ }
      }
    } finally { setBusyId(null) }
  }

  if (!items && !error) return <LoadingState />
  if (error) return <ErrorState message={error} retry={() => { setError(''); setReload((value) => value + 1) }} />
  if (!items) return null

  return (
    <div className="page">
      <PageHeader eyebrow="Capacidad instalada" title="Impresoras" description="Costo operativo por hora de cada máquina del taller." actions={isAdmin ? <button className="button button--accent" onClick={() => open()}><Plus size={18} /> Nueva impresora</button> : undefined} />
      {!isAdmin && <div className="inline-notice catalog-permission-note">Puedes consultar el catálogo. La edición está reservada a administradores.</div>}
      <div className="info-band"><Clock3 size={20} /><p><strong>El costo por hora también queda congelado por pieza.</strong> Editar una máquina actualiza trabajos nuevos; las instantáneas existentes se conservan incluso al recalcular.</p></div>
      {items.length === 0 ? <EmptyState icon={<Printer />} title="Sin impresoras" description="Un administrador debe registrar una máquina activa para calcular tiempo de producción." action={isAdmin ? <button className="button button--primary" onClick={() => open()}>Agregar impresora</button> : undefined} /> : (
        <section className="catalog-grid">{items.map((item) => (
          <article className={`catalog-card printer-card ${item.active ? '' : 'is-inactive'}`} key={item.id}>
            <header><span className="catalog-icon"><Printer /></span><div className="catalog-badges"><span className={`operational-status operational-status--${item.operationalStatus.toLowerCase()}`}>{printerOperationalStatusLabels[item.operationalStatus]}</span><span className={`availability ${item.active ? 'active' : ''}`}>{item.active ? 'En catálogo' : 'Archivada'}</span></div></header>
            <h2>{item.name}</h2>
            <p className="model-name">{item.model || 'Modelo no especificado'}</p>
            <div className="catalog-price"><strong>{currency.format(item.costPerHour)}</strong><span>/ hora</span></div>
            {item.notes && <p className="catalog-notes">{item.notes}</p>}
            <p>Registrada {formatDate(item.createdAt)}</p>
            {isAdmin && <footer><button className="button button--secondary button--compact" onClick={() => open(item)}><Pencil size={16} /> Editar</button><button className="toggle-button" onClick={() => void toggle(item)} disabled={busyId !== null || item.operationalStatus === 'BUSY'} title={item.operationalStatus === 'BUSY' ? 'La ocupación y disponibilidad se gestionan desde producción.' : undefined} aria-label={`${item.active ? 'Archivar' : 'Agregar al catálogo'} ${item.name}`} aria-pressed={item.active}><span />{item.active ? 'En catálogo' : 'Archivada'}</button></footer>}
          </article>
        ))}</section>
      )}
      {isAdmin && editing && <Modal title={editing === 'new' ? 'Nueva impresora' : 'Editar impresora'} onClose={() => !saving && setEditing(null)}>
        <form className="form-stack" onSubmit={submit}>
          <div className="form-grid form-grid--2">
            <FormField label="Nombre *" htmlFor="printer-name" error={errors.name}><input id="printer-name" autoFocus value={form.name} maxLength={100} onChange={(event) => { setForm({ ...form, name: event.target.value }); setErrors({ ...errors, name: '' }) }} required /></FormField>
            <FormField label="Modelo" htmlFor="printer-model" error={errors.model}><input id="printer-model" value={form.model || ''} maxLength={100} onChange={(event) => { setForm({ ...form, model: event.target.value || null }); setErrors({ ...errors, model: '' }) }} /></FormField>
            <FormField label="Costo operativo por hora (MXN) *" htmlFor="printer-cost" error={errors.costPerHour}><input id="printer-cost" type="number" min="0" step="0.01" value={form.costPerHour} onChange={(event) => { setForm({ ...form, costPerHour: Number(event.target.value) }); setErrors({ ...errors, costPerHour: '' }) }} required /></FormField>
            <FormField label="Estado operativo *" htmlFor="printer-operational-status" error={errors.operationalStatus} hint={editing !== 'new' && editing.operationalStatus === 'BUSY' ? 'Ocupada se gestiona desde producción.' : 'El estado Ocupada se asigna automáticamente desde producción.'}><select id="printer-operational-status" value={form.operationalStatus} disabled={editing !== 'new' && editing.operationalStatus === 'BUSY'} onChange={(event) => { setForm({ ...form, operationalStatus: event.target.value as PrinterRequest['operationalStatus'] }); setErrors({ ...errors, operationalStatus: '' }) }}>{editing !== 'new' && editing.operationalStatus === 'BUSY' && <option value="BUSY">{printerOperationalStatusLabels.BUSY}</option>}{manuallyManagedPrinterStatuses.map((status) => <option key={status} value={status}>{printerOperationalStatusLabels[status]}</option>)}</select></FormField>
          </div>
          <FormField label="Notas" htmlFor="printer-notes" error={errors.notes}><textarea id="printer-notes" rows={3} maxLength={1000} value={form.notes || ''} placeholder="Configuración, restricciones o contexto operativo…" onChange={(event) => setForm({ ...form, notes: event.target.value || null })} /></FormField>
          <label className="check-row"><input type="checkbox" checked={form.active} disabled={editing !== 'new' && editing.operationalStatus === 'BUSY'} onChange={(event) => setForm({ ...form, active: event.target.checked })} /><span><strong>En catálogo</strong><small>{editing !== 'new' && editing.operationalStatus === 'BUSY' ? 'No puede archivarse mientras producción la mantiene ocupada.' : 'Archivar no cambia su estado operativo ni las piezas existentes.'}</small></span></label>
          <div className="modal-actions"><button type="button" className="button button--secondary" onClick={() => setEditing(null)} disabled={saving}>Cancelar</button><button className="button button--primary" disabled={saving}>{saving ? 'Guardando…' : 'Guardar impresora'}</button></div>
        </form>
      </Modal>}
    </div>
  )
}
