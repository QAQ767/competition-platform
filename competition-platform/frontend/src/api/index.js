import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { refreshWsConnection } from '../utils/websocket'

/**
 * axios 实例：baseURL /api（开发期由 Vite 代理到 8080）
 * 认证：Authorization: Bearer <accessToken>（v2.0 JWT）
 * 401 时自动用 refreshToken 刷新一次并重试，失败则跳登录页
 */
const api = axios.create({
  baseURL: '/api',
  timeout: 15000
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

async function refreshToken() {
  const refreshToken = localStorage.getItem('refreshToken')
  if (!refreshToken) return null
  const res = await axios.post('/api/auth/refresh', { refreshToken })
  if (res.data && res.data.code === 200) {
    const auth = res.data.data
    localStorage.setItem('token', auth.accessToken)
    localStorage.setItem('refreshToken', auth.refreshToken)
    refreshWsConnection() // token 已更换，WebSocket 重连
    return auth.accessToken
  }
  return null
}

function forceLogout() {
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('user')
  router.push('/login')
}

api.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && body.code !== undefined) {
      if (body.code === 200) {
        return body.data
      }
      if (body.code === 401) {
        forceLogout()
        ElMessage.warning('登录已过期，请重新登录')
        return Promise.reject(new Error(body.message || '未登录'))
      }
      if (body.code === 403) {
        ElMessage.error(body.message || '没有权限')
        return Promise.reject(new Error(body.message || '没有权限'))
      }
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  async (err) => {
    const { response, config } = err
    // HTTP 401：尝试用 refresh token 刷新一次后重放原请求
    if (response && response.status === 401 && !config._retry) {
      config._retry = true
      try {
        const newToken = await refreshToken()
        if (newToken) {
          config.headers['Authorization'] = `Bearer ${newToken}`
          return api(config)
        }
      } catch (e) {
        /* 刷新失败，走登出 */
      }
      forceLogout()
      ElMessage.warning('登录已过期，请重新登录')
      return Promise.reject(err)
    }
    if (response && response.status === 403) {
      ElMessage.error('没有权限执行该操作')
      return Promise.reject(err)
    }
    ElMessage.error(err.message || '网络错误')
    return Promise.reject(err)
  }
)

export default api
