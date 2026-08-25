import { AlertTriangle, Archive, Package, Pencil, Plus } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { materialsApi } from '../api'
import { ApiError } from '../api/http'
import { EmptyState, ErrorState, LoadingState } from '../components/Feedback'
import { FormField } from '../components/FormField'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'
import type { Material, MaterialRequest } from '../types'
import { currency, formatDate, number } from '../utils/format'

const emptyMaterial: MaterialRequest = {
  name: '',
  pricePerKg: 0,
  materialType: null,
  brand: null,
  color: null,
  stockGrams: null,
  lowStockThresholdGrams: null,
  notes: null,
  active: true,
}

export function MaterialsPage() {
  const [items, setItems] = useState<Material[] | null>(null)
  const [editing, setEditing] = useState<Material | 'new' | null>(null)
  const [form, setForm] = useState<MaterialRequest>(emptyMaterial)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState<number | null>(null)
  const [saving, setSaving] = useState(false)
  const [reload, setReload] = useState(0)
  const toast = useToast()
  const { isAdmin } = useAuth()

  useEffect(() => {
    let active = true
    materialsApi.list().then((response) => { if (active) setItems(response) }).catch((caught: unknown) => { if (active) setError(caught instanceof ApiError ? caught.message : 'No fue posible cargar materiales.') })
    return () => { active = false }
  }, [reload])

  function open(item?: Material) {
    setEditing(item || 'new')
    setForm(item ? {
      name: item.name,
      pricePerKg: item.pricePerKg,
      materialType: item.materialType,
      brand: item.brand,
      color: item.color,
      stockGrams: item.stockGrams,
      lowStockThresholdGrams: item.lowStockThresholdGrams,
      notes: item.notes,
      active: item.active,
    } : emptyMaterial)
    setErrors({})
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (saving) return
    setSaving(true); setErrors({})
    try {
      const payload = {
        ...form,
        materialType: form.materialType?.trim() || null,
        brand: form.brand?.trim() || null,
        color: form.color?.trim() || null,
        notes: form.notes?.trim() || null,
      }
      const response = editing === 'new' ? await materialsApi.create(payload) : await materialsApi.update(editing!.id, payload)
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
      <PageHeader eyebrow="Biblioteca de costos" title="Materiales" description="Precios actuales por kilogramo para estimar cada fabricación." actions={isAdmin ? <button className="button button--accent" onClick={() => open()}><Plus size={18} /> Nuevo material</button> : undefined} />
      {!isAdmin && <div className="inline-notice catalog-permission-note">Puedes consultar el catálogo. La edición está reservada a administradores.</div>}
      <div className="info-band"><Archive size={20} /><p><strong>Los cambios no alteran cotizaciones anteriores.</strong> Cada pieza conserva una instantánea del precio por kilogramo usado al crearla; recalcular mantiene ese histórico.</p></div>
      {items.length === 0 ? <EmptyState icon={<Package />} title="Sin materiales" description="Un administrador debe agregar un material activo para comenzar a costear piezas." action={isAdmin ? <button className="button button--primary" onClick={() => open()}>Agregar material</button> : undefined} /> : (
        <section className="catalog-grid">
          {items.map((item) => {
            const lowStock = item.active && item.stockGrams !== null && item.lowStockThresholdGrams !== null
              && item.stockGrams <= item.lowStockThresholdGrams
            return (
              <article className={`catalog-card material-card ${item.active ? '' : 'is-inactive'} ${lowStock ? 'has-low-stock' : ''}`} key={item.id}>
                <header><span className="catalog-icon"><Package /></span><span className={`availability ${item.active ? 'active' : ''}`}>{item.active ? 'Activo' : 'Archivado'}</span></header>
                <h2>{item.name}</h2>
                <p className="catalog-identity">{[item.materialType, item.brand, item.color].filter(Boolean).join(' · ') || 'Sin especificaciones adicionales'}</p>
                <div className="catalog-price"><strong>{currency.format(item.pricePerKg)}</strong><span>/ kg</span></div>
                <div className="stock-summary">
                  <span>Stock aproximado</span>
                  <strong>{item.stockGrams === null ? 'Sin registrar' : `≈ ${number.format(item.stockGrams)} g`}</strong>
                  {item.lowStockThresholdGrams !== null && <small>Umbral: {number.format(item.lowStockThresholdGrams)} g</small>}
                  {lowStock && <em><AlertTriangle size={14} /> Stock bajo</em>}
                </div>
                {item.notes && <p className="catalog-notes">{item.notes}</p>}
                <p>Registrado {formatDate(item.createdAt)}</p>
                {isAdmin && <footer><button className="button button--secondary button--compact" onClick={() => open(item)}><Pencil size={16} /> Editar</button><button className="toggle-button" onClick={() => void toggle(item)} disabled={busyId !== null} aria-label={`${item.active ? 'Archivar' : 'Activar'} ${item.name}`} aria-pressed={item.active}><span />{item.active ? 'Activo' : 'Inactivo'}</button></footer>}
              </article>
            )
          })}
        </section>
      )}
      {isAdmin && editing && <Modal title={editing === 'new' ? 'Nuevo material' : 'Editar material'} onClose={() => !saving && setEditing(null)}>
        <form className="form-stack" onSubmit={submit}>
          <div className="form-grid form-grid--2">
            <FormField label="Nombre *" htmlFor="material-name" error={errors.name}><input id="material-name" autoFocus value={form.name} maxLength={100} onChange={(event) => { setForm({ ...form, name: event.target.value }); setErrors({ ...errors, name: '' }) }} required /></FormField>
            <FormField label="Precio por kilogramo (MXN) *" htmlFor="material-price" error={errors.pricePerKg}><input id="material-price" type="number" min="0" step="0.01" value={form.pricePerKg} onChange={(event) => { setForm({ ...form, pricePerKg: Number(event.target.value) }); setErrors({ ...errors, pricePerKg: '' }) }} required /></FormField>
            <FormField label="Tipo de material" htmlFor="material-type" error={errors.materialType}><input id="material-type" value={form.materialType || ''} maxLength={60} placeholder="PLA, PETG, resina…" onChange={(event) => setForm({ ...form, materialType: event.target.value || null })} /></FormField>
            <FormField label="Marca" htmlFor="material-brand" error={errors.brand}><input id="material-brand" value={form.brand || ''} maxLength={100} placeholder="Opcional" onChange={(event) => setForm({ ...form, brand: event.target.value || null })} /></FormField>
            <FormField label="Color" htmlFor="material-color" error={errors.color}><input id="material-color" value={form.color || ''} maxLength={80} placeholder="Nombre o código" onChange={(event) => setForm({ ...form, color: event.target.value || null })} /></FormField>
            <FormField label="Stock aproximado (g)" htmlFor="material-stock" error={errors.stockGrams} hint="Dato manual; no se descuenta con las órdenes."><input id="material-stock" type="number" min="0" step="0.001" value={form.stockGrams ?? ''} placeholder="Sin registrar" onChange={(event) => setForm({ ...form, stockGrams: event.target.value === '' ? null : Number(event.target.value) })} /></FormField>
            <FormField label="Umbral de stock bajo (g)" htmlFor="material-threshold" error={errors.lowStockThresholdGrams}><input id="material-threshold" type="number" min="0" step="0.001" value={form.lowStockThresholdGrams ?? ''} placeholder="Sin alerta" onChange={(event) => setForm({ ...form, lowStockThresholdGrams: event.target.value === '' ? null : Number(event.target.value) })} /></FormField>
          </div>
          <FormField label="Notas" htmlFor="material-notes" error={errors.notes}><textarea id="material-notes" rows={3} maxLength={1000} value={form.notes || ''} placeholder="Propiedades, proveedor habitual o condiciones de uso…" onChange={(event) => setForm({ ...form, notes: event.target.value || null })} /></FormField>
          <label className="check-row"><input type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /><span><strong>Disponible para nuevas piezas</strong><small>Puede cambiarse después sin perder el historial.</small></span></label>
          <div className="modal-actions"><button type="button" className="button button--secondary" onClick={() => setEditing(null)} disabled={saving}>Cancelar</button><button className="button button--primary" disabled={saving}>{saving ? 'Guardando…' : 'Guardar material'}</button></div>
        </form>
      </Modal>}
    </div>
  )
}
