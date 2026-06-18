<template>
  <nav class="top-nav" v-if="showNav">
    <div class="nav-links">
      <n-button
        v-for="item in navItems"
        :key="item.path"
        :type="isActive(item) ? 'primary' : 'default'"
        :text="!isActive(item)"
        size="small"
        @click="$router.push(item.path)"
      >
        {{ item.label }}
      </n-button>
    </div>
    <div class="nav-actions">
      <n-button
        text
        size="small"
        :type="app.themeStyle === 'clay' ? 'primary' : 'default'"
        @click="app.toggleThemeStyle()"
        :title="app.themeStyle === 'clay' ? $t('nav.switchToDefault') : $t('nav.switchToClay')"
      >
        <n-icon :size="16">
          <CubeOutline />
        </n-icon>
      </n-button>
      <n-button text size="small" @click="app.toggleTheme()">
        <n-icon :size="16">
          <MoonOutline v-if="app.colorScheme === 'light'" />
          <SunnyOutline v-else />
        </n-icon>
      </n-button>
      <n-button text size="small" @click="toggleLang()">
        {{ app.locale === 'zh-CN' ? 'EN' : '中' }}
      </n-button>
    </div>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { MoonOutline, SunnyOutline, CubeOutline, ShieldCheckmarkOutline } from '@vicons/ionicons5'
import { useAppStore } from '@/store/editor/app.js'
import { useAuthStore } from '@/store/auth.js'

const route = useRoute()
const { t, locale } = useI18n()
const app = useAppStore()
const auth = useAuthStore()

const navItems = computed(() => {
  const items = [
    { path: '/', label: t('nav.home') },
    { path: '/workbench', label: t('nav.workbench') },
    { path: '/artist-manager', label: t('nav.artistManager') },
    { path: '/pipelines', label: t('nav.operationLog') },
    { path: '/player', label: t('nav.collection') },
    { path: '/config', label: t('nav.config') },
  ]
    if (auth.hasRole('ROLE_ADMIN')) {
    items.push({ path: '/admin', label: '权限管理', icon: ShieldCheckmarkOutline })
  }
  return items
})

const showNav = computed(() => route.name !== 'Home')

function isActive(item) {
  if (item.path === '/pipelines') return route.path.startsWith('/pipeline')
  if (item.path === '/player') return route.path.startsWith('/player')
  if (item.path === '/artist-manager') return route.path.startsWith('/artist-manager')
  return route.path === item.path
}

function toggleLang() {
  const next = app.locale === 'zh-CN' ? 'en' : 'zh-CN'
  app.setLocale(next)
  locale.value = next
}
</script>

<style scoped>
.top-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 40px;
  padding: 0 16px;
  border-bottom: var(--border-width-strong) solid var(--ct-border);
  background: var(--gradient-button, var(--ct-bg));
  flex-shrink: 0;
}
.nav-links {
  display: flex;
  gap: 4px;
}
.nav-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
