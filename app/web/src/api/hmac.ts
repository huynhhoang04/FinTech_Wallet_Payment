// HMAC-SHA256 request signing, docs 04 section 0.3:
//   stringToSign = METHOD \n PATH \n CANONICAL_QUERY \n TIMESTAMP \n SHA256_HEX(rawBody)
// The canonical string is never sent; the gateway rebuilds it and compares signatures.
// NOTE: this dev client carries the shared dev client secret. The real Expo app signs with
// crypto-js (docs 01 section 5) and gets its secret from configuration, not from source.
const CLIENT_SECRET = import.meta.env?.VITE_HMAC_SECRET ?? 'dev-only-hmac-secret'

const encoder = new TextEncoder()

export type SignedRequest = {
  headers: Record<string, string>
  stringToSign: string
  signature: string
}

export async function sign(method: string, path: string, rawBody: string): Promise<SignedRequest> {
  const timestamp = Math.floor(Date.now() / 1000).toString()
  const bodyHash = hex(await crypto.subtle.digest('SHA-256', encoder.encode(rawBody)))
  const stringToSign = [method.toUpperCase(), path, '', timestamp, bodyHash].join('\n')
  const signature = await hmacSha256(stringToSign)

  return {
    headers: { 'X-Timestamp': timestamp, 'X-Signature': signature },
    stringToSign,
    signature,
  }
}

async function hmacSha256(message: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    'raw',
    encoder.encode(CLIENT_SECRET),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  )
  return hex(await crypto.subtle.sign('HMAC', key, encoder.encode(message)))
}

function hex(buffer: ArrayBuffer): string {
  return Array.from(new Uint8Array(buffer), (byte) => byte.toString(16).padStart(2, '0')).join('')
}
