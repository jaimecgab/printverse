export type { AuthSession, AuthUser, LoginRequest, UserRole } from './auth'

export type QuoteStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'REJECTED'

export interface Customer {
  id: number
  name: string
  phone: string
  email: string | null
  notes: string | null
  createdAt: string
}

export interface CustomerRequest {
  name: string
  phone: string
  email: string | null
  notes: string | null
}

export interface Material {
  id: number
  name: string
  pricePerKg: number
  materialType: string | null
  brand: string | null
  color: string | null
  stockGrams: number | null
  lowStockThresholdGrams: number | null
  notes: string | null
  active: boolean
  createdAt: string
}

export interface MaterialRequest {
  name: string
  pricePerKg: number
  materialType: string | null
  brand: string | null
  color: string | null
  stockGrams: number | null
  lowStockThresholdGrams: number | null
  notes: string | null
  active: boolean
}

export type PrinterOperationalStatus = 'AVAILABLE' | 'BUSY' | 'MAINTENANCE' | 'OUT_OF_SERVICE'

export interface Printer {
  id: number
  name: string
  model: string | null
  costPerHour: number
  operationalStatus: PrinterOperationalStatus
  notes: string | null
  active: boolean
  createdAt: string
}

export interface PrinterRequest {
  name: string
  model: string | null
  costPerHour: number
  operationalStatus: PrinterOperationalStatus
  notes: string | null
  active: boolean
}

export interface CustomerSummary {
  id: number
  name: string
  phone: string
  email: string | null
}

export interface QuoteSummary {
  id: number
  quoteNumber: string
  title: string | null
  customer: CustomerSummary
  status: QuoteStatus
  createdAt: string
  updatedAt: string
  validUntil: string
  currencyCode: string
  total: number
  estimatedProfit: number
  realMarginPercentage: number
}

export interface Charge {
  id: number
  description: string
  amount: number
}

export interface QuoteItem {
  id: number
  name: string
  quantity: number
  material: { id: number; name: string }
  printer: { id: number; name: string; model: string | null }
  weightGrams: number
  printTimeMinutes: number
  failureRiskPercentage: number
  materialPricePerKgSnapshot: number
  printerCostPerHourSnapshot: number
  materialCostUnit: number
  machineCostUnit: number
  failureRiskCostUnit: number
  additionalChargesUnit: number
  internalCostUnit: number
  suggestedPriceUnit: number
  manualUnitPrice: number | null
  finalUnitPrice: number
  itemSubtotal: number
  additionalCharges: Charge[]
}

export interface Quote extends QuoteSummary {
  sentAt: string | null
  acceptedAt: string | null
  rejectedAt: string | null
  estimatedDeliveryDate: string | null
  depositPercentage: number | null
  notes: string | null
  internalNotes: string | null
  markupPercentage: number
  discountPercentage: number
  taxEnabled: boolean
  taxPercentage: number
  internalCost: number
  suggestedSubtotal: number
  finalSubtotal: number
  discountAmount: number
  subtotalAfterDiscount: number
  taxAmount: number
  depositAmount: number
  remainingBalance: number
  estimatedProfit: number
  realMarginPercentage: number
  items: QuoteItem[]
}

export interface QuoteCreateRequest {
  customerId: number
  title: string | null
  validUntil: string
  estimatedDeliveryDate: string | null
  depositPercentage: number | null
  notes: string | null
  internalNotes: string | null
  markupPercentage: number
  discountPercentage?: number
  taxEnabled: boolean
  taxPercentage: number
}

export type QuoteUpdateRequest = Omit<QuoteCreateRequest, 'customerId' | 'discountPercentage'> & {
  discountPercentage: number
}

export interface ItemRequest {
  name: string
  quantity: number
  materialId: number
  printerId: number
  weightGrams: number
  printTimeMinutes: number
  failureRiskPercentage: number
  manualUnitPrice: number | null
  additionalCharges: ChargeRequest[]
}

export interface ChargeRequest {
  description: string
  amount: number
}

export interface QuoteDuplicateRequest {
  validUntil: string
  estimatedDeliveryDate: string | null
}

export type ProductionOrderStatus = 'PENDING' | 'IN_PRODUCTION' | 'READY' | 'DELIVERED' | 'CANCELLED'
export type ProductionItemStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'BLOCKED'
export type ProductionPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'

export interface ProductionOrderItem {
  id: number
  quoteItemId: number
  name: string
  quantity: number
  materialName: string
  printerName: string
  printerModel: string | null
  weightGrams: number
  printTimeMinutes: number
  assignedPrinter: Pick<Printer, 'id' | 'name' | 'model'> | null
  completedQuantity: number
  status: ProductionItemStatus
  notes: string | null
  version: number
}

export interface ProductionOrder {
  id: number
  orderNumber: string
  quote: Pick<QuoteSummary, 'id' | 'quoteNumber' | 'title' | 'customer'>
  status: ProductionOrderStatus
  priority: ProductionPriority
  dueDate: string | null
  notes: string | null
  createdAt: string
  updatedAt: string
  startedAt: string | null
  readyAt: string | null
  deliveredAt: string | null
  cancelledAt: string | null
  items: ProductionOrderItem[]
}

export interface ProductionOrderCreateRequest {
  dueDate?: string | null
  priority?: ProductionPriority | null
  notes?: string | null
}

export interface ProductionItemUpdateRequest {
  assignedPrinterId?: number
  status?: ProductionItemStatus
  completedQuantity?: number
  notes?: string | null
  expectedVersion: number
}

export interface DashboardOrderSummary {
  id: number
  orderNumber: string
  quoteId: number
  quoteNumber: string
  customerName: string
  status: ProductionOrderStatus
  priority: ProductionPriority
  dueDate: string | null
}

export interface Dashboard {
  quoteCounts: Record<QuoteStatus, number>
  sentPipelineAmount: number
  acceptedRevenue: number
  acceptedEstimatedProfit: number
  acceptanceRate: number
  productionCounts: Record<ProductionOrderStatus, number>
  lowStockMaterials: DashboardLowStockMaterial[]
  printerCounts: Record<PrinterOperationalStatus, number>
  dueSoonOrders: DashboardOrderSummary[]
  overdueOrders: DashboardOrderSummary[]
  recentQuotes: QuoteSummary[]
  updatedAt: string
}

export interface DashboardLowStockMaterial {
  id: number
  name: string
  materialType: string | null
  brand: string | null
  color: string | null
  stockGrams: number
  lowStockThresholdGrams: number
}

export interface CustomerOverview {
  customer: Customer
  quoteCounts: Record<QuoteStatus, number>
  totalAcceptedRevenue: number
  totalEstimatedProfit: number
  recentQuotes: QuoteSummary[]
  productionOrderCount: number
}
