import { readFileSync } from 'node:fs'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { API_PORT, DEV_PORT, PREVIEW_PORT } from './ports.mjs'

const escapeHtml = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c])

function brandTitle() {
  return {
    name: 'brand-title',
    transformIndexHtml: {
      order: 'pre',
      handler(html) {
        const brand = JSON.parse(readFileSync(new URL('./src/brand.json', import.meta.url), 'utf8'))
        return html.replaceAll('%BRAND_NAME%', escapeHtml(brand.name || 'Your App'))
      },
    },
  }
}

export default defineConfig({
  plugins: [react(), brandTitle()],
  server: {
    port: DEV_PORT,
    strictPort: true,
    proxy: {
      '/api': { target: `http://localhost:${process.env.API_PORT || API_PORT}`, changeOrigin: true },
    },
  },
  preview: { port: PREVIEW_PORT, strictPort: true },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
    include: ['src/**/*.test.{js,jsx}'],
  },
})
