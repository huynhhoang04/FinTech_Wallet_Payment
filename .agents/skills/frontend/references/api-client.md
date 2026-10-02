# API Client and Signing (React Native)

Reference for the `frontend` skill. Full implementation of the signed-request flow.

## 1. Environment

`.env` (and `.env.example` with a placeholder):

```
EXPO_PUBLIC_API_BASE_URL=https://api.<domain>
```

Only `EXPO_PUBLIC_*` variables reach the app bundle. The HMAC secret is **per client** and must
not be a global backend secret; treat it as a device/app credential, not as something secret from
the app's own user.

## 2. HMAC helper (`src/api/hmac.ts`)

```ts
import CryptoJS from 'crypto-js';

export function sha256Hex(body: string): string {
  return CryptoJS.SHA256(body).toString(CryptoJS.enc.Hex);
}

export function stringToSign(
  method: string,
  path: string,
  canonicalQuery: string,
  timestamp: string,
  rawBody: string,
): string {
  return [method.toUpperCase(), path, canonicalQuery, timestamp, sha256Hex(rawBody)].join('\n');
}

export function sign(secret: string, payload: string): string {
  return CryptoJS.HmacSHA256(payload, secret).toString(CryptoJS.enc.Hex);
}

export function canonicalQuery(params: Record<string, string | undefined>): string {
  return Object.keys(params)
    .filter((k) => params[k] !== undefined && params[k] !== '')
    .sort()
    .map((k) => `${encodeURIComponent(k)}=${encodeURIComponent(params[k] as string)}`)
    .join('&');
}
```

## 3. Idempotency key (`src/utils/uuid.ts`)

```ts
export function uuidv4(): string {
  // crypto.randomUUID exists in modern Hermes; fall back to a v4 generator if not
  return globalThis.crypto?.randomUUID?.() ?? fallbackUuid();
}
```

Generate the key **once**, outside the retry loop:

```ts
const idempotencyKey = uuidv4();
await retry(() => transfer(payload, idempotencyKey));   // same key on every attempt
```

## 4. Client wrapper (`src/api/client.ts`)

```ts
import * as SecureStore from 'expo-secure-store';
import { sign, stringToSign, canonicalQuery } from './hmac';
import { uuidv4 } from '../utils/uuid';

const BASE = process.env.EXPO_PUBLIC_API_BASE_URL!;
const CLIENT_SECRET = process.env.EXPO_PUBLIC_HMAC_SECRET!;

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) {
    super(message);
  }
}

type Options = {
  method: 'GET' | 'POST' | 'PUT' | 'DELETE';
  path: string;
  query?: Record<string, string | undefined>;
  body?: unknown;
  auth?: boolean;
  idempotencyKey?: string;
};

export async function request<T>(opts: Options): Promise<T> {
  const query = canonicalQuery(opts.query ?? {});
  // Serialize ONCE - this exact string is both hashed and sent.
  const rawBody = opts.body === undefined ? '' : JSON.stringify(opts.body);
  const ts = Math.floor(Date.now() / 1000).toString();
  const signature = sign(CLIENT_SECRET, stringToSign(opts.method, opts.path, query, ts, rawBody));

  const headers: Record<string, string> = {
    'X-Timestamp': ts,
    'X-Signature': signature,
  };
  if (rawBody) headers['Content-Type'] = 'application/json';
  if (opts.idempotencyKey) headers['X-Idempotency-Key'] = opts.idempotencyKey;
  if (opts.auth !== false) {
    const token = await SecureStore.getItemAsync('accessToken');
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  const res = await fetch(`${BASE}${opts.path}${query ? `?${query}` : ''}`, {
    method: opts.method,
    headers,
    body: rawBody || undefined,
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new ApiError(res.status, err.code ?? 'UNKNOWN', err.message ?? res.statusText);
  }
  return res.status === 204 ? (undefined as T) : (await res.json()) as T;
}

export const money = {
  transfer: (payload: TransferRequest) =>
    request<TransferResponse>({
      method: 'POST',
      path: '/api/v1/transactions/transfer',
      body: payload,
      idempotencyKey: uuidv4(),
    }),
  deposit: (payload: DepositRequest) =>
    request<DepositResponse>({
      method: 'POST',
      path: '/api/v1/transactions/deposit',
      body: payload,
      idempotencyKey: uuidv4(),
    }),
};
```

## 5. Reads with TanStack Query

```ts
export function useBalance(accountId: string) {
  return useQuery({
    queryKey: ['balance', accountId],
    queryFn: () => request<BalanceResponse>({
      method: 'GET',
      path: `/api/v1/accounts/${accountId}/balance`,
    }),
    staleTime: 5_000,
  });
}
```

Reads are **not** signed with a money key and carry no idempotency key; they still carry the HMAC
headers because the gateway verifies every request.

## 6. Handling 401

```ts
let refreshing: Promise<void> | null = null;

async function ensureFreshToken() {
  if (!refreshing) {
    refreshing = (async () => {
      const refresh = await SecureStore.getItemAsync('refreshToken');
      const res = await request<TokenResponse>({
        method: 'POST',
        path: '/api/v1/auth/refresh',
        body: { refreshToken: refresh },
        auth: false,
      });
      await SecureStore.setItemAsync('accessToken', res.accessToken);
      await SecureStore.setItemAsync('refreshToken', res.refreshToken);
    })().finally(() => { refreshing = null; });
  }
  return refreshing;
}
```

Retry the original request once after a successful refresh; on failure, clear storage and route
to login.

## 7. Debugging a rejected request

| Symptom | Likely cause |
|---|---|
| `400 INVALID_SIGNATURE` | Body re-serialized after hashing; query not canonicalized; wrong timestamp unit |
| `401` right after login | Token not read from secure storage yet (async race) |
| `409 DUPLICATE_REQUEST` | Same key still in flight - the user double-tapped; disable the button while pending |
| `409 IDEMPOTENCY_CONFLICT` | Key regenerated for a changed payload - generate one key per operation |
| Works on emulator, fails on device | `localhost` base URL instead of the VPS/tunnel URL |

Log the `stringToSign` **only** in a dev build when hunting a signature bug, and remember to
remove it - it contains the body.
