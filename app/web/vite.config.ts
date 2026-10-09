import { defineConfig, loadEnv, type ProxyOptions } from 'vite'
import react from '@vitejs/plugin-react'

// Local services (no docker-compose): each Spring service runs on its own port, so the dev
// proxy routes by path prefix. Set VITE_USE_GATEWAY=true to send /api and /webhooks through
// the api-gateway (:8080) instead — then HMAC signing is actually enforced by the gateway.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  const gateway = env.VITE_GATEWAY ?? 'http://localhost:8080'
  const useGateway = env.VITE_USE_GATEWAY === 'true'
  const internalToken = env.VITE_INTERNAL_TOKEN ?? 'dev-internal-token'

  const ledger = env.VITE_LEDGER ?? 'http://localhost:8082'
  const txengine = env.VITE_TXENGINE ?? 'http://localhost:8081'
  const payment = env.VITE_PAYMENT ?? 'http://localhost:8083'
  const notification = env.VITE_NOTIFICATION ?? 'http://localhost:8084'
  const audit = env.VITE_AUDIT ?? 'http://localhost:8085'

  // /internal/** needs X-Internal-Token. Injecting it here (Node side) keeps the dev token
  // out of the browser bundle.
  const withInternalToken = (target: string): ProxyOptions => ({
    target,
    changeOrigin: true,
    headers: { 'X-Internal-Token': internalToken },
  })

  const health = (target: string): ProxyOptions => ({
    target,
    changeOrigin: true,
    rewrite: () => '/actuator/health',
  })

  const apiProxy: Record<string, ProxyOptions> = useGateway
    ? { '/api': { target: gateway, changeOrigin: true }, '/webhooks': { target: gateway, changeOrigin: true } }
    : {
        '/api/v1/auth': { target: ledger, changeOrigin: true },
        '/api/v1/accounts': { target: txengine, changeOrigin: true },
        '/api/v1/transactions': { target: txengine, changeOrigin: true },
        '/api/v1/payments': { target: payment, changeOrigin: true },
        '/api/v1/notifications': { target: notification, changeOrigin: true },
        '/api/v1/audit': { target: audit, changeOrigin: true },
        '/api/v1/reconciliation': { target: audit, changeOrigin: true },
        '/webhooks': { target: payment, changeOrigin: true },
      }

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        ...apiProxy,
        '/internal/payments': withInternalToken(payment),
        '/internal/notifications': withInternalToken(notification),
        '/internal/transactions': withInternalToken(txengine),
        '/internal/accounts': withInternalToken(ledger),
        '/internal/ledger': withInternalToken(ledger),
        '/health/ledger': health(ledger),
        '/health/txengine': health(txengine),
        '/health/payment': health(payment),
        '/health/notification': health(notification),
        '/health/audit': health(audit),
      },
    },
  }
})
