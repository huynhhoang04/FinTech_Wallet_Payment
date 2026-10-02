import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Proxying keeps the browser on one origin (no CORS preflight, which would reach the gateway
// without an HMAC signature) and preserves the path, so the signature stays valid.
// Point the target at http://localhost:8082 to call the ledger directly, skipping the gateway.
const target = 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target, changeOrigin: true },
      '/webhooks': { target, changeOrigin: true },
    },
  },
})
