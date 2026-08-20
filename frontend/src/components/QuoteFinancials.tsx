import type { Quote, QuoteItem } from '../types'
import { currency, number } from '../utils/format'

export function QuoteFinancials({ quote }: { quote: Quote }) {
  return (
    <section className="financial-grid" aria-label="Resumen financiero">
      <div className="financial-card">
        <span>Costo interno</span>
        <strong>{currency.format(quote.internalCost)}</strong>
        <small>Material, máquina, riesgo y extras</small>
      </div>
      <div className="financial-card">
        <span>Subtotal final</span>
        <strong>{currency.format(quote.finalSubtotal)}</strong>
        <small>Antes de descuento e IVA</small>
      </div>
      <div className="financial-card financial-card--profit">
        <span>Ganancia estimada</span>
        <strong>{currency.format(quote.estimatedProfit)}</strong>
        <small>Margen real {number.format(quote.realMarginPercentage)}%</small>
      </div>
      <div className="financial-card financial-card--total">
        <span>Total cotizado</span>
        <strong>{currency.format(quote.total)}</strong>
        <small>{quote.taxEnabled ? `Incluye ${number.format(quote.taxPercentage)}% de IVA` : 'Sin IVA'}</small>
      </div>
    </section>
  )
}

export function ItemBreakdown({ item }: { item: QuoteItem }) {
  return (
    <div className="item-breakdown">
      <dl>
        <div><dt>Material / unidad</dt><dd>{currency.format(item.materialCostUnit)}</dd></div>
        <div><dt>Máquina / unidad</dt><dd>{currency.format(item.machineCostUnit)}</dd></div>
        <div><dt>Riesgo / unidad</dt><dd>{currency.format(item.failureRiskCostUnit)}</dd></div>
        <div><dt>Cargos / unidad</dt><dd>{currency.format(item.additionalChargesUnit)}</dd></div>
      </dl>
      <dl className="breakdown-totals">
        <div><dt>Costo interno unitario</dt><dd>{currency.format(item.internalCostUnit)}</dd></div>
        <div><dt>Precio sugerido</dt><dd>{currency.format(item.suggestedPriceUnit)}</dd></div>
        <div><dt>Precio final unitario</dt><dd>{currency.format(item.finalUnitPrice)}</dd></div>
        <div><dt>Subtotal pieza</dt><dd>{currency.format(item.itemSubtotal)}</dd></div>
      </dl>
    </div>
  )
}
