import { Plus, Trash2 } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/http'
import type { ChargeRequest, ItemRequest, Material, Printer, QuoteItem } from '../types'
import { currency } from '../utils/format'
import { FormField } from './FormField'
import { Modal } from './Modal'

interface DraftCharge extends ChargeRequest { key: string }

interface PieceEditorProps {
  item?: QuoteItem
  materials: Material[]
  printers: Printer[]
  onClose: () => void
  onSave: (item: ItemRequest) => Promise<void>
}

export function PieceEditor({ item, materials, printers, onClose, onSave }: PieceEditorProps) {
  const [form, setForm] = useState({
    name: item?.name || '', quantity: item?.quantity || 1,
    materialId: item?.material.id || materials.find((value) => value.active)?.id || 0,
    printerId: item?.printer.id || printers.find((value) => value.active)?.id || 0,
    weightGrams: item?.weightGrams || 0,
    hours: item ? Math.floor(item.printTimeMinutes / 60) : 0,
    minutes: item ? item.printTimeMinutes % 60 : 0,
    failureRiskPercentage: item?.failureRiskPercentage || 0,
    manual: item?.manualUnitPrice !== null && item?.manualUnitPrice !== undefined,
    manualUnitPrice: item?.manualUnitPrice || 0,
  })
  const [charges, setCharges] = useState<DraftCharge[]>(() => item?.additionalCharges.map((value) => ({ description: value.description, amount: value.amount, key: `existing-${value.id}` })) || [])
  const [charge, setCharge] = useState<ChargeRequest>({ description: '', amount: 0 })
  const [saving, setSaving] = useState(false)
  const [dirty, setDirty] = useState(false)
  const [message, setMessage] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})

  const availableMaterials = materials.filter((value) => value.active || value.id === item?.material.id)
  const availablePrinters = printers.filter((value) => value.active || value.id === item?.printer.id)

  function addCharge() {
    if (!charge.description.trim()) { setMessage('Escribe una descripción para el cargo.'); return }
    if (!Number.isFinite(charge.amount) || charge.amount < 0) { setMessage('El importe del cargo no puede ser negativo.'); return }
    if (Math.abs(Math.round(charge.amount * 100) - charge.amount * 100) > 1e-8) { setMessage('El importe del cargo admite como máximo dos decimales.'); return }
    setCharges((current) => [...current, { ...charge, description: charge.description.trim(), key: `new-${Date.now()}-${current.length}` }])
    setDirty(true)
    setCharge({ description: '', amount: 0 }); setMessage('')
  }

  async function submit(event: FormEvent) {
    event.preventDefault(); if (saving) return
    if (!form.materialId || !form.printerId) { setMessage('Selecciona un material y una impresora activos.'); return }
    const pendingDescription = charge.description.trim()
    const hasPendingCharge = Boolean(pendingDescription) || charge.amount !== 0
    if (hasPendingCharge && !pendingDescription) { setMessage('Escribe una descripción para el cargo pendiente.'); return }
    if (hasPendingCharge && (!Number.isFinite(charge.amount) || charge.amount < 0)) { setMessage('El importe del cargo pendiente no puede ser negativo.'); return }
    if (hasPendingCharge && Math.abs(Math.round(charge.amount * 100) - charge.amount * 100) > 1e-8) { setMessage('El importe del cargo pendiente admite como máximo dos decimales.'); return }
    const submittedCharges = hasPendingCharge ? [...charges, { ...charge, description: pendingDescription }] : charges
    setSaving(true); setMessage(''); setErrors({})
    try {
      await onSave({
        name: form.name.trim(), quantity: form.quantity, materialId: form.materialId, printerId: form.printerId,
        weightGrams: form.weightGrams, printTimeMinutes: form.hours * 60 + form.minutes,
        failureRiskPercentage: form.failureRiskPercentage, manualUnitPrice: form.manual ? form.manualUnitPrice : null,
        additionalCharges: submittedCharges.map(({ description, amount }) => ({ description, amount })),
      })
      onClose()
    } catch (caught) {
      if (caught instanceof ApiError) setErrors(caught.errors)
      setMessage(caught instanceof Error ? caught.message : 'No fue posible guardar la pieza.')
    } finally { setSaving(false) }
  }

  function requestClose() {
    if (!dirty || window.confirm('Hay cambios de la pieza sin guardar. ¿Cerrar de todos modos?')) onClose()
  }

  return (
    <Modal title={item ? 'Editar pieza' : 'Agregar pieza'} description="Define consumos y tiempo por unidad; la cantidad multiplica el resultado." onClose={() => !saving && requestClose()} wide>
      <form className="piece-form" onSubmit={submit}>
        {message && <div className="inline-error" role="alert">{message}</div>}
        <div className="form-grid form-grid--3">
          <FormField label="Nombre de la pieza *" htmlFor="piece-name" error={errors.name}><input id="piece-name" autoFocus required maxLength={200} value={form.name} onChange={(event) => { setForm({ ...form, name: event.target.value }); setDirty(true) }} /></FormField>
          <FormField label="Cantidad *" htmlFor="piece-quantity" error={errors.quantity}><input id="piece-quantity" type="number" min="1" max="100000" step="1" required value={form.quantity} onChange={(event) => { setForm({ ...form, quantity: Number(event.target.value) }); setDirty(true) }} /></FormField>
          <FormField label="Peso por unidad (g) *" htmlFor="piece-weight" error={errors.weightGrams}><input id="piece-weight" type="number" min="0" step="0.001" required value={form.weightGrams} onChange={(event) => { setForm({ ...form, weightGrams: Number(event.target.value) }); setDirty(true) }} /></FormField>
        </div>
        <div className="form-grid form-grid--2">
          <FormField label="Material *" htmlFor="piece-material" error={errors.materialId}><select id="piece-material" required value={form.materialId} onChange={(event) => { setForm({ ...form, materialId: Number(event.target.value) }); setDirty(true) }}><option value={0}>Selecciona…</option>{availableMaterials.map((value) => <option key={value.id} value={value.id}>{value.name}{!value.active ? ' (archivado, selección actual)' : ''}</option>)}</select></FormField>
          <FormField label="Impresora *" htmlFor="piece-printer" error={errors.printerId}><select id="piece-printer" required value={form.printerId} onChange={(event) => { setForm({ ...form, printerId: Number(event.target.value) }); setDirty(true) }}><option value={0}>Selecciona…</option>{availablePrinters.map((value) => <option key={value.id} value={value.id}>{value.name}{value.model ? ` · ${value.model}` : ''}{!value.active ? ' (archivada, selección actual)' : ''}</option>)}</select></FormField>
        </div>
        <fieldset className="fieldset-block">
          <legend>Tiempo de impresión por unidad</legend>
          <div className="time-inputs"><FormField label="Horas" htmlFor="piece-hours" error={errors.printTimeMinutes}><input id="piece-hours" type="number" min="0" step="1" value={form.hours} onChange={(event) => { setForm({ ...form, hours: Number(event.target.value) }); setDirty(true) }} /></FormField><FormField label="Minutos" htmlFor="piece-minutes"><input id="piece-minutes" type="number" min="0" max="59" step="1" value={form.minutes} onChange={(event) => { setForm({ ...form, minutes: Number(event.target.value) }); setDirty(true) }} /></FormField><FormField label="Riesgo de fallo (%)" htmlFor="piece-risk" error={errors.failureRiskPercentage}><input id="piece-risk" type="number" min="0" max="100" step="0.0001" required value={form.failureRiskPercentage} onChange={(event) => { setForm({ ...form, failureRiskPercentage: Number(event.target.value) }); setDirty(true) }} /></FormField></div>
        </fieldset>
        <label className="check-row manual-price"><input type="checkbox" checked={form.manual} onChange={(event) => { setForm({ ...form, manual: event.target.checked }); setDirty(true) }} /><span><strong>Definir precio unitario manual</strong><small>Al desactivarlo se envía nulo y vuelve a usarse el precio sugerido.</small></span></label>
        {item && <div className="price-reference"><span>Precio sugerido vigente</span><strong>{currency.format(item.suggestedPriceUnit)}</strong><small>{form.manual ? 'El precio personalizado será el usado para el subtotal.' : 'Este precio se usará para el subtotal.'}</small></div>}
        {form.manual && <FormField label="Precio unitario manual (MXN)" htmlFor="manual-price" error={errors.manualUnitPrice}><input id="manual-price" type="number" min="0" step="0.01" required value={form.manualUnitPrice} onChange={(event) => { setForm({ ...form, manualUnitPrice: Number(event.target.value) }); setDirty(true) }} /></FormField>}

        <section className="charge-editor">
          <div className="section-heading"><div><p className="eyebrow">Acabados y extras</p><h3>Cargos adicionales por unidad</h3></div></div>
          {charges.map((value) => <div className="charge-row charge-row--draft" key={value.key}><span>{value.description}<small>Se guarda con la pieza</small></span><strong>{currency.format(value.amount)}</strong><button type="button" className="icon-button icon-button--danger" onClick={() => { setCharges((current) => current.filter((itemValue) => itemValue.key !== value.key)); setDirty(true) }} aria-label={`Quitar cargo ${value.description}`}><Trash2 size={16} /></button></div>)}
          <div className="new-charge-row"><FormField label="Descripción" htmlFor="charge-description"><input id="charge-description" value={charge.description} maxLength={200} placeholder="Lijado, pintura, empaque…" onChange={(event) => { setCharge({ ...charge, description: event.target.value }); setDirty(true) }} /></FormField><FormField label="Importe" htmlFor="charge-amount"><input id="charge-amount" type="number" min="0" step="0.01" value={charge.amount} onChange={(event) => { setCharge({ ...charge, amount: Number(event.target.value) }); setDirty(true) }} /></FormField><button type="button" className="button button--secondary" onClick={addCharge}><Plus size={17} /> Añadir</button></div>
          <small>Altas y bajas se aplicarán juntas al guardar la pieza. Si cierras, no se modifica el servidor.</small>
        </section>
        <div className="modal-actions"><button type="button" className="button button--secondary" onClick={requestClose} disabled={saving}>Cerrar</button><button className="button button--primary" disabled={saving}>{saving ? 'Guardando pieza y cargos…' : item ? 'Actualizar pieza' : 'Agregar pieza'}</button></div>
      </form>
    </Modal>
  )
}
