import { useAuthStore } from '@/store/auth.js'

/**
 * 权限检查 composable —— 所有权限码从数据库动态加载。
 *
 * 用法：
 *   const { has, any } = usePermission()
 *   v-if="has('music:delete')"
 *   v-if="hasRoleAdmin"
 */
export function usePermission() {
  const auth = useAuthStore()

  /** 检查单一权限码 */
  function has(code) {
    return auth.hasPermission(code)
  }

  /** 检查任一权限码 */
  function any(...codes) {
    return auth.hasAnyPermission(...codes)
  }

  /** 是否管理员 */
  const hasRoleAdmin = auth.hasRole('ROLE_ADMIN')

  return { has, any, hasRoleAdmin }
}
