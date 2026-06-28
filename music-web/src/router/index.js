import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/store/auth.js'
import { componentRegistry } from './componentRegistry.js'

// ══════════════════════════════════════════════════
// 基础路由（始终存在，无权限语义）
// ══════════════════════════════════════════════════

const baseRoutes = [
  {
    path: '/',
    redirect: '/player',
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
  },
  {
    path: '/forbidden',
    name: 'Forbidden',
    component: () => import('@/views/auth/Forbidden.vue'),
  },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes: baseRoutes,
})

// ══════════════════════════════════════════════════
// 动态路由安装 / 卸载
// ══════════════════════════════════════════════════

let routesInstalled = false
const installedNames = []

/**
 * 将后端返回的菜单树转换为 Vue Router 路由并注册。
 * 登录成功或刷新恢复 session 后调用。
 *
 * @param {Array} menus — 后端 /api/auth/menus 返回的菜单树
 */
export function buildAndInstallRoutes(menus) {
  // 先卸载旧路由（支持重新登录时权限变更）
  uninstallRoutes()
  routesInstalled = true

  if (!menus || menus.length === 0) return

  const routes = convertMenuTree(menus)
  for (const route of routes) {
    try {
      router.addRoute(route)
      collectNames(route)
    } catch (e) {
      console.warn('[Router] Failed to add route:', route.name, e)
    }
  }
}

/** 递归转换菜单节点 → Vue Router 路由对象 */
function convertMenuTree(nodes) {
  return nodes.map(node => {
    // __redirect__ 特殊标记 → redirect 路由
    if (node.component === '__redirect__') {
      return { name: node.name, path: node.path, redirect: { name: 'AdminUsers' } }
    }

    const component = componentRegistry[node.component]
    if (!component) {
      console.warn(`[Router] Unknown component key: "${node.component}" for route "${node.name}"`)
      return null
    }

    const route = {
      path: node.path,
      name: node.name,
      component,
      meta: node.meta || {},
    }

    if (node.children && node.children.length > 0) {
      route.children = convertMenuTree(node.children).filter(Boolean)
    }

    return route
  }).filter(Boolean)
}

/** 收集路由名称（用于卸载） */
function collectNames(route) {
  if (route.name) installedNames.push(route.name)
  if (route.children) {
    for (const child of route.children) {
      collectNames(child)
    }
  }
}

/** 卸载所有动态注册的路由（登出时调用） */
export function uninstallRoutes() {
  if (!routesInstalled) return
  for (const name of installedNames) {
    try {
      router.removeRoute(name)
    } catch { /* ignore */ }
  }
  installedNames.length = 0
  routesInstalled = false
}

// ══════════════════════════════════════════════════
// 简化守卫
// ══════════════════════════════════════════════════

router.beforeEach(async (to, _from, next) => {
  const TOKEN_KEY = 'auth-token'
  let token = null
  try {
    token = localStorage.getItem(TOKEN_KEY)
  } catch { /* ignore */ }

  // ── 1. 路由未匹配（刷新场景：动态路由尚未安装）──
  if (to.matched.length === 0) {
    // 无 token → 跳登录
    if (!token) {
      next({ name: 'Login', query: { redirect: to.fullPath } })
      return
    }

    // 有 token 但路由未安装 → 初始化 session
    if (!routesInstalled) {
      const auth = useAuthStore()
      if (!auth.user) {
        await auth.init()
      }

      if (auth.user && auth.menus.length > 0) {
        buildAndInstallRoutes(auth.menus)
        // 重试导航（replace 避免冗余历史记录）
        next({ ...to, replace: true })
        return
      }

      // init 失败（token 过期等）→ 跳登录
      next({ name: 'Login' })
      return
    }

    // 路由已安装但仍不匹配 → 真正 404，回播放器首页
    next({ path: '/player' })
    return
  }

  // ── 2. 需要认证但未登录 ──
  if (to.meta.requiresAuth && !token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

  // ── 3. 已登录访问登录页 → 播放器首页 ──
  if (to.name === 'Login' && token) {
    next({ path: '/player' })
    return
  }

  next()
})

export default router
