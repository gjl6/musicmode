

import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth/auth.js'

const TOKEN_KEY = 'auth-token'
const REFRESH_KEY = 'auth-refresh-token'

export const useAuthStore = defineStore('auth', () => {
    const accessToken = ref(loadToken(TOKEN_KEY))
  const refreshToken = ref(loadToken(REFRESH_KEY))
  const user = ref(null)
  const permissions = ref([])
  const permissionMeta = ref({})
  const loading = ref(false)

    const isAuthenticated = computed(() => !!accessToken.value)


  function hasPermission(code) {
    return permissions.value.includes(code)
  }


  function hasRole(roleCode) {
    return user.value?.roles?.includes(roleCode) ?? false
  }


  function hasAnyPermission(...codes) {
    return codes.some((c) => permissions.value.includes(c))
  }

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
    } catch {  }
  }


  async function login(username, password) {
    loading.value = true
    try {
      const res = await authApi.login(username, password)
      accessToken.value = res.accessToken
      refreshToken.value = res.refreshToken
      user.value = res.userInfo ?? null
      saveToken(TOKEN_KEY, res.accessToken)
      saveToken(REFRESH_KEY, res.refreshToken)
      await fetchPermissionsSilent()
      return res
    } finally {
      loading.value = false
    }
  }


  async function register(username, password, displayName, email) {
    loading.value = true
    try {
      const res = await authApi.register(username, password, displayName, email)
      accessToken.value = res.accessToken
      refreshToken.value = res.refreshToken
      user.value = res.userInfo ?? null
      saveToken(TOKEN_KEY, res.accessToken)
      saveToken(REFRESH_KEY, res.refreshToken)
      await fetchPermissionsSilent()
      return res
    } finally {
      loading.value = false
    }
  }


  async function logout() {
    try {
      await authApi.logout()
    } catch {
          } finally {
      accessToken.value = null
      refreshToken.value = null
      user.value = null
      permissions.value = []
      saveToken(TOKEN_KEY, null)
      saveToken(REFRESH_KEY, null)
    }
  }


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
            accessToken.value = null
      refreshToken.value = null
      user.value = null
      permissions.value = []
      saveToken(TOKEN_KEY, null)
      saveToken(REFRESH_KEY, null)
      return false
    }
  }


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


  async function fetchPermissions() {
    try {
      const data = await authApi.getMyPermissions()
      if (Array.isArray(data) && data.length > 0) {
                if (typeof data[0] === 'object') {
          permissions.value = data.map((p) => p.permissionCode)
          const meta = {}
          for (const p of data) {
            meta[p.permissionCode] = { permissionName: p.permissionName, description: p.description }
          }
          permissionMeta.value = meta
        } else {
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


  function getPermissionName(code) {
    return permissionMeta.value[code]?.permissionName || code
  }


  async function fetchPermissionsSilent() {
    try {
      await fetchPermissions()
    } catch {
          }
  }

  return {
    accessToken,
    refreshToken,
    user,
    permissions,
    permissionMeta,
    loading,
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
  }
})
