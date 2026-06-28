<template>
  <aside class="player-sidebar" :class="{ collapsed }">
    <!-- ═══ 头部 ═══ -->
    <div class="sidebar-header">
      <span v-if="!collapsed" class="sidebar-title">{{ $t('dashboard.title') }}</span>
      <n-button
        size="tiny"
        text
        class="collapse-btn"
        :title="collapsed ? '展开侧边栏' : '收起侧边栏'"
        @click="collapsed = !collapsed"
      >
        <n-icon :size="16">
          <ChevronBackOutline v-if="!collapsed" />
          <ChevronForwardOutline v-else />
        </n-icon>
      </n-button>
    </div>

    <!-- ═══ 导航 ═══ -->
    <nav class="sidebar-nav">
      <div
        v-for="item in navItems"
        :key="item.key"
        class="nav-item"
        :class="{ active: isActive(item.key) }"
        @click="navigate(item.key)"
        :title="collapsed ? item.label : undefined"
      >
        <div class="nav-item-icon">
          <n-icon :size="20"><component :is="item.icon" /></n-icon>
        </div>
        <Transition name="nav-label-fade">
          <span v-if="!collapsed" class="nav-item-label">{{ item.label }}</span>
        </Transition>
        <!-- 激活指示条 -->
        <div v-if="isActive(item.key)" class="nav-item-bar" />
      </div>
    </nav>

    <!-- ═══ 库统计摘要 ═══ -->
    <Transition name="nav-label-fade">
      <div v-if="!collapsed" class="sidebar-stats">
        <div class="stats-item">
          <n-icon :size="14" color="var(--sb-text-3)"><PeopleOutline /></n-icon>
          <span class="stats-label">{{ $t('dashboard.stats.artists') }}</span>
          <span class="stats-value">{{ fmtNum(library.artistCount) }}</span>
        </div>
        <div class="stats-item">
          <n-icon :size="14" color="var(--sb-text-3)"><DiscOutline /></n-icon>
          <span class="stats-label">{{ $t('dashboard.stats.albums') }}</span>
          <span class="stats-value">{{ fmtNum(library.albumCount) }}</span>
        </div>
        <div class="stats-item">
          <n-icon :size="14" color="var(--sb-text-3)"><MusicalNotesOutline /></n-icon>
          <span class="stats-label">{{ $t('dashboard.stats.songs') }}</span>
          <span class="stats-value">{{ fmtNum(library.songCount) }}</span>
        </div>
      </div>
    </Transition>

    <!-- ═══ 用户区（底部固定）═══ -->
    <div class="sidebar-user" :class="{ collapsed }" @click="collapsed ? (collapsed = false) : router.push('/player/settings')">
      <div class="user-avatar">
        <img v-if="avatarUrl" :src="avatarUrl" class="user-avatar-img" alt="" />
        <span v-else class="user-avatar-text">{{ avatarLetter }}</span>
      </div>

      <Transition name="nav-label-fade">
        <div v-if="!collapsed" class="user-info-row">
          <span class="user-name">{{ displayName }}</span>

          <n-button
            text
            size="tiny"
            class="user-settings-btn"
            title="设置"
            @click.stop="router.push('/player/settings')"
          >
            <template #icon><n-icon :size="18"><SettingsOutline /></n-icon></template>
          </n-button>
        </div>
      </Transition>
    </div>

    <!-- ═══ 迷你播放器 ═══ -->
    <div v-if="player.isMinimized && player.hasTrack" class="sidebar-mini-player">
      <!-- 进度条 -->
      <div class="smp-progress" @click="seekMini">
        <div class="smp-progress-track">
          <div class="smp-progress-fill" :style="{ width: (player.progress * 100) + '%' }" />
        </div>
      </div>

      <div class="smp-body">
        <!-- 封面 -->
        <div class="smp-cover" @click="player.isMinimized = false">
          <img v-if="miniCover" :src="miniCover" class="smp-cover-img" alt="cover" />
          <n-icon v-else :size="20" color="var(--ct-text-3)"><MusicalNotesOutline /></n-icon>
        </div>

        <!-- 歌名 -->
        <n-tooltip trigger="hover" :delay="500">
          <template #trigger>
            <span class="smp-title" @click="player.isMinimized = false">{{ player.displayTitle }}</span>
          </template>
          {{ player.displayTitle }}
        </n-tooltip>

        <!-- 播放/暂停 -->
        <div class="smp-play" @click="player.togglePlay()">
          <n-icon :size="18">
            <PauseOutline v-if="player.isPlaying" />
            <PlayOutline v-else />
          </n-icon>
        </div>

        <!-- 展开 -->
        <n-button text class="smp-expand" @click="player.isMinimized = false">
          <template #icon><n-icon :size="16"><ChevronForwardOutline /></n-icon></template>
        </n-button>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  HomeOutline, PeopleOutline, DiscOutline,
  PricetagsOutline, MusicalNotesOutline, ListOutline,
  ChevronBackOutline, ChevronForwardOutline,
  PlayOutline, PauseOutline,
  SettingsOutline,
} from '@vicons/ionicons5'
import { usePlayerStore } from '@/store/playback/player.js'
import { useLibraryStore } from '@/store/playback/library.js'
import { useAuthStore } from '@/store/auth.js'
import { getAvatarUrl } from '@/api/auth/auth.js'
import { subsonicGetCoverArtUrl } from '@/api/playback/subsonic.js'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const player = usePlayerStore()
const library = useLibraryStore()
const auth = useAuthStore()

const collapsed = ref(false)

// ═══ 导航项定义（computed 确保语言切换时标签更新）═══
const navItems = computed(() => [
  { key: '/player',           icon: HomeOutline,          label: t('player.nav.home') },
  { key: '/player/artists',   icon: PeopleOutline,        label: t('player.nav.artists') },
  { key: '/player/albums',    icon: DiscOutline,           label: t('player.nav.albums') },
  { key: '/player/genres',    icon: PricetagsOutline,      label: t('player.nav.genres') },
  { key: '/player/songs',     icon: MusicalNotesOutline,   label: t('player.nav.songs') },
  { key: '/player/playlists', icon: ListOutline,           label: t('player.nav.playlists') },
])

function isActive(key) {
  if (key === '/player') return route.path === '/player'
  return route.path.startsWith(key)
}

function navigate(key) {
  router.push(key)
}

// ═══ 预加载库统计数据 ═══
onMounted(() => {
  library.loadStats()
})

// ═══ 数字格式化 ═══
function fmtNum(n) {
  if (n == null) return '—'
  if (n >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, '') + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  return String(n)
}

// ═══ 用户区 ═══
const displayName = computed(() => auth.user?.displayName || auth.user?.username || '')

const avatarLetter = computed(() => {
  const name = displayName.value
  return name ? name.charAt(0).toUpperCase() : '?'
})

const avatarUrl = computed(() => {
  if (!auth.user?.id) return null
  // 用 avatarVersion 做 cache-bust，确保上传后立即刷新
  void auth.avatarVersion
  return `${getAvatarUrl(auth.user.id)}?v=${auth.avatarVersion}`
})

// ═══ 迷你播放器 ═══
const miniCover = computed(() => {
  const artId = player.current?.coverArt || player.current?.albumId
  return artId ? subsonicGetCoverArtUrl(artId, 100) : null
})

function seekMini(e) {
  const bar = e.currentTarget
  const rect = bar.getBoundingClientRect()
  const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
  player.seek(ratio * player.duration)
}
</script>

<style scoped>
/* ═══════════════════════════════════════════════════════
   PlayerSidebar — 自定义导航 + 粘土风格
   ═══════════════════════════════════════════════════════ */

.player-sidebar {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border: var(--border-width-strong, 3px) solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--gradient-sidebar, var(--sb-bg));
  box-shadow: var(--effect-card-inner), var(--effect-card-outer);
  transition: width var(--transition-slow);
  overflow: hidden;
  position: relative;
}
.player-sidebar.collapsed {
  width: 56px;
}

/* ═══ 头部 ═══ */
.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 14px 10px;
  min-height: 44px;
  flex-shrink: 0;
}
.collapsed .sidebar-header {
  justify-content: center;
  padding: 14px 4px 10px;
}

.sidebar-title {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--sb-text);
  white-space: nowrap;
  letter-spacing: 0.03em;
  line-height: var(--leading-tight);
}

.collapse-btn {
  flex-shrink: 0;
  color: var(--sb-text-3) !important;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm) !important;
  transition: color var(--transition-fast), background var(--transition-fast);
}
.collapse-btn:hover {
  color: var(--sb-text) !important;
  background: var(--sb-bg-hover) !important;
}

/* ═══ 导航区域 ═══ */
.sidebar-nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 4px 8px;
  overflow-y: auto;
  overflow-x: hidden;
}
.sidebar-nav::-webkit-scrollbar { width: 4px; }
.sidebar-nav::-webkit-scrollbar-thumb {
  background: var(--sb-scrollbar);
  border-radius: 2px;
}

/* ── 导航项 ── */
.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 44px;
  padding: 0 10px 0 14px;
  border-radius: var(--radius-md);
  border: var(--border-width-default) solid transparent;
  color: var(--sb-text-2);
  cursor: pointer;
  transition:
    color var(--transition-fast),
    background var(--transition-fast),
    border-color var(--transition-fast),
    box-shadow var(--transition-fast);
  user-select: none;
}
.collapsed .nav-item {
  justify-content: center;
  padding: 0;
}

.nav-item:hover {
  color: var(--sb-text);
  background: var(--sb-bg-hover);
  border-color: var(--sb-border);
  box-shadow: var(--effect-button-inner), var(--effect-button-outer, none);
}

.nav-item:active {
  transform: scale(0.97);
  box-shadow: var(--effect-press);
  transition: transform 0.1s ease;
}

/* ── 激活态 ── */
.nav-item.active {
  color: var(--sb-text);
  background: var(--sb-bg-hover);
}

/* ── 激活指示条 ── */
.nav-item-bar {
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: var(--selection-bar-width, 3px);
  border-radius: 0 3px 3px 0;
  background: var(--color-accent);
  box-shadow:
    var(--selection-bar-shadow, none),
    var(--effect-selection-bar, 0 0 8px rgb(var(--sb-selection-rgb, var(--color-accent-rgb)) / 0.5));
}

/* ── 图标 ── */
.nav-item-icon {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: var(--radius-sm);
  transition: background var(--transition-fast);
}
.nav-item.active .nav-item-icon {
  background: var(--effect-button-inner, none);
  box-shadow: var(--effect-input-inner, none);
}

/* ── 文字标签 ── */
.nav-item-label {
  font-size: var(--text-base);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: var(--leading-tight);
}

/* ── 标签过渡 ── */
.nav-label-fade-enter-active,
.nav-label-fade-leave-active {
  transition: opacity 0.15s ease;
}
.nav-label-fade-enter-from,
.nav-label-fade-leave-to {
  opacity: 0;
}

/* ═══ 库统计摘要 ═══ */
.sidebar-stats {
  flex-shrink: 0;
  padding: 10px 14px;
  margin: 0 8px;
  border-top: var(--border-width-default) solid var(--sb-border);
}

.stats-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 0;
}

.stats-label {
  flex: 1;
  font-size: var(--text-2xs);
  color: var(--sb-text-3);
  letter-spacing: 0.03em;
}

.stats-value {
  font-size: var(--text-2xs);
  font-weight: 700;
  color: var(--sb-text-2);
  font-feature-settings: 'tnum';
  letter-spacing: -0.3px;
}

/* ═══════ 用户区 — 底部固定 ═══════ */
.sidebar-user {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  margin: 0 8px 4px;
  border-top: var(--border-width-default) solid var(--sb-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--transition-fast);
}
.sidebar-user:hover {
  background: var(--sb-bg-hover);
}
.sidebar-user.collapsed {
  justify-content: center;
  padding: 12px 4px;
  margin: 0;
  border-top: none;
}

/* 头像 — 粘土圆形 */
.user-avatar {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  border: var(--border-width-strong, 3px) solid var(--color-border);
  background: var(--gradient-button-primary, var(--color-accent));
  box-shadow:
    inset 1px 1px 3px rgba(255 255 255 / 0.3),
    inset -2px -2px 4px rgba(0 0 0 / 0.1),
    0 2px 8px rgb(var(--color-accent-rgb) / 0.2);
  transition: transform var(--transition-fast), box-shadow var(--transition-fast);
  user-select: none;
}
.user-avatar:hover {
  transform: scale(1.08);
}
.user-avatar:active {
  transform: scale(0.95);
}

.user-avatar-text {
  font-size: 14px;
  font-weight: 700;
  color: #fff;
  line-height: 1;
}

.user-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

/* 用户名 + 齿轮行 */
.user-info-row {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

/* 用户名 */
.user-name {
  flex: 1;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--sb-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

/* 齿轮按钮 */
.user-settings-btn {
  flex-shrink: 0;
  color: var(--sb-text-3) !important;
  width: 30px;
  height: 30px;
  border-radius: var(--radius-sm) !important;
  transition: color var(--transition-fast), transform var(--transition-fast);
}
.user-settings-btn:hover {
  color: var(--sb-text) !important;
  background: var(--sb-bg-hover) !important;
  transform: rotate(30deg);
}

/* ═══════ 迷你播放器 — 粘土嵌入 ═══════ */
.sidebar-mini-player {
  margin-top: auto;
  border-top: var(--border-width-strong, 3px) solid var(--sb-border);
  background: var(--gradient-button, var(--sb-bg));
  box-shadow: var(--effect-input-inner);
  overflow: hidden;
}

.smp-progress {
  cursor: pointer;
  padding: 0 6px;
}
.smp-progress-track {
  height: 3px;
  border-radius: 2px;
  background: var(--sb-border);
  overflow: hidden;
}
.smp-progress-fill {
  height: 100%;
  border-radius: 2px;
  background: var(--gradient-button-primary, var(--color-accent));
  box-shadow: 0 0 4px rgb(var(--sb-selection-rgb, var(--color-accent-rgb)) / 0.4);
  transition: width 0.15s linear;
}

.smp-body {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
}

/* 封面 */
.smp-cover {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  border: var(--border-width-strong, 3px) solid var(--sb-border);
  background: var(--sb-bg);
  box-shadow: var(--effect-input-inner);
  overflow: hidden;
  cursor: pointer;
}
.smp-cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 歌名 */
.smp-title {
  flex: 1;
  font-size: var(--text-xs);
  font-weight: 500;
  color: var(--sb-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  min-width: 0;
}
.smp-title:hover {
  color: var(--color-accent);
}

/* 播放键 */
.smp-play {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--gradient-button-primary, var(--color-accent));
  color: #fff;
  box-shadow:
    inset 1px 1px 3px rgba(255 255 255 / 0.3),
    inset -2px -2px 4px rgba(0 0 0 / 0.1),
    0 2px 8px rgb(var(--sb-selection-rgb, var(--color-accent-rgb)) / 0.25),
    0 4px 16px rgb(var(--sb-selection-rgb, var(--color-accent-rgb)) / 0.12);
  cursor: pointer;
  transition: transform 0.15s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.smp-play:hover {
  transform: scale(1.12);
}
.smp-play:active {
  transform: scale(0.9);
}

/* 展开按钮 */
.smp-expand {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  color: var(--sb-text-3) !important;
}
.smp-expand:hover {
  color: var(--sb-text) !important;
}
</style>
