const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')

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
    'A quote must contain at least one item before it can be sent': 'La cotización necesita al menos una pieza antes de enviarse.',
    'Only DRAFT quotes can be modified': 'Sólo las cotizaciones en borrador se pueden modificar.',
  }
  if (exact[message]) return exact[message]
  if (/^Material \d+ is inactive$/.test(message)) return 'El material seleccionado está inactivo.'
  if (/^Printer \d+ is inactive$/.test(message)) return 'La impresora seleccionada está inactiva.'
  if (/^Quote status cannot change/.test(message)) return 'Ese cambio de estado no está permitido.'
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
  errors: Record<string, string>

  constructor(status: number, problem: ProblemDetail) {
    super(spanishMessage(problem.detail, 'No fue posible completar la operación.'))
    this.name = 'ApiError'
    this.status = status
    this.title = problem.title || 'Error de solicitud'
    this.errors = Object.fromEntries(Object.entries(problem.errors || {}).map(([field, message]) => [field, spanishFieldError(message)]))
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers: {
        Accept: 'application/json',
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...options.headers,
      },
    })
  } catch {
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
    throw new ApiError(response.status, problem)
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export const http = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', ...(body === undefined ? {} : { body: JSON.stringify(body) }) }),
  put: <T>(path: string, body: unknown) => request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) => request<T>(path, { method: 'PATCH', body: JSON.stringify(body) }),
  delete: (path: string) => request<void>(path, { method: 'DELETE' }),
}
