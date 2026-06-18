import axios from 'axios'

const TOKEN_KEY = 'auth-token'
const REFRESH_KEY = 'auth-refresh-token'

const client = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

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
  } catch {  }
}

function saveRefreshToken(token) {
  try {
    if (token) localStorage.setItem(REFRESH_KEY, token)
    else localStorage.removeItem(REFRESH_KEY)
  } catch {  }
}

function showApiError(message) {
  try {
    if (window.$message) window.$message.error(message)
  } catch {  }
}

client.interceptors.response.use(
  (res) => res.data,
  async (err) => {
    const originalRequest = err.config
    const msg = err?.response?.data?.message || err.message
    console.error('[API]', originalRequest?.url, err.response?.status, msg)

        if (err.response?.status === 403) {
      showApiError(msg || '权限不足，无法执行此操作')
      return Promise.reject(err)
    }

        if (err.response?.status === 401 && !originalRequest._retry) {
      const refresh = getRefreshToken()

      if (refresh && originalRequest.url !== '/auth/login'
          && originalRequest.url !== '/auth/refresh') {
        if (isRefreshing) {
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
                    saveToken(null)
          saveRefreshToken(null)
                    if (window.location.hash !== '#/login') {
            window.location.hash = '#/login'
          }
          return Promise.reject(refreshErr)
        } finally {
          isRefreshing = false
        }
      }

            saveToken(null)
      saveRefreshToken(null)
      if (window.location.hash !== '#/login') {
        window.location.hash = '#/login'
      }
    }

    return Promise.reject(err)
  },
)

export default client
