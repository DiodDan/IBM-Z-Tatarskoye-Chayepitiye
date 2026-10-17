import type { ApiErrorBody } from '../types/api'

const BASE_URL = '/api'

export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  body?: unknown
  signal?: AbortSignal
}

/** The one place that talks to the backend. The user_id cookie is sent automatically. */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'

  let response: Response
  try {
    response = await fetch(BASE_URL + path, {
      method: options.method ?? 'GET',
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
      credentials: 'same-origin',
      signal: options.signal,
    })
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') throw err
    throw new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the server. Is the backend running?')
  }

  if (response.status === 204) return undefined as T

  if (!response.ok) {
    let body: Partial<ApiErrorBody> = {}
    try {
      body = await response.json()
    } catch {
      /* body was not JSON */
    }
    throw new ApiError(
      response.status,
      body.code ?? 'HTTP_' + response.status,
      body.message ?? `Request failed (${response.status})`,
    )
  }

  return (await response.json()) as T
}
