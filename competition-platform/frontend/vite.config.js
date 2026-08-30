import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 开发期代理到后端，前端代码不写死后端地址
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // WebSocket 实时聊天
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true
      }
    }
  }
})
