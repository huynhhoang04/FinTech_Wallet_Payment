---
name: frontend
description: React Native (Expo) client conventions for this repo - Expo Router screens, TanStack Query and Zustand state, the typed API client, HMAC request signing from the device, secure token storage, and integer money formatting. Use when editing app/, adding a screen, calling a backend endpoint, handling login tokens, or debugging a request that the gateway rejects.
license: Proprietary
metadata:
  project: fintech-wallet-payment
  version: "1.0"
  owner: devops-academy
---

# Frontend

The React Native client in `app/`. It is the **demo client** for the system - the DevOps story is
the priority, so prefer a plain, correct screen over a polished one. It is nevertheless the only
place where HMAC signing happens on a real device, so that part must be right.

API contract: `docs/04-api-contract.md`. The client talks **only** to the API Gateway.

## 1. Stack

Expo (managed workflow) + TypeScript · Expo Router · TanStack Query · Zustand ·
crypto-js (HMAC) · expo-secure-store (tokens).

Do not add a native module that requires a custom dev client unless it is unavoidable; keep the
managed workflow so `npx expo start` just works.

## 2. Structure

```
app/
├─ app.json / app.config.ts
├─ .env                    # EXPO_PUBLIC_API_BASE_URL=...  (not committed)
├─ .env.example
└─ src/
   ├─ app/                 # Expo Router routes
   │  ├─ (auth)/login.tsx
   │  ├─ (tabs)/index.tsx        # balance + wallets
   │  ├─ (tabs)/transfer.tsx
   │  ├─ (tabs)/history.tsx
   │  └─ _layout.tsx
   ├─ api/
   │  ├─ client.ts         # fetch wrapper: headers, signing, error mapping
   │  ├─ hmac.ts           # stringToSign + HMAC-SHA256
   │  ├─ endpoints.ts      # typed functions per endpoint
   │  └─ types.ts          # request/response types mirroring docs/04
   ├─ components/
   ├─ hooks/               # useBalance, useTransfer (TanStack Query)
   ├─ store/               # Zustand: auth, wallet
   └─ utils/               # formatMoney, uuid
```

## 3. API client rules

- Base URL comes from `EXPO_PUBLIC_API_BASE_URL`. No hardcoded hosts.
- Every money request sends:
  - `Authorization: Bearer <accessToken>`
  - `X-Timestamp` (unix seconds) and `X-Signature` (HMAC of the exact body)
  - `X-Idempotency-Key` (a UUID generated **once per operation**, reused on retry)
- Sign the exact body string that is sent. Serialize once into a variable, hash it, send that
  same string:

```ts
const body = JSON.stringify(payload);       // serialize ONCE
const ts = Math.floor(Date.now() / 1000).toString();
const sts = stringToSign(method, path, query, ts, body);
const signature = sign(secret, sts);

await fetch(url, {
  method,
  headers: {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${accessToken}`,
    'X-Timestamp': ts,
    'X-Signature': signature,
    'X-Idempotency-Key': idempotencyKey,
  },
  body,                                     // the SAME string that was hashed
});
```

- Generate the idempotency key **before** the request and keep it across retries; a new key per
  attempt defeats the whole mechanism.
- On `401`, refresh once via `/api/v1/auth/refresh`, then retry. On a second `401`, log out.

Details and a full client: [references/api-client.md](references/api-client.md).

## 4. Tokens

- Store access and refresh tokens in `expo-secure-store`. Never `AsyncStorage`, never a plain
  variable that survives logout.
- Access token lives in memory for the request path; refresh token is the durable one.
- Logout clears the secure store and the Zustand auth slice, and cancels in-flight queries.

## 5. Money on the client

- Amounts are **integers in the smallest unit** (VND = dong). Never parse a server amount with
  `parseFloat`.
- Format for display only: `new Intl.NumberFormat('vi-VN').format(amount) + ' ₫'`.
- Validate input: positive integer, within the limits the API enforces, so the user gets a clear
  message instead of a `422`.
- Show the fee and the resulting balance before confirming a transfer.

## 6. Errors

Map the backend `code` to a user message; never show a raw error string.

| `code` | Message |
|---|---|
| `INSUFFICIENT_BALANCE` | So du khong du |
| `FRAUD_BLOCKED` | Giao dich bi tam chan de kiem tra |
| `IDEMPOTENCY_CONFLICT` | Yeu cau khong hop le, vui long thu lai |
| `RATE_LIMITED` | Ban thao tac qua nhanh, thu lai sau |
| `UPSTREAM_UNAVAILABLE` | He thong dang ban, thu lai sau |
| `INVALID_SIGNATURE` | (dev only) signing mismatch - check canonical string and the raw body |

## 7. Screens (scope)

1. Login / register.
2. Wallet list with balances.
3. Transfer (source, target, amount, confirm).
4. History with status and the ledger breakdown for one transaction.

That is the whole app. Do not build settings, profiles, or theming.

## 8. Commands

```bash
cd app
npm install
npx expo start                 # QR code -> Expo Go on a device
npx expo start --tunnel        # when the VPS/phone are on different networks
npx tsc --noEmit               # type check
```

## 9. Definition of done for a client change

- [ ] Request is signed and carries a stable idempotency key for money operations.
- [ ] Tokens are in secure storage; logout clears them.
- [ ] Money is handled as an integer and formatted only for display.
- [ ] Backend error codes are mapped to messages.
- [ ] `npx tsc --noEmit` passes.
- [ ] The screen works against the **real** API on the VPS (milestone M6), not only a mock.

## 10. References

- [references/api-client.md](references/api-client.md) - the client wrapper, HMAC helper, and the
  signed-request flow in full.
- `docs/04-api-contract.md` sections 0.2-0.5 (headers, signature, errors, pagination).
