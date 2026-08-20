import { ArrowLeft, ArrowRight, CalendarDays } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { customersApi, quotesApi } from '../api'
import { ApiError } from '../api/http'
import { ErrorState, LoadingState } from '../components/Feedback'
import { FormField } from '../components/FormField'
import { PageHeader } from '../components/PageHeader'
import { useToast } from '../context/ToastContext'
import type { Customer, QuoteCreateRequest } from '../types'
import { addDays, toDateInput } from '../utils/format'

export function NewQuotePage() {
  const [customers, setCustomers] = useState<Customer[] | null>(null)
  const [form, setForm] = useState<QuoteCreateRequest>({ customerId: 0, validUntil: addDays(15), estimatedDeliveryDate: null, depositPercentage: null, notes: null, markupPercentage: 30, discountPercentage: 0, taxEnabled: true, taxPercentage: 16 })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const navigate = useNavigate(); const toast = useToast()

  useEffect(() => {
    let active = true
    customersApi.list().then((response) => { if (active) setCustomers(response) }).catch((caught: unknown) => { if (active) setError(caught instanceof ApiError ? caught.message : 'No fue posible cargar los clientes.') })
    return () => { active = false }
  }, [])

  async function submit(event: FormEvent) {
    event.preventDefault(); if (saving) return; setSaving(true); setErrors({})
    try {
      const quote = await quotesApi.create({ ...form, notes: form.notes?.trim() || null })
      toast.success('Borrador creado. Ahora agrega una o más piezas.'); navigate(`/quotes/${quote.id}/edit`, { replace: true })
    } catch (caught) { if (caught instanceof ApiError) { setErrors(caught.errors); if (!Object.keys(caught.errors).length) toast.error(caught.message) } else toast.error('No fue posible crear la cotización.') } finally { setSaving(false) }
  }

  if (!customers && !error) return <LoadingState label="Preparando nuevo proyecto…" />
  if (error) return <ErrorState message={error} />
  if (!customers) return null

  return (
    <div className="page page--narrow">
      <Link to="/quotes" className="back-link"><ArrowLeft size={17} /> Volver a cotizaciones</Link>
      <PageHeader eyebrow="Paso 1 de 2" title="Inicia una cotización" description="Define el cliente y las condiciones generales. Después podrás agregar todas las piezas y cargos necesarios." />
      {customers.length === 0 ? <div className="empty-state"><h2>Primero necesitas un cliente</h2><p>No usamos datos de ejemplo. Registra un cliente real antes de cotizar.</p><Link to="/customers" className="button button--primary">Ir a clientes</Link></div> : (
        <form className="panel quote-terms-form" onSubmit={submit}>
          <div className="form-section-title"><span>01</span><div><h2>Cliente y calendario</h2><p>La cotización quedará ligada a este cliente.</p></div></div>
          <div className="form-grid form-grid--2"><FormField label="Cliente *" htmlFor="quote-customer" error={errors.customerId}><select id="quote-customer" autoFocus required value={form.customerId} onChange={(event) => setForm({ ...form, customerId: Number(event.target.value) })}><option value={0}>Selecciona un cliente…</option>{customers.map((customer) => <option key={customer.id} value={customer.id}>{customer.name} · {customer.phone}</option>)}</select></FormField><FormField label="Válida hasta *" htmlFor="quote-valid" error={errors.validUntil}><input id="quote-valid" type="date" min={toDateInput(new Date())} required value={form.validUntil} onChange={(event) => setForm({ ...form, validUntil: event.target.value })} /></FormField><FormField label="Entrega estimada" htmlFor="quote-delivery" error={errors.estimatedDeliveryDate}><input id="quote-delivery" type="date" min={toDateInput(new Date())} value={form.estimatedDeliveryDate || ''} onChange={(event) => setForm({ ...form, estimatedDeliveryDate: event.target.value || null })} /></FormField><FormField label="Anticipo (%)" htmlFor="quote-deposit" error={errors.depositPercentage}><input id="quote-deposit" type="number" min="0" max="100" step="0.0001" value={form.depositPercentage ?? ''} placeholder="Opcional" onChange={(event) => setForm({ ...form, depositPercentage: event.target.value === '' ? null : Number(event.target.value) })} /></FormField></div>
          <div className="form-section-title"><span>02</span><div><h2>Condiciones de precio</h2><p>Se aplicarán a todas las piezas del proyecto.</p></div></div>
          <div className="form-grid form-grid--3"><FormField label="Markup sobre costo (%) *" htmlFor="quote-markup" error={errors.markupPercentage}><input id="quote-markup" type="number" min="0" step="0.0001" required value={form.markupPercentage} onChange={(event) => setForm({ ...form, markupPercentage: Number(event.target.value) })} /></FormField><FormField label="Descuento (%)" htmlFor="quote-discount" error={errors.discountPercentage}><input id="quote-discount" type="number" min="0" max="100" step="0.0001" value={form.discountPercentage || 0} onChange={(event) => setForm({ ...form, discountPercentage: Number(event.target.value) })} /></FormField><FormField label="IVA (%) *" htmlFor="quote-tax" error={errors.taxPercentage}><input id="quote-tax" type="number" min="0" max="100" step="0.0001" required disabled={!form.taxEnabled} value={form.taxPercentage} onChange={(event) => setForm({ ...form, taxPercentage: Number(event.target.value) })} /></FormField></div>
          <label className="check-row"><input type="checkbox" checked={form.taxEnabled} onChange={(event) => setForm({ ...form, taxEnabled: event.target.checked })} /><span><strong>Aplicar IVA a la cotización</strong><small>El total se calcula después del descuento.</small></span></label>
          <FormField label="Notas para la cotización" htmlFor="quote-notes" error={errors.notes}><textarea id="quote-notes" rows={5} maxLength={3000} value={form.notes || ''} onChange={(event) => setForm({ ...form, notes: event.target.value || null })} placeholder="Alcance, colores, condiciones de entrega…" /></FormField>
          <div className="next-step-note"><CalendarDays /><span><strong>El siguiente paso no es definitivo.</strong> El borrador seguirá editable mientras no lo marques como enviado.</span></div>
          <div className="form-submit-row"><Link to="/quotes" className="button button--secondary">Cancelar</Link><button className="button button--accent" disabled={saving || !form.customerId}>{saving ? 'Creando borrador…' : <>Crear y agregar piezas <ArrowRight size={18} /></>}</button></div>
        </form>
      )}
    </div>
  )
}
