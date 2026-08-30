import { defineStore } from 'pinia'
import api from '../api'
import { connectWebSocket, disconnectWebSocket } from '../utils/websocket'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    refreshToken: localStorage.getItem('refreshToken') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null')
  }),
  getters: {
    isLogin: (s) => !!s.token,
    isAdmin: (s) => !!s.user && s.user.role === 'ADMIN'
  },
  actions: {
    async login(form) {
      const data = await api.post('/auth/login', form)
      this.setAuth(data)
    },
    async register(form) {
      const data = await api.post('/auth/register', form)
      this.setAuth(data)
    },
    setAuth(data) {
      this.token = data.accessToken
      this.refreshToken = data.refreshToken
      this.user = data.user
      localStorage.setItem('token', data.accessToken)
      localStorage.setItem('refreshToken', data.refreshToken)
      localStorage.setItem('user', JSON.stringify(data.user))
      connectWebSocket()
    },
    async fetchMe() {
      const user = await api.get('/auth/me')
      this.user = user
      localStorage.setItem('user', JSON.stringify(user))
    },
    setUser(user) {
      this.user = user
      localStorage.setItem('user', JSON.stringify(user))
    },
    async logout() {
      // 通知后端将当前 access token 加入黑名单（Redis），实现主动下线
      try {
        await api.post('/auth/logout')
      } catch (e) {
        /* 网络异常也照常清理本地登录态 */
      }
      disconnectWebSocket()
      this.token = ''
      this.refreshToken = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('user')
    }
  }
})
