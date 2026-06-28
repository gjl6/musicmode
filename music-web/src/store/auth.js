/*
 * useAuthStore — 认证状态管理
 *
 * 职责：
 *   1. Token 管理（accessToken / refreshToken）—— localStorage 持久化
 *   2. 用户信息缓存
 *   3. 登录 / 注册 / 登出 / Token 刷新
 *   4. isAuthenticated 派生状态
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth/auth.js'

const TOKEN_KEY = 'auth-token'
const REFRESH_KEY = 'auth-refresh-token'

export const useAuthStore = defineStore('auth', () => {
  // ---- 状态 ----
  const accessToken = ref(loadToken(TOKEN_KEY))
  const refreshToken = ref(loadToken(REFRESH_KEY))
  const user = ref(null)
  const permissions = ref([])       // 当前用户的权限码列表
  const permissionMeta = ref({})    // { code: { permissionName, description } } 从 DB 加载
  const menus = ref([])             // 后端返回的菜单/路由树
  const loading = ref(false)
  const avatarVersion = ref(0)      // 头像变更计数器，用于 cache-bust

  // ---- 派生 ----
  const isAuthenticated = computed(() => !!accessToken.value)

  /** 是否有指定权限码 */
  function hasPermission(code) {
    return permissions.value.includes(code)
  }

  /** 是否拥有指定角色 */
  function hasRole(roleCode) {
    return user.value?.roles?.includes(roleCode) ?? false
  }

  /** 是否拥有任一权限 */
  function hasAnyPermission(...codes) {
    return codes.some((c) => permissions.value.includes(c))
  }

  // ---- 方法 ----
  function loadToken(key) {
    try {
      return localStorage.getItem(key) || null
    } catch {
      return null
    }
  }

  function saveToken(key, value) {
    try {
      if (value) localStorage.setItem(key, value)
      else localStorage.removeItem(key)
    } catch { /* quota exceeded — 静默失败 */ }
  }

  /** 登录 */
  async function login(username, password) {
    loading.value = true
    try {
      const res = await authApi.login(username, password)
      accessToken.value = res.accessToken
      refreshToken.value = res.refreshToken
      user.value = res.userInfo ?? null
      menus.value = res.menus ?? []
      saveToken(TOKEN_KEY, res.accessToken)
      saveToken(REFRESH_KEY, res.refreshToken)
      await fetchPermissionsSilent()
      // 按权限安装路由
      const { buildAndInstallRoutes } = await import('@/router/index.js')
      buildAndInstallRoutes(menus.value)
      return res
    } finally {
      loading.value = false
    }
  }

  /** 注册 */
  async function register(username, password, displayName, email) {
    loading.value = true
    try {
      const res = await authApi.register(username, password, displayName, email)
      accessToken.value = res.accessToken
      refreshToken.value = res.refreshToken
      user.value = res.userInfo ?? null
      menus.value = res.menus ?? []
      saveToken(TOKEN_KEY, res.accessToken)
      saveToken(REFRESH_KEY, res.refreshToken)
      await fetchPermissionsSilent()
      const { buildAndInstallRoutes } = await import('@/router/index.js')
      buildAndInstallRoutes(menus.value)
      return res
    } finally {
      loading.value = false
    }
  }

  /** 登出 */
  async function logout() {
    // 先卸载动态路由（避免残留）
    try {
      const { uninstallRoutes } = await import('@/router/index.js')
      uninstallRoutes()
    } catch { /* ignore */ }

    try {
      await authApi.logout()
    } catch {
      // 即使服务端登出失败，也清理本地状态
    } finally {
      accessToken.value = null
      refreshToken.value = null
      user.value = null
      permissions.value = []
      menus.value = []
      saveToken(TOKEN_KEY, null)
      saveToken(REFRESH_KEY, null)
    }
  }

  /** 尝试用 refreshToken 刷新 accessToken */
  async function tryRefresh() {
    if (!refreshToken.value) return false
    try {
      const res = await authApi.refreshToken(refreshToken.value)
      accessToken.value = res.accessToken
      refreshToken.value = res.refreshToken
      user.value = res.userInfo ?? null
      saveToken(TOKEN_KEY, res.accessToken)
      saveToken(REFRESH_KEY, res.refreshToken)
      await fetchPermissionsSilent()
      return true
    } catch {
      // 刷新失败 — 清理登录态
      accessToken.value = null
      refreshToken.value = null
      user.value = null
      permissions.value = []
      saveToken(TOKEN_KEY, null)
      saveToken(REFRESH_KEY, null)
      return false
    }
  }

  /** 拉取当前用户信息 */
  async function fetchMe() {
    try {
      const u = await authApi.getMe()
      user.value = u
      await fetchPermissionsSilent()
      return u
    } catch {
      return null
    }
  }

  /** 静默拉取权限（不抛异常，从 DB 加载 code + name） */
  async function fetchPermissions() {
    try {
      const data = await authApi.getMyPermissions()
      if (Array.isArray(data) && data.length > 0) {
        // API 返回 [{permissionCode, permissionName, description}, ...]
        if (typeof data[0] === 'object') {
          permissions.value = data.map((p) => p.permissionCode)
          const meta = {}
          for (const p of data) {
            meta[p.permissionCode] = { permissionName: p.permissionName, description: p.description }
          }
          permissionMeta.value = meta
        } else {
          // 兼容旧格式 [string, ...]
          permissions.value = data
        }
      } else {
        permissions.value = data || []
      }
      return permissions.value
    } catch {
      permissions.value = []
      return []
    }
  }

  /** 获取权限显示名称（从 DB 加载的元数据） */
  function getPermissionName(code) {
    return permissionMeta.value[code]?.permissionName || code
  }

  /** 静默拉取权限 —— 失败不影响主流程 */
  async function fetchPermissionsSilent() {
    try {
      await fetchPermissions()
    } catch {
      // 静默
    }
  }

  /** 拉取当前用户菜单/路由树 */
  async function fetchMenus() {
    try {
      const data = await authApi.getMenus()
      menus.value = data || []
      return menus.value
    } catch {
      menus.value = []
      return []
    }
  }

  function bumpAvatar() {
    avatarVersion.value++
  }

  // ── 监听拦截器的 session-expired 事件，同步重置 store ──
  if (typeof window !== 'undefined') {
    window.addEventListener('auth:session-expired', () => {
      accessToken.value = null
      refreshToken.value = null
      user.value = null
      permissions.value = []
      menus.value = []
    })
  }

  // ── 会话初始化（解决刷新后权限未加载的竞态）──
  let initPromise = null

  /** 确保用户数据已加载。有 token 但 user/permissions 为空时拉取；已加载则跳过。 */
  async function init() {
    // 已初始化过
    if (user.value && menus.value.length > 0) return
    // 无 token，无需初始化
    if (!accessToken.value) return
    // 正在初始化中，复用同一个 Promise
    if (initPromise) return initPromise

    initPromise = (async () => {
      try {
        await fetchMe()
        await fetchPermissionsSilent()
        await fetchMenus()
      } catch {
        // 静默 — 初始化失败不影响守卫（守卫看到空 user 会跳 forbidden）
      } finally {
        initPromise = null
      }
    })()

    return initPromise
  }

  return {
    accessToken,
    refreshToken,
    user,
    permissions,
    permissionMeta,
    menus,
    loading,
    avatarVersion,
    isAuthenticated,
    hasPermission,
    hasRole,
    hasAnyPermission,
    getPermissionName,
    login,
    register,
    logout,
    tryRefresh,
    fetchMe,
    fetchPermissions,
    fetchMenus,
    bumpAvatar,
    init,
  }
})
