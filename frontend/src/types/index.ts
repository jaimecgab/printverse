export type QuoteStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'REJECTED'

export interface Customer {
  id: number
  name: string
  phone: string
  email?: string | null
  notes?: string | null
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
  active: boolean
  createdAt: string
}

export interface MaterialRequest {
  name: string
  pricePerKg: number
  active: boolean
}

export interface Printer {
  id: number
  name: string
  model?: string | null
  costPerHour: number
  active: boolean
  createdAt: string
}

export interface PrinterRequest {
  name: string
  model: string | null
  costPerHour: number
  active: boolean
}

export interface CustomerSummary {
  id: number
  name: string
  phone: string
  email?: string | null
}

export interface QuoteSummary {
  id: number
  quoteNumber: string
  customer: CustomerSummary
  status: QuoteStatus
  createdAt: string
  validUntil: string
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
  printer: { id: number; name: string; model?: string | null }
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
  manualUnitPrice?: number | null
  finalUnitPrice: number
  itemSubtotal: number
  additionalCharges: Charge[]
}

export interface Quote extends QuoteSummary {
  estimatedDeliveryDate?: string | null
  depositPercentage?: number | null
  notes?: string | null
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
  estimatedProfit: number
  realMarginPercentage: number
  items: QuoteItem[]
}

export interface QuoteCreateRequest {
  customerId: number
  validUntil: string
  estimatedDeliveryDate: string | null
  depositPercentage: number | null
  notes: string | null
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
}

export interface ChargeRequest {
  description: string
  amount: number
}
