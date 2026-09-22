import { Box } from 'lucide-react'

export function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <div className="brand" aria-label="PrintVerse">
      <span className="brand-mark" aria-hidden="true">
        <Box size={25} strokeWidth={1.7} />
        <span />
      </span>
      {!compact && (
        <span className="brand-type">
          PRINT<span>VERSE</span>
          <small>GESTIÓN DE IMPRESIÓN 3D</small>
        </span>
      )}
    </div>
  )
}
