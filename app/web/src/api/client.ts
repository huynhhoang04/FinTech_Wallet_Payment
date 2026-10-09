import { sign } from './hmac'
import { recordLog } from './log'

export type Method = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'

export type ApiCall<T> = {
  status: number
  ok: boolean
  requestId: string | null
  stringToSign: string
  signature: string
  body: T
  ms: number
}

/** Carries the backend error contract from docs 04 section 0.4. */
export class ApiError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly status: number,
    readonly requestId: string | null,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export function isApiError(value: unknown): value is ApiError {
  return value instanceof ApiError
}

function parse(text: string): unknown {
  if (text === '') return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

function codeOf(body: unknown): string | null {
  if (body && typeof body === 'object' && 'code' in body) {
    return String((body as { code: unknown }).code)
  }
  return null
}

function messageOf(body: unknown): string | null {
  if (body && typeof body === 'object' && 'message' in body) {
    return String((body as { message: unknown }).message)
  }
  return null
}

/** HMAC-signed call (docs 04 section 0.3). Signature is only enforced by the gateway; services ignore it. */
export async function call<T>(
  method: Method,
  path: string,
  payload?: unknown,
  token?: string,
  note?: string,
): Promise<ApiCall<T>> {
  const rawBody = payload === undefined ? '' : JSON.stringify(payload)
  const signed = await sign(method, path, rawBody)

  const headers: Record<string, string> = { ...signed.headers }
  if (payload !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers['Authorization'] = `Bearer ${token}`

  const started = performance.now()
  let response: Response
  try {
    response = await fetch(path, {
      method,
      headers,
      body: payload === undefined ? undefined : rawBody,
    })
  } catch (cause) {
    const ms = Math.round(performance.now() - started)
    recordLog({
      method,
      path,
      status: 0,
      ok: false,
      ms,
      requestId: null,
      stringToSign: signed.stringToSign,
      signature: signed.signature,
      code: 'NETWORK_ERROR',
      note,
    })
    throw new ApiError('NETWORK_ERROR', `Không kết nối được ${path} (${String(cause)})`, 0, null)
  }

  const ms = Math.round(performance.now() - started)
  const requestId = response.headers.get('X-Request-Id')
  const text = response.status === 204 ? '' : await response.text()
  const body = parse(text) as T
  const code = response.ok ? null : codeOf(body)

  recordLog({
    method,
    path,
    status: response.status,
    ok: response.ok,
    ms,
    requestId,
    stringToSign: signed.stringToSign,
    signature: signed.signature,
    code,
    note,
  })

  if (!response.ok) {
    throw new ApiError(code ?? 'HTTP_ERROR', messageOf(body) ?? `HTTP ${response.status}`, response.status, requestId)
  }
  return { status: response.status, ok: response.ok, requestId, stringToSign: signed.stringToSign, signature: signed.signature, body, ms }
}

/** Deliberately unsigned, for the "missing signature → 400" demo through the gateway. */
export async function callUnsigned<T>(method: Method, path: string, payload?: unknown, token?: string): Promise<ApiCall<T>> {
  const rawBody = payload === undefined ? '' : JSON.stringify(payload)
  const headers: Record<string, string> = {}
  if (payload !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers['Authorization'] = `Bearer ${token}`

  const started = performance.now()
  const response = await fetch(path, { method, headers, body: payload === undefined ? undefined : rawBody })
  const ms = Math.round(performance.now() - started)
  const body = parse(response.status === 204 ? '' : await response.text()) as T
  const requestId = response.headers.get('X-Request-Id')
  recordLog({
    method,
    path,
    status: response.status,
    ok: response.ok,
    ms,
    requestId,
    stringToSign: '(không ký)',
    signature: '(không có)',
    code: response.ok ? null : codeOf(body),
    note: 'unsigned demo',
  })
  return { status: response.status, ok: response.ok, requestId, stringToSign: '(không ký)', signature: '(không có)', body, ms }
}

/** Builds a path with a sorted query string (matches the gateway canonical query). */
export function withQuery(path: string, query: Record<string, string | number | undefined | null>): string {
  const params = Object.entries(query)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .sort(([a], [b]) => a.localeCompare(b))
  if (params.length === 0) return path
  const search = params.map(([key, value]) => `${key}=${encodeURIComponent(String(value))}`).join('&')
  return `${path}?${search}`
}
