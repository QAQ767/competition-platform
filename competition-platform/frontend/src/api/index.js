import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { useUserStore } from '../stores/user'
import { disconnectWebSocket, refreshWsConnection } from '../utils/websocket'

const api = axios.create({ baseURL: '/api', timeout: 15000 })
let refreshing = null

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

function expireSession() {
  const hadSession = !!localStorage.getItem('token')
  const store = useUserStore()
  store.token = ''
  store.refreshToken = ''
  store.user = null
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('user')
  disconnectWebSocket()
  const current = router.currentRoute.value
  if (current.path !== '/login')
    router.replace({ path: '/login', query: { redirect: current.fullPath } })
  if (hadSession) ElMessage.warning('登录已过期，请重新登录')
}

function refreshAccessToken() {
  if (refreshing) return refreshing
  const previous = localStorage.getItem('refreshToken')
  if (!previous) return Promise.resolve(null)
  refreshing = axios
    .post('/api/auth/refresh', { refreshToken: previous }, { timeout: 15000 })
    .then((res) => {
      if (
        res.data?.code !== 200 ||
        !res.data.data?.accessToken ||
        localStorage.getItem('refreshToken') !== previous
      )
        return null
      const auth = res.data.data
      const store = useUserStore()
      store.token = auth.accessToken
      store.refreshToken = auth.refreshToken
      localStorage.setItem('token', auth.accessToken)
      localStorage.setItem('refreshToken', auth.refreshToken)
      refreshWsConnection()
      return auth.accessToken
    })
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

async function handleUnauthorized(config, error) {
  if (/^\/auth\/(login|register|refresh)$/.test(config?.url || '')) {
    ElMessage.error(
      error.response?.data?.message || error.message || '账号或密码不正确'
    )
    throw error
  }
  if (config && !config._retry) {
    config._retry = true
    try {
      const current = localStorage.getItem('token')
      // 同一批失败请求可能晚于刷新完成才返回，优先复用已经更新的 token。
      const token =
        current && config.headers.Authorization !== 'Bearer ' + current
          ? current
          : await refreshAccessToken()
      if (token) {
        config.headers.Authorization = 'Bearer ' + token
        return api(config)
      }
    } catch {
      /* 刷新失败时统一清理界面与本地登录态。 */
    }
  }
  expireSession()
  throw error
}

api.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body?.code === undefined) return body
    if (body.code === 200) return body.data
    const error = new Error(body.message || '请求失败')
    if (body.code === 401) return handleUnauthorized(res.config, error)
    ElMessage.error(
      body.message || (body.code === 403 ? '没有权限执行该操作' : '请求失败')
    )
    return Promise.reject(error)
  },
  (error) => {
    if (error.response?.status === 401)
      return handleUnauthorized(error.config, error)
    ElMessage.error(
      error.response?.data?.message ||
        (error.response?.status === 403
          ? '没有权限执行该操作'
          : '网络连接失败，请稍后重试')
    )
    return Promise.reject(error)
  }
)

export default api
