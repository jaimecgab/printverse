import { http } from './http'
import type {
  Customer,
  CustomerOverview,
  CustomerRequest,
  ItemRequest,
  Material,
  MaterialRequest,
  Dashboard,
  Printer,
  PrinterRequest,
  Quote,
  QuoteCreateRequest,
  QuoteDuplicateRequest,
  QuoteStatus,
  QuoteSummary,
  QuoteUpdateRequest,
  ProductionItemUpdateRequest,
  ProductionOrder,
  ProductionOrderCreateRequest,
  ProductionOrderStatus,
  AuthSession,
  AuthUser,
  LoginRequest,
} from '../types'

export const authApi = {
  login: (body: LoginRequest) => http.post<AuthSession>('/api/auth/login', body),
  me: () => http.get<AuthUser>('/api/auth/me'),
}

export const customersApi = {
  list: () => http.get<Customer[]>('/api/customers'),
  get: (id: number) => http.get<Customer>(`/api/customers/${id}`),
  overview: (id: number) => http.get<CustomerOverview>(`/api/customers/${id}/overview`),
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
  recalculate: (id: number) => http.post<Quote>(`/api/quotes/${id}/recalculate`),
  setStatus: (id: number, status: QuoteStatus) => http.patch<Quote>(`/api/quotes/${id}/status`, { status }),
  duplicate: (id: number, body: QuoteDuplicateRequest) => http.post<Quote>(`/api/quotes/${id}/duplicate`, body),
  pdf: (id: number, signal?: AbortSignal) => http.download(`/api/quotes/${id}/pdf`, `cotizacion-${id}.pdf`, signal),
}

export const productionApi = {
  list: () => http.get<ProductionOrder[]>('/api/production-orders'),
  get: (id: number) => http.get<ProductionOrder>(`/api/production-orders/${id}`),
  getByQuote: (quoteId: number) => http.get<ProductionOrder>(`/api/production-orders/by-quote/${quoteId}`),
  convert: (quoteId: number, body?: ProductionOrderCreateRequest) => http.post<ProductionOrder>(`/api/quotes/${quoteId}/production-order`, body),
  setStatus: (id: number, status: ProductionOrderStatus) => http.patch<ProductionOrder>(`/api/production-orders/${id}/status`, { status }),
  updateItem: (orderId: number, itemId: number, body: ProductionItemUpdateRequest) =>
    http.patch<ProductionOrder>(`/api/production-orders/${orderId}/items/${itemId}`, body),
}

export const dashboardApi = { get: () => http.get<Dashboard>('/api/dashboard') }
