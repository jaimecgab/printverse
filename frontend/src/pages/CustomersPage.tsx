import { Mail, Pencil, Phone, Plus, Search, UserRound, Users } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { customersApi } from '../api'
import { ApiError } from '../api/http'
import { EmptyState, ErrorState, LoadingState } from '../components/Feedback'
import { FormField } from '../components/FormField'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { useToast } from '../context/ToastContext'
import type { Customer, CustomerRequest } from '../types'
import { formatDate } from '../utils/format'

const emptyForm: CustomerRequest = { name: '', phone: '', email: null, notes: null }

export function CustomersPage() {
  const [customers, setCustomers] = useState<Customer[] | null>(null)
  const [search, setSearch] = useState('')
  const [editing, setEditing] = useState<Customer | 'new' | null>(null)
  const [form, setForm] = useState<CustomerRequest>(emptyForm)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [reload, setReload] = useState(0)
  const toast = useToast()

  useEffect(() => {
    let active = true
    customersApi.list().then((response) => { if (active) setCustomers(response) }).catch((caught: unknown) => {
      if (active) setError(caught instanceof ApiError ? caught.message : 'No fue posible consultar los clientes.')
    })
    return () => { active = false }
  }, [reload])

  function open(customer?: Customer) {
    setEditing(customer || 'new')
    setForm(customer ? { name: customer.name, phone: customer.phone, email: customer.email ?? null, notes: customer.notes ?? null } : emptyForm)
    setFieldErrors({})
  }

  function update(field: keyof CustomerRequest, value: string) {
    setForm((current) => ({ ...current, [field]: value || null }))
    setFieldErrors((current) => ({ ...current, [field]: '' }))
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (saving) return
    setSaving(true)
    setFieldErrors({})
    try {
      const payload = { ...form, name: form.name.trim(), phone: form.phone.trim(), email: form.email?.trim() || null, notes: form.notes?.trim() || null }
      const response = editing === 'new' ? await customersApi.create(payload) : await customersApi.update(editing!.id, payload)
      setCustomers((current) => editing === 'new' ? [response, ...(current || [])] : (current || []).map((item) => item.id === response.id ? response : item))
      toast.success(editing === 'new' ? 'Cliente registrado.' : 'Datos del cliente actualizados.')
      setEditing(null)
    } catch (caught) {
      if (caught instanceof ApiError) {
        setFieldErrors(caught.errors)
        if (Object.keys(caught.errors).length === 0) toast.error(caught.message)
      } else toast.error('No fue posible guardar el cliente.')
    } finally {
      setSaving(false)
    }
  }

  if (!customers && !error) return <LoadingState label="Consultando directorio…" />
  if (error) return <ErrorState message={error} retry={() => { setError(''); setReload((value) => value + 1) }} />
  if (!customers) return null

  const query = search.toLocaleLowerCase('es-MX')
  const filtered = customers.filter((customer) => [customer.name, customer.phone, customer.email || ''].some((value) => value.toLocaleLowerCase('es-MX').includes(query)))

  return (
    <div className="page">
      <PageHeader eyebrow="Directorio" title="Clientes" description="Personas y empresas detrás de cada proyecto." actions={<button className="button button--accent" onClick={() => open()}><Plus size={18} /> Nuevo cliente</button>} />
      <div className="toolbar">
        <label className="search-box"><Search size={18} /><span className="sr-only">Buscar clientes</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar por nombre, teléfono o correo…" /></label>
        <span className="result-count">{filtered.length} de {customers.length}</span>
      </div>
      {filtered.length === 0 ? (
        <EmptyState icon={<Users />} title={customers.length ? 'Sin coincidencias' : 'Tu directorio está vacío'} description={customers.length ? 'Prueba una búsqueda distinta.' : 'Registra clientes reales para poder crear cotizaciones.'} action={!customers.length ? <button className="button button--primary" onClick={() => open()}>Registrar cliente</button> : undefined} />
      ) : (
        <section className="customer-grid">
          {filtered.map((customer) => (
            <article className="customer-card" key={customer.id}>
              <div className="customer-card-top"><span className="avatar"><UserRound /></span><button className="icon-button" onClick={() => open(customer)} aria-label={`Editar a ${customer.name}`}><Pencil size={17} /></button></div>
              <h2>{customer.name}</h2>
              <a href={`tel:${customer.phone}`}><Phone size={16} /> {customer.phone}</a>
              {customer.email ? <a href={`mailto:${customer.email}`}><Mail size={16} /> {customer.email}</a> : <span className="muted-line"><Mail size={16} /> Sin correo</span>}
              {customer.notes && <p className="customer-notes">{customer.notes}</p>}
              <footer>Cliente desde {formatDate(customer.createdAt)}</footer>
            </article>
          ))}
        </section>
      )}

      {editing && (
        <Modal title={editing === 'new' ? 'Nuevo cliente' : 'Editar cliente'} description="Los campos marcados son necesarios para cotizar." onClose={() => !saving && setEditing(null)}>
          <form className="form-stack" onSubmit={submit} noValidate>
            <FormField label="Nombre *" htmlFor="customer-name" error={fieldErrors.name}><input id="customer-name" autoFocus value={form.name} onChange={(event) => update('name', event.target.value)} maxLength={150} required /></FormField>
            <FormField label="Teléfono *" htmlFor="customer-phone" error={fieldErrors.phone}><input id="customer-phone" value={form.phone} onChange={(event) => update('phone', event.target.value)} maxLength={40} required /></FormField>
            <FormField label="Correo electrónico" htmlFor="customer-email" error={fieldErrors.email}><input id="customer-email" type="email" value={form.email || ''} onChange={(event) => update('email', event.target.value)} maxLength={254} /></FormField>
            <FormField label="Notas internas" htmlFor="customer-notes" error={fieldErrors.notes} hint="No se muestran en la cotización."><textarea id="customer-notes" rows={4} value={form.notes || ''} onChange={(event) => update('notes', event.target.value)} maxLength={2000} /></FormField>
            <div className="modal-actions"><button type="button" className="button button--secondary" onClick={() => setEditing(null)} disabled={saving}>Cancelar</button><button className="button button--primary" disabled={saving}>{saving ? 'Guardando…' : 'Guardar cliente'}</button></div>
          </form>
        </Modal>
      )}
    </div>
  )
}
