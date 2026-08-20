import type { QuoteStatus } from '../types'
import { statusLabels } from '../utils/format'

export function StatusBadge({ status }: { status: QuoteStatus }) {
  return <span className={`status status--${status.toLowerCase()}`}>{statusLabels[status]}</span>
}
