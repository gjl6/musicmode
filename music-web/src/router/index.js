import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/store/auth.js'

import authRoutes from './auth.js'
import editorRoutes from './editor.js'
import playbackRoutes from './playback.js'

const routes = [...editorRoutes, ...authRoutes, ...playbackRoutes]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

router.beforeEach((to, _from, next) => {
  const TOKEN_KEY = 'auth-token'
  let token = null
  try {
    token = localStorage.getItem(TOKEN_KEY)
  } catch {  }

    if (to.meta.requiresAuth && !token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

    if (to.meta.requiresAdmin) {
    const auth = useAuthStore()
    if (!auth.hasRole('ROLE_ADMIN')) {
      next({ name: 'Forbidden' })
      return
    }
  }

    if (to.name === 'Login' && token) {
    next({ name: 'Workbench' })
    return
  }

  next()
})

export default router
