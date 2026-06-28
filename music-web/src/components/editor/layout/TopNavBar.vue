<template>
  <nav class="top-nav" v-if="showNav">
    <div class="nav-links">
      <template v-for="(item, idx) in navItems" :key="item.path">
        <a
          class="nav-link"
          :class="{ active: isActive(item) }"
          @click="router.push(item.path)"
        >{{ item.label }}</a>
        <!-- 首页之后插入音乐管理下拉 -->
        <n-dropdown
          v-if="idx === 0 && musicMgmtOptions.length > 0"
          trigger="hover"
          :options="musicMgmtOptions"
          @select="handleMusicMgmtSelect"
        >
          <a class="nav-link" :class="{ active: isMusicMgmtActive }">
            {{ musicMgmtLabel }}
            <n-icon :size="10" class="nav-chevron"><ChevronDownOutline /></n-icon>
          </a>
        </n-dropdown>
      </template>
    </div>
    <div class="nav-actions">
      <n-button
        text
        size="small"
        :type="app.themeStyle === 'clay' ? 'primary' : 'default'"
        @click="app.toggleThemeStyle()"
        :title="app.themeStyle === 'clay' ? $t('nav.switchToDefault') : $t('nav.switchToClay')"
      >
        <n-icon :size="16"><CubeOutline /></n-icon>
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
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { MoonOutline, SunnyOutline, CubeOutline, ChevronDownOutline } from '@vicons/ionicons5'
import { useAppStore } from '@/store/editor/app.js'
import { useAuthStore } from '@/store/auth.js'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const app = useAppStore()
const auth = useAuthStore()

const navItems = computed(() => {
  const items = []
  // 管道 — 需要 pipeline:write
  if (auth.hasPermission('pipeline:write')) {
    items.push({ path: '/pipelines', label: t('nav.operationLog') })
  }
  // 音乐库 — 需要 music:read（ROLE_USER 默认拥有）
  items.push({ path: '/player', label: t('nav.collection') })
  // 配置 — 需要 config:write
  if (auth.hasPermission('config:write')) {
    items.push({ path: '/config', label: t('nav.config') })
  }
  return items
})

const showNav = computed(() => {
  if (!auth.isAuthenticated) return false
  // 只有听歌权限的用户不需要工具栏；编辑者/管理员可见
  return auth.hasAnyPermission('music:write', 'pipeline:write', 'config:write', 'artist:write', 'album:write')
      || auth.hasRole('ROLE_ADMIN')
})

function isActive(item) {
  if (item.path === '/pipelines') return route.path.startsWith('/pipeline')
  if (item.path === '/player') return route.path.startsWith('/player')
  return route.path === item.path
}

// ── 音乐管理下拉（按权限过滤）──
const musicMgmtOptions = computed(() => {
  const opts = []
  if (auth.hasPermission('music:write')) {
    opts.push({ label: t('nav.songManagement'), key: '/workbench' })
  }
  if (auth.hasPermission('artist:write')) {
    opts.push({ label: t('nav.artistManager'), key: '/artist-manager' })
  }
  if (auth.hasPermission('album:write')) {
    opts.push({ label: t('nav.albumManager'), key: '/album-manager' })
  }
  return opts
})

const isMusicMgmtActive = computed(() =>
  route.path === '/workbench' ||
  route.path.startsWith('/artist-manager') ||
  route.path.startsWith('/album-manager')
)

// 按钮文字跟随当前子页面
const musicMgmtLabel = computed(() => {
  if (route.path === '/workbench') return t('nav.songManagement')
  if (route.path.startsWith('/artist-manager')) return t('nav.artistManager')
  if (route.path.startsWith('/album-manager')) return t('nav.albumManager')
  return t('nav.musicManagement')
})

function handleMusicMgmtSelect(key) {
  router.push(key)
}

function toggleLang() {
  const next = app.locale === 'zh-CN' ? 'en' : 'zh-CN'
  app.setLocale(next)
  locale.value = next
}
</script>

<style scoped>
/* ═══ 导航栏容器 — 粘土渐变底 + 双层阴影 ═══ */
.top-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 42px;
  padding: 0 14px;
  background: var(--gradient-button, var(--ct-bg));
  border-bottom: var(--border-width-strong) solid var(--ct-border);
  box-shadow: var(--shadow-sm);
  flex-shrink: 0;
}

/* ═══ 导航链接组 ═══ */
.nav-links {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* ═══ 粘土 Pill 链接 ═══ */
.nav-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 14px;
  border-radius: var(--radius-xl, 24px);
  font-size: 12.5px;
  font-weight: 500;
  color: var(--ct-text-2);
  cursor: pointer;
  user-select: none;
  white-space: nowrap;
  text-decoration: none;
  border: var(--border-width-default, 2px) solid transparent;
  transition: all 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
}

/* hover — 浮起 + 粘土阴影 */
.nav-link:hover {
  color: var(--ct-text);
  background: var(--color-surface, var(--ct-card-bg));
  border-color: rgb(var(--ct-accent-rgb) / 0.2);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

/* active — 渐变填充 pill */
.nav-link.active {
  color: var(--color-on-primary, #FFFFFF);
  background: var(--gradient-button-primary, linear-gradient(135deg, var(--ct-accent), rgb(var(--ct-accent-rgb) / 0.85)));
  border-color: var(--color-primary, var(--ct-accent));
  box-shadow: var(--shadow-md);
  font-weight: 700;
}

/* press — 粘土按压（squish） */
.nav-link:active {
  transform: scale(0.94);
  transition: transform 0.1s ease-out;
}

/* ═══ Chevron 箭头 ═══ */
.nav-chevron {
  opacity: 0.5;
  flex-shrink: 0;
  transition: opacity 0.2s;
}
.nav-link:hover .nav-chevron { opacity: 0.75; }
.nav-link.active .nav-chevron { opacity: 0.9; }

/* ═══ 右侧操作按钮区 ═══ */
.nav-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}
</style>
