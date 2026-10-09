import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { ApiError, call, callUnsigned, type ApiCall, type Method } from '../api/client'
import type { LoginResponse, RegisterResponse, UserProfile } from '../api/types'

const STORAGE_KEY = 'wallet.tokens'

export type Tokens = { accessToken: string; refreshToken: string }
export type LoginInput = { phone: string; password: string }
export type RegisterInput = { phone: string; email: string; password: string; fullName: string }

type AuthContextValue = {
  tokens: Tokens | null
  user: UserProfile | null
  isAdmin: boolean
  busy: boolean
  login(input: LoginInput): Promise<void>
  register(input: RegisterInput): Promise<RegisterResponse>
  logout(): void
  reloadUser(): Promise<void>
  call<T>(method: Method, path: string, payload?: unknown, note?: string): Promise<ApiCall<T>>
  callUnsigned<T>(method: Method, path: string, payload?: unknown): Promise<ApiCall<T>>
}

const AuthContext = createContext<AuthContextValue | null>(null)

function decodeRole(token: string): string | null {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))) as { role?: unknown }
    return typeof json.role === 'string' ? json.role : null
  } catch {
    return null
  }
}

function loadStored(): Tokens | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as Tokens) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [tokens, setTokens] = useState<Tokens | null>(loadStored)
  const [user, setUser] = useState<UserProfile | null>(null)
  const [busy, setBusy] = useState(false)
  const tokensRef = useRef(tokens)
  tokensRef.current = tokens

  const persist = useCallback((next: Tokens | null) => {
    setTokens(next)
    tokensRef.current = next
    if (next) localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    else localStorage.removeItem(STORAGE_KEY)
  }, [])

  const setSession = useCallback(
    (response: LoginResponse) => {
      persist({ accessToken: response.accessToken, refreshToken: response.refreshToken })
    },
    [persist],
  )

  const logout = useCallback(() => {
    persist(null)
    setUser(null)
  }, [persist])

  const refresh = useCallback(async () => {
    const current = tokensRef.current
    if (!current) throw new ApiError('UNAUTHENTICATED', 'Chưa đăng nhập', 401, null)
    const result = await call<LoginResponse>('POST', '/api/v1/auth/refresh', { refreshToken: current.refreshToken }, undefined, 'refresh')
    setSession(result.body)
    return result.body
  }, [setSession])

  const rawCall = useCallback(
    async <T,>(method: Method, path: string, payload?: unknown, note?: string): Promise<ApiCall<T>> => {
      const token = tokensRef.current?.accessToken
      try {
        return await call<T>(method, path, payload, token, note)
      } catch (error) {
        // Access token lives 15 minutes; transparently refresh once, then retry (docs 04 section 2.3).
        if (error instanceof ApiError && error.status === 401 && tokensRef.current?.refreshToken) {
          try {
            await refresh()
          } catch {
            logout()
            throw error
          }
          return call<T>(method, path, payload, tokensRef.current?.accessToken, note)
        }
        throw error
      }
    },
    [refresh, logout],
  )

  const reloadUser = useCallback(async () => {
    if (!tokensRef.current) {
      setUser(null)
      return
    }
    const result = await rawCall<UserProfile>('GET', '/api/v1/auth/me', undefined, 'me')
    setUser(result.body)
  }, [rawCall])

  const login = useCallback(
    async (input: LoginInput) => {
      setBusy(true)
      try {
        const result = await call<LoginResponse>('POST', '/api/v1/auth/login', input, undefined, 'login')
        setSession(result.body)
        const profile = await call<UserProfile>('GET', '/api/v1/auth/me', undefined, result.body.accessToken, 'me')
        setUser(profile.body)
      } finally {
        setBusy(false)
      }
    },
    [setSession],
  )

  const register = useCallback(async (input: RegisterInput) => {
    setBusy(true)
    try {
      const result = await call<RegisterResponse>('POST', '/api/v1/auth/register', input, undefined, 'register')
      return result.body
    } finally {
      setBusy(false)
    }
  }, [])

  useEffect(() => {
    if (tokensRef.current) {
      reloadUser().catch(() => logout())
    }
  }, [reloadUser, logout])

  const isAdmin = useMemo(() => {
    const role = tokens ? decodeRole(tokens.accessToken) : null
    return role === 'ROLE_ADMIN' || role === 'ADMIN'
  }, [tokens])

  const value: AuthContextValue = {
    tokens,
    user,
    isAdmin,
    busy,
    login,
    register,
    logout,
    reloadUser,
    call: rawCall,
    callUnsigned: (method, path, payload) => callUnsigned(method, path, payload, tokensRef.current?.accessToken),
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}
