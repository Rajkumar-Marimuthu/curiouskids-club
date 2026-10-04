import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Same origin as production (CloudFront serves /api), so the session cookie stays first-party.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
