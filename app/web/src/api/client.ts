import { sign } from './hmac'

export type ApiCall = {
  status: number
  ok: boolean
  requestId: string | null
  stringToSign: string
  signature: string
  body: unknown
}

export async function call(
  method: 'GET' | 'POST',
  path: string,
  payload?: unknown,
  token?: string,
): Promise<ApiCall> {
  const rawBody = payload === undefined ? '' : JSON.stringify(payload)
  const signed = await sign(method, path, rawBody)

  const headers: Record<string, string> = { ...signed.headers }
  if (payload !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const response = await fetch(path, {
    method,
    headers,
    body: payload === undefined ? undefined : rawBody,
  })

  return {
    status: response.status,
    ok: response.ok,
    requestId: response.headers.get('X-Request-Id'),
    stringToSign: signed.stringToSign,
    signature: signed.signature,
    body: parse(response.status === 204 ? '' : await response.text()),
  }
}

function parse(text: string): unknown {
  if (text === '') {
    return null
  }
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}
