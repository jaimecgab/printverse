import { Archive, Package, Pencil, Plus } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { materialsApi } from '../api'
import { ApiError } from '../api/http'
import { EmptyState, ErrorState, LoadingState } from '../components/Feedback'
import { FormField } from '../components/FormField'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { useToast } from '../context/ToastContext'
import type { Material, MaterialRequest } from '../types'
import { currency, formatDate } from '../utils/format'

export function MaterialsPage() {
  const [items, setItems] = useState<Material[] | null>(null)
  const [editing, setEditing] = useState<Material | 'new' | null>(null)
  const [form, setForm] = useState<MaterialRequest>({ name: '', pricePerKg: 0, active: true })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState<number | null>(null)
  const [saving, setSaving] = useState(false)
  const [reload, setReload] = useState(0)
  const toast = useToast()

  useEffect(() => {
    let active = true
    materialsApi.list().then((response) => { if (active) setItems(response) }).catch((caught: unknown) => { if (active) setError(caught instanceof ApiError ? caught.message : 'No fue posible cargar materiales.') })
    return () => { active = false }
  }, [reload])

  function open(item?: Material) {
    setEditing(item || 'new')
    setForm(item ? { name: item.name, pricePerKg: item.pricePerKg, active: item.active } : { name: '', pricePerKg: 0, active: true })
    setErrors({})
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (saving) return
    setSaving(true); setErrors({})
    try {
      const response = editing === 'new' ? await materialsApi.create(form) : await materialsApi.update(editing!.id, form)
      setItems((current) => editing === 'new' ? [response, ...(current || [])] : (current || []).map((item) => item.id === response.id ? response : item))
      toast.success(editing === 'new' ? 'Material agregado.' : 'Material actualizado.')
      setEditing(null)
    } catch (caught) {
      if (caught instanceof ApiError) { setErrors(caught.errors); if (!Object.keys(caught.errors).length) toast.error(caught.message) } else toast.error('No fue posible guardar el material.')
    } finally { setSaving(false) }
  }

  async function toggle(item: Material) {
    if (busyId !== null) return
    setBusyId(item.id)
    try {
      const response = await materialsApi.setActive(item.id, !item.active)
      setItems((current) => (current || []).map((value) => value.id === item.id ? response : value))
      toast.success(response.active ? 'Material activado.' : 'Material archivado.')
    } catch (caught) { toast.error(caught instanceof ApiError ? caught.message : 'No fue posible cambiar el estado.') } finally { setBusyId(null) }
  }

  if (!items && !error) return <LoadingState />
  if (error) return <ErrorState message={error} retry={() => { setError(''); setReload((value) => value + 1) }} />
  if (!items) return null

  return (
    <div className="page">
      <PageHeader eyebrow="Biblioteca de costos" title="Materiales" description="Precios actuales por kilogramo para estimar cada fabricación." actions={<button className="button button--accent" onClick={() => open()}><Plus size={18} /> Nuevo material</button>} />
      <div className="info-band"><Archive size={20} /><p><strong>Los cambios no alteran cotizaciones anteriores.</strong> Cada pieza conserva una instantánea del precio por kilogramo usado al crearla; recalcular mantiene ese histórico.</p></div>
      {items.length === 0 ? <EmptyState icon={<Package />} title="Sin materiales" description="Agrega un material activo para comenzar a costear piezas." action={<button className="button button--primary" onClick={() => open()}>Agregar material</button>} /> : (
        <section className="catalog-grid">
          {items.map((item) => (
            <article className={`catalog-card ${item.active ? '' : 'is-inactive'}`} key={item.id}>
              <header><span className="catalog-icon"><Package /></span><span className={`availability ${item.active ? 'active' : ''}`}>{item.active ? 'Activo' : 'Archivado'}</span></header>
              <h2>{item.name}</h2>
              <div className="catalog-price"><strong>{currency.format(item.pricePerKg)}</strong><span>/ kg</span></div>
              <p>Registrado {formatDate(item.createdAt)}</p>
              <footer><button className="button button--secondary button--compact" onClick={() => open(item)}><Pencil size={16} /> Editar</button><button className="toggle-button" onClick={() => void toggle(item)} disabled={busyId !== null} aria-label={`${item.active ? 'Archivar' : 'Activar'} ${item.name}`} aria-pressed={item.active}><span />{item.active ? 'Activo' : 'Inactivo'}</button></footer>
            </article>
          ))}
        </section>
      )}
      {editing && <Modal title={editing === 'new' ? 'Nuevo material' : 'Editar material'} onClose={() => !saving && setEditing(null)}><form className="form-stack" onSubmit={submit}><FormField label="Nombre *" htmlFor="material-name" error={errors.name}><input id="material-name" autoFocus value={form.name} maxLength={100} onChange={(event) => { setForm({ ...form, name: event.target.value }); setErrors({ ...errors, name: '' }) }} required /></FormField><FormField label="Precio por kilogramo (MXN) *" htmlFor="material-price" error={errors.pricePerKg}><input id="material-price" type="number" min="0" step="0.01" value={form.pricePerKg} onChange={(event) => { setForm({ ...form, pricePerKg: Number(event.target.value) }); setErrors({ ...errors, pricePerKg: '' }) }} required /></FormField><label className="check-row"><input type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /><span><strong>Disponible para nuevas piezas</strong><small>Puede cambiarse después sin perder el historial.</small></span></label><div className="modal-actions"><button type="button" className="button button--secondary" onClick={() => setEditing(null)} disabled={saving}>Cancelar</button><button className="button button--primary" disabled={saving}>{saving ? 'Guardando…' : 'Guardar material'}</button></div></form></Modal>}
    </div>
  )
}
