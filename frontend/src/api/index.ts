import { http } from './http'
import type {
  ChargeRequest,
  Customer,
  CustomerRequest,
  ItemRequest,
  Material,
  MaterialRequest,
  Printer,
  PrinterRequest,
  Quote,
  QuoteCreateRequest,
  QuoteStatus,
  QuoteSummary,
  QuoteUpdateRequest,
} from '../types'

export const customersApi = {
  list: () => http.get<Customer[]>('/api/customers'),
  get: (id: number) => http.get<Customer>(`/api/customers/${id}`),
  create: (body: CustomerRequest) => http.post<Customer>('/api/customers', body),
  update: (id: number, body: CustomerRequest) => http.put<Customer>(`/api/customers/${id}`, body),
}

export const materialsApi = {
  list: () => http.get<Material[]>('/api/materials'),
  create: (body: MaterialRequest) => http.post<Material>('/api/materials', body),
  update: (id: number, body: MaterialRequest) => http.put<Material>(`/api/materials/${id}`, body),
  setActive: (id: number, active: boolean) => http.patch<Material>(`/api/materials/${id}/active`, { active }),
}

export const printersApi = {
  list: () => http.get<Printer[]>('/api/printers'),
  create: (body: PrinterRequest) => http.post<Printer>('/api/printers', body),
  update: (id: number, body: PrinterRequest) => http.put<Printer>(`/api/printers/${id}`, body),
  setActive: (id: number, active: boolean) => http.patch<Printer>(`/api/printers/${id}/active`, { active }),
}

export const quotesApi = {
  list: () => http.get<QuoteSummary[]>('/api/quotes'),
  get: (id: number) => http.get<Quote>(`/api/quotes/${id}`),
  create: (body: QuoteCreateRequest) => http.post<Quote>('/api/quotes', body),
  update: (id: number, body: QuoteUpdateRequest) => http.put<Quote>(`/api/quotes/${id}`, body),
  addItem: (quoteId: number, body: ItemRequest) => http.post<Quote>(`/api/quotes/${quoteId}/items`, body),
  updateItem: (quoteId: number, itemId: number, body: ItemRequest) =>
    http.put<Quote>(`/api/quotes/${quoteId}/items/${itemId}`, body),
  deleteItem: (quoteId: number, itemId: number) => http.delete(`/api/quotes/${quoteId}/items/${itemId}`),
  addCharge: (quoteId: number, itemId: number, body: ChargeRequest) =>
    http.post<Quote>(`/api/quotes/${quoteId}/items/${itemId}/charges`, body),
  deleteCharge: (quoteId: number, itemId: number, chargeId: number) =>
    http.delete(`/api/quotes/${quoteId}/items/${itemId}/charges/${chargeId}`),
  recalculate: (id: number) => http.post<Quote>(`/api/quotes/${id}/recalculate`),
  setStatus: (id: number, status: QuoteStatus) => http.patch<Quote>(`/api/quotes/${id}/status`, { status }),
}
