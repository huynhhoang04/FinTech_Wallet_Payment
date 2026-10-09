import { useAuth } from './AuthProvider'
import { useAsync, type AsyncState } from './useAsync'
import type { AccountInfo, BalanceInfo } from '../api/types'

export type AccountWithBalance = AccountInfo & { balance: BalanceInfo | null }

/** Accounts of the current user, each enriched with its balance (docs 04 sections 3.7-3.8). */
export function useAccounts(): AsyncState<AccountWithBalance[]> {
  const { call } = useAuth()
  return useAsync(async () => {
    const accounts = (await call<AccountInfo[]>('GET', '/api/v1/accounts', undefined, 'accounts')).body
    return Promise.all(
      accounts.map(async (account) => {
        try {
          const balance = (await call<BalanceInfo>('GET', `/api/v1/accounts/${account.accountId}/balance`, undefined, 'balance')).body
          return { ...account, balance }
        } catch {
          return { ...account, balance: null }
        }
      }),
    )
  }, [])
}
