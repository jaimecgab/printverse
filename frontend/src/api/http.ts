import { getAccessToken, invalidateSession } from '../auth/session'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

interface ProblemDetail {
  title?: string
  detail?: string
  status?: number
  errors?: Record<string, string>
}

function spanishMessage(message: string | undefined, fallback: string): string {
  if (!message) return fallback
  const exact: Record<string, string> = {
    'One or more fields are invalid': 'Uno o más campos no son válidos.',
    'The request body is missing or contains invalid values': 'La solicitud está incompleta o contiene valores no válidos.',
    'The operation conflicts with existing data': 'La operación entra en conflicto con datos existentes.',
    'The quote is being modified by another request; retry the operation': 'Otra operación está modificando la cotización. Inténtalo de nuevo.',
    'The resource is being modified by another request; retry the operation': 'Otra operación está modificando este recurso. Actualiza los datos e inténtalo de nuevo.',
    'A quote must contain at least one item before it can be sent': 'La cotización necesita al menos una pieza antes de enviarse.',
    'Only DRAFT quotes can be modified': 'Sólo las cotizaciones en borrador se pueden modificar.',
    'All production order items must be COMPLETED before marking the order READY': 'Completa todas las piezas antes de marcar la orden como lista.',
    'Invalid username or password': 'Usuario o contraseña incorrectos.',
    'A valid Bearer access token is required': 'Tu sesión no es válida o ha expirado. Inicia sesión de nuevo.',
    'You do not have permission to perform this operation': 'No tienes permisos para realizar esta operación.',
  }
  if (exact[message]) return exact[message]
  if (/^Material \d+ is inactive$/.test(message)) return 'El material seleccionado está inactivo.'
  if (/^Printer \d+ is inactive$/.test(message)) return 'La impresora seleccionada está inactiva.'
  if (/^Printer \d+ is (MAINTENANCE|OUT_OF_SERVICE) and cannot (be assigned|start production)$/.test(message)) return 'La impresora ya no está disponible para nuevas asignaciones.'
  if (/printer.*(already )?occupied|occupied.*printer/i.test(message)) return 'La impresora ya está ocupada por otra pieza en proceso.'
  if (/IN_PROGRESS.*(requires|must have).*printer|printer.*required.*IN_PROGRESS/i.test(message)) return 'Una pieza en proceso debe tener una impresora asignada.'
  if (/IN_PROGRESS.*(order|IN_PRODUCTION)|(order|Production order).*IN_PRODUCTION.*IN_PROGRESS/i.test(message)) return 'La orden debe estar en producción antes de iniciar una pieza.'
  if (/IN_PROGRESS.*BLOCKED.*reassigning.*printer|cannot change.*printer.*IN_PROGRESS|printer.*cannot be changed.*IN_PROGRESS/i.test(message)) return 'Bloquea la pieza antes de cambiar la impresora asignada.'
  if (/item status cannot change|invalid.*item.*transition/i.test(message)) return 'Ese cambio de estado de la pieza no está permitido.'
  if (/BUSY.*managed.*production|managed.*BUSY/i.test(message)) return 'El estado Ocupada se gestiona automáticamente desde producción.'
  if (/BUSY printer.*(must remain BUSY|cannot be archived)/i.test(message)) return 'La impresora no puede cambiar de estado ni archivarse mientras está ocupada.'
  if (/Production order item changed after it was loaded/i.test(message)) return 'La pieza cambió en otra sesión. Actualiza la orden antes de intentarlo de nuevo.'
  if (/^Quote status cannot change/.test(message)) return 'Ese cambio de estado no está permitido.'
  if (/^Production order status cannot change/.test(message)) return 'Ese cambio de estado de producción no está permitido.'
  if (/^Items cannot be modified when the production order/.test(message)) return 'Las piezas ya no se pueden modificar en el estado actual de la orden.'
  if (/^Completed quantity must be between/.test(message)) return 'La cantidad completada está fuera del rango permitido.'
  if (/^A COMPLETED item must have its full quantity completed$/.test(message)) return 'Una pieza completa debe tener todas sus unidades terminadas.'
  if (/^A PENDING item cannot have completed units$/.test(message)) return 'Una pieza pendiente no puede tener unidades terminadas.'
  if (/^An item with its full quantity completed must be COMPLETED$/.test(message)) return 'Una pieza con todas sus unidades terminadas debe marcarse como completa.'
  if (/^A material named '.+' already exists$/.test(message)) return 'Ya existe un material con ese nombre.'
  if (/^A printer named '.+' already exists$/.test(message)) return 'Ya existe una impresora con ese nombre.'
  if (/was not found/.test(message)) return 'El recurso solicitado no existe o ya no está disponible.'
  return fallback
}

function spanishFieldError(message: string): string {
  if (message === 'must not be blank' || message === 'must not be null') return 'Este campo es obligatorio.'
  if (message === 'must be a future or present date') return 'La fecha debe ser hoy o posterior.'
  if (message === 'must be a well-formed email address') return 'Escribe un correo electrónico válido.'
  if (message === 'must be greater than 0') return 'El valor debe ser mayor que cero.'
  if (message === 'must be greater than or equal to 0') return 'El valor no puede ser negativo.'
  if (message.startsWith('size must be between')) return 'La longitud del texto está fuera del límite permitido.'
  if (message.startsWith('must be less than or equal to')) return 'El valor supera el máximo permitido.'
  if (message.startsWith('must be greater than or equal to')) return 'El valor es menor al mínimo permitido.'
  return message
}

export class ApiError extends Error {
  status: number
  title: string
  detail: string
  errors: Record<string, string>

  constructor(status: number, problem: ProblemDetail) {
    super(spanishMessage(problem.detail, 'No fue posible completar la operación.'))
    this.name = 'ApiError'
    this.status = status
    this.title = problem.title || 'Error de solicitud'
    this.detail = problem.detail || ''
    this.errors = Object.fromEntries(Object.entries(problem.errors || {}).map(([field, message]) => [field, spanishFieldError(message)]))
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    const headers = new Headers(options.headers)
    headers.set('Accept', 'application/json')
    if (options.body) headers.set('Content-Type', 'application/json')
    const token = getAccessToken()
    if (token) headers.set('Authorization', `Bearer ${token}`)
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers,
    })
  } catch (caught) {
    if (caught instanceof DOMException && caught.name === 'AbortError') throw caught
    throw new ApiError(0, {
      title: 'Sin conexión',
      detail: 'No se pudo conectar con el servidor. Verifica que la API esté disponible.',
    })
  }

  if (!response.ok) {
    let problem: ProblemDetail = { detail: `La solicitud falló con código ${response.status}.` }
    try {
      problem = (await response.json()) as ProblemDetail
    } catch {
      // Preserve the generic message when the server does not return ProblemDetail JSON.
    }
    if (response.status === 401) invalidateSession()
    throw new ApiError(response.status, problem)
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export interface DownloadedFile {
  blob: Blob
  fileName: string
}

function safeFileName(disposition: string | null, fallback: string): string {
  const encoded = disposition?.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  const quoted = disposition?.match(/filename="([^"]+)"/i)?.[1]
  let value = quoted
  if (encoded) {
    try { value = decodeURIComponent(encoded.replace(/^"|"$/g, '')) } catch { value = quoted }
  }
  if (!value) value = fallback
  const sanitized = Array.from(value, (character) => {
    const code = character.charCodeAt(0)
    return character === '/' || character === '\\' || code < 32 || code === 127 ? '_' : character
  }).join('')
  return sanitized.replace(/^\.+/, '') || fallback
}

async function download(path: string, fallbackName: string, signal?: AbortSignal): Promise<DownloadedFile> {
  let response: Response
  try {
    const headers = new Headers({ Accept: 'application/pdf' })
    const token = getAccessToken()
    if (token) headers.set('Authorization', `Bearer ${token}`)
    response = await fetch(`${API_BASE_URL}${path}`, { headers, signal })
  } catch (caught) {
    if (caught instanceof DOMException && caught.name === 'AbortError') throw caught
    throw new ApiError(0, { title: 'Sin conexión', detail: 'No se pudo conectar con el servidor. Verifica que la API esté disponible.' })
  }
  if (!response.ok) {
    let problem: ProblemDetail = { detail: `La solicitud falló con código ${response.status}.` }
    try { problem = (await response.json()) as ProblemDetail } catch { /* The PDF endpoint may return an empty error body. */ }
    if (response.status === 401) invalidateSession()
    throw new ApiError(response.status, problem)
  }
  return { blob: await response.blob(), fileName: safeFileName(response.headers.get('Content-Disposition'), fallbackName) }
}

export const http = {
  get: <T>(path: string, signal?: AbortSignal) => request<T>(path, { signal }),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', ...(body === undefined ? {} : { body: JSON.stringify(body) }) }),
  put: <T>(path: string, body: unknown) => request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) => request<T>(path, { method: 'PATCH', body: JSON.stringify(body) }),
  delete: (path: string) => request<void>(path, { method: 'DELETE' }),
  download,
}
