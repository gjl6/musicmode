import axios from 'axios'

const TOKEN_KEY = 'auth-token'
const REFRESH_KEY = 'auth-refresh-token'

const client = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// ---- 请求拦截器：自动附加 Bearer Token ----
client.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (err) => Promise.reject(err),
)

// ---- 响应拦截器：解包 data + 401 自动刷 Token ----
let isRefreshing = false
let failedQueue = []

function processQueue(error, token = null) {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) {
      reject(error)
    } else {
      resolve(token)
    }
  })
  failedQueue = []
}

export function getToken() {
  try {
    return localStorage.getItem(TOKEN_KEY) || null
  } catch {
    return null
  }
}

/** @deprecated 认证已迁移到 HttpOnly Cookie，不再需要在 URL 中拼接 token。 */
function getRefreshToken() {
  try {
    return localStorage.getItem(REFRESH_KEY) || null
  } catch {
    return null
  }
}

function saveToken(token) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch { /* ignore */ }
}

function saveRefreshToken(token) {
  try {
    if (token) localStorage.setItem(REFRESH_KEY, token)
    else localStorage.removeItem(REFRESH_KEY)
  } catch { /* ignore */ }
}

// 全局消息提示（Naive UI message 挂载在 window）
function showApiError(message) {
  try {
    if (window.$message) window.$message.error(message)
  } catch { /* 静默 — message provider 可能未挂载 */ }
}

client.interceptors.response.use(
  (res) => res.data,
  async (err) => {
    const originalRequest = err.config
    const msg = err?.response?.data?.message || err.message
    console.error('[API]', originalRequest?.url, err.response?.status, msg)

    // 403 — 权限不足，用户可见提示
    if (err.response?.status === 403) {
      showApiError(msg || '权限不足，无法执行此操作')
      return Promise.reject(err)
    }

    // 401 — 尝试刷新 Token
    if (err.response?.status === 401 && !originalRequest._retry) {
      const refresh = getRefreshToken()

      if (refresh && originalRequest.url !== '/auth/login'
          && originalRequest.url !== '/auth/refresh') {
        if (isRefreshing) {
          // 已有刷新进行中 — 排队等待
          return new Promise((resolve, reject) => {
            failedQueue.push({ resolve, reject })
          }).then((newToken) => {
            originalRequest.headers.Authorization = `Bearer ${newToken}`
            return client(originalRequest)
          })
        }

        originalRequest._retry = true
        isRefreshing = true

        try {
          const res = await client.post('/auth/refresh', { refreshToken: refresh })
          const newToken = res.accessToken
          saveToken(newToken)
          if (res.refreshToken) saveRefreshToken(res.refreshToken)
          processQueue(null, newToken)
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          return client(originalRequest)
        } catch (refreshErr) {
          processQueue(refreshErr, null)
          // 刷新失败 — 清理登录态
          saveToken(null)
          saveRefreshToken(null)
          // 通知 Pinia store 同步重置状态
          window.dispatchEvent(new CustomEvent('auth:session-expired'))
          // 跳转到登录页（仅当不在登录页时）
          if (window.location.hash !== '#/login') {
            window.location.hash = '#/login'
          }
          return Promise.reject(refreshErr)
        } finally {
          isRefreshing = false
        }
      }

      // 无 refreshToken 或已经是 auth 接口 — 清理并跳转
      saveToken(null)
      saveRefreshToken(null)
      // 通知 Pinia store 同步重置状态
      window.dispatchEvent(new CustomEvent('auth:session-expired'))
      if (window.location.hash !== '#/login') {
        window.location.hash = '#/login'
      }
    }

    return Promise.reject(err)
  },
)

export default client
