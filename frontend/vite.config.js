import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发服务器：/api 代理转发到后端 8080
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
