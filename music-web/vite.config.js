import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  define: {
    global: 'globalThis'
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8089',
        changeOrigin: true
      },
      '/rest': {
        target: 'http://localhost:8089',
        changeOrigin: true
      },
      '/ws': {
        target: 'http://localhost:8089',
        ws: true,
        changeOrigin: true
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/naive-ui')) return 'naive-ui'
          if (id.includes('node_modules/vue') || id.includes('node_modules/vue-router') || id.includes('node_modules/pinia') || id.includes('node_modules/vue-i18n')) return 'vue-vendor'
          if (id.includes('node_modules/@vicons')) return 'icons'
          if (id.includes('node_modules/@stomp') || id.includes('node_modules/sockjs-client')) return 'stomp'
        },
      },
    },
  },
})
