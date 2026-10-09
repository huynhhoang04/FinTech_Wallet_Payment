import { useSyncExternalStore } from 'react'

export type LogEntry = {
  id: string
  at: string
  method: string
  path: string
  status: number
  ok: boolean
  ms: number
  requestId: string | null
  stringToSign: string
  signature: string
  code: string | null
  note?: string
}

let entries: LogEntry[] = []
const listeners = new Set<() => void>()

const emit = () => listeners.forEach((listener) => listener())

export function recordLog(entry: Omit<LogEntry, 'id' | 'at'>): void {
  entries = [{ ...entry, id: crypto.randomUUID(), at: new Date().toISOString() }, ...entries].slice(0, 300)
  emit()
}

export function clearLog(): void {
  entries = []
  emit()
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

const snapshot = () => entries

export function useRequestLog(): LogEntry[] {
  return useSyncExternalStore(subscribe, snapshot, snapshot)
}
