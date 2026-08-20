import { Clock3, Pencil, Plus, Printer } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { printersApi } from '../api'
import { ApiError } from '../api/http'
import { EmptyState, ErrorState, LoadingState } from '../components/Feedback'
import { FormField } from '../components/FormField'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { useToast } from '../context/ToastContext'
import type { Printer as PrinterType, PrinterRequest } from '../types'
import { currency, formatDate } from '../utils/format'

export function PrintersPage() {
  const [items, setItems] = useState<PrinterType[] | null>(null)
  const [editing, setEditing] = useState<PrinterType | 'new' | null>(null)
  const [form, setForm] = useState<PrinterRequest>({ name: '', model: null, costPerHour: 0, active: true })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState<number | null>(null)
  const [saving, setSaving] = useState(false)
  const [reload, setReload] = useState(0)
  const toast = useToast()

  useEffect(() => {
    let active = true
    printersApi.list().then((response) => { if (active) setItems(response) }).catch((caught: unknown) => { if (active) setError(caught instanceof ApiError ? caught.message : 'No fue posible cargar impresoras.') })
    return () => { active = false }
  }, [reload])

  function open(item?: PrinterType) {
    setEditing(item || 'new')
    setForm(item ? { name: item.name, model: item.model ?? null, costPerHour: item.costPerHour, active: item.active } : { name: '', model: null, costPerHour: 0, active: true })
    setErrors({})
  }

  async function submit(event: FormEvent) {
    event.preventDefault(); if (saving) return; setSaving(true); setErrors({})
    try {
      const payload = { ...form, model: form.model?.trim() || null }
      const response = editing === 'new' ? await printersApi.create(payload) : await printersApi.update(editing!.id, payload)
      setItems((current) => editing === 'new' ? [response, ...(current || [])] : (current || []).map((item) => item.id === response.id ? response : item))
      toast.success(editing === 'new' ? 'Impresora agregada.' : 'Impresora actualizada.'); setEditing(null)
    } catch (caught) { if (caught instanceof ApiError) { setErrors(caught.errors); if (!Object.keys(caught.errors).length) toast.error(caught.message) } else toast.error('No fue posible guardar la impresora.') } finally { setSaving(false) }
  }

  async function toggle(item: PrinterType) {
    if (busyId !== null) return; setBusyId(item.id)
    try { const response = await printersApi.setActive(item.id, !item.active); setItems((current) => (current || []).map((value) => value.id === item.id ? response : value)); toast.success(response.active ? 'Impresora activada.' : 'Impresora archivada.') } catch (caught) { toast.error(caught instanceof ApiError ? caught.message : 'No fue posible cambiar el estado.') } finally { setBusyId(null) }
  }

  if (!items && !error) return <LoadingState />
  if (error) return <ErrorState message={error} retry={() => { setError(''); setReload((value) => value + 1) }} />
  if (!items) return null

  return (
    <div className="page">
      <PageHeader eyebrow="Capacidad instalada" title="Impresoras" description="Costo operativo por hora de cada máquina del taller." actions={<button className="button button--accent" onClick={() => open()}><Plus size={18} /> Nueva impresora</button>} />
      <div className="info-band"><Clock3 size={20} /><p><strong>El costo por hora también queda congelado por pieza.</strong> Editar una máquina actualiza trabajos nuevos; las instantáneas existentes se conservan incluso al recalcular.</p></div>
      {items.length === 0 ? <EmptyState icon={<Printer />} title="Sin impresoras" description="Registra una máquina activa para calcular tiempo de producción." action={<button className="button button--primary" onClick={() => open()}>Agregar impresora</button>} /> : <section className="catalog-grid">{items.map((item) => <article className={`catalog-card printer-card ${item.active ? '' : 'is-inactive'}`} key={item.id}><header><span className="catalog-icon"><Printer /></span><span className={`availability ${item.active ? 'active' : ''}`}>{item.active ? 'Operativa' : 'Archivada'}</span></header><h2>{item.name}</h2><p className="model-name">{item.model || 'Modelo no especificado'}</p><div className="catalog-price"><strong>{currency.format(item.costPerHour)}</strong><span>/ hora</span></div><p>Registrada {formatDate(item.createdAt)}</p><footer><button className="button button--secondary button--compact" onClick={() => open(item)}><Pencil size={16} /> Editar</button><button className="toggle-button" onClick={() => void toggle(item)} disabled={busyId !== null} aria-label={`${item.active ? 'Archivar' : 'Activar'} ${item.name}`} aria-pressed={item.active}><span />{item.active ? 'Activa' : 'Inactiva'}</button></footer></article>)}</section>}
      {editing && <Modal title={editing === 'new' ? 'Nueva impresora' : 'Editar impresora'} onClose={() => !saving && setEditing(null)}><form className="form-stack" onSubmit={submit}><FormField label="Nombre *" htmlFor="printer-name" error={errors.name}><input id="printer-name" autoFocus value={form.name} maxLength={100} onChange={(event) => { setForm({ ...form, name: event.target.value }); setErrors({ ...errors, name: '' }) }} required /></FormField><FormField label="Modelo" htmlFor="printer-model" error={errors.model}><input id="printer-model" value={form.model || ''} maxLength={100} onChange={(event) => { setForm({ ...form, model: event.target.value || null }); setErrors({ ...errors, model: '' }) }} /></FormField><FormField label="Costo operativo por hora (MXN) *" htmlFor="printer-cost" error={errors.costPerHour}><input id="printer-cost" type="number" min="0" step="0.01" value={form.costPerHour} onChange={(event) => { setForm({ ...form, costPerHour: Number(event.target.value) }); setErrors({ ...errors, costPerHour: '' }) }} required /></FormField><label className="check-row"><input type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /><span><strong>Disponible para nuevas piezas</strong><small>Las piezas existentes conservan su instantánea.</small></span></label><div className="modal-actions"><button type="button" className="button button--secondary" onClick={() => setEditing(null)} disabled={saving}>Cancelar</button><button className="button button--primary" disabled={saving}>{saving ? 'Guardando…' : 'Guardar impresora'}</button></div></form></Modal>}
    </div>
  )
}
