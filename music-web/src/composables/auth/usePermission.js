import { useAuthStore } from '@/store/auth.js'


export function usePermission() {
  const auth = useAuthStore()


  function has(code) {
    return auth.hasPermission(code)
  }


  function any(...codes) {
    return auth.hasAnyPermission(...codes)
  }


  const hasRoleAdmin = auth.hasRole('ROLE_ADMIN')

  return { has, any, hasRoleAdmin }
}
