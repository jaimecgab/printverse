import type { ProductionItemStatus, ProductionOrderStatus } from '../types'
import { productionItemStatusLabels, productionStatusLabels } from '../utils/production'

export function ProductionStatusBadge({ status }: { status: ProductionOrderStatus }) {
  return <span className={`status production-status production-status--${status.toLowerCase()}`}>{productionStatusLabels[status]}</span>
}

export function ProductionItemStatusBadge({ status }: { status: ProductionItemStatus }) {
  return <span className={`status production-item-status production-item-status--${status.toLowerCase()}`}>{productionItemStatusLabels[status]}</span>
}
