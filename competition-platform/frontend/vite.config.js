import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const proxy = {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true
  },
  // 头像和附件地址由后端返回为 /uploads/...，必须与 API 一样转发。
  '/uploads': {
    target: 'http://localhost:8080',
    changeOrigin: true
  },
  '/ws': {
    target: 'ws://localhost:8080',
    ws: true
  }
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy
  },
  preview: { proxy }
})
