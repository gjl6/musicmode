<template>
  <div class="player-sidebar" :class="{ collapsed }">
    <div class="sidebar-header">
      <span v-if="!collapsed" class="sidebar-title">{{ $t('dashboard.title') }}</span>
      <n-button
        size="tiny"
        text
        class="collapse-btn"
        @click="collapsed = !collapsed"
      >
        <n-icon :size="14">
          <ChevronBackOutline v-if="!collapsed" />
          <ChevronForwardOutline v-else />
        </n-icon>
      </n-button>
    </div>

    <n-menu
      :value="activeKey"
      :collapsed="collapsed"
      :collapsed-width="48"
      :collapsed-icon-size="20"
      :options="menuOptions"
      :root-indent="16"
      :indent="8"
      @update:value="navigate"
    />


    <div v-if="player.isMinimized && player.hasTrack" class="sidebar-mini-player">

      <div class="smp-progress" @click="seekMini">
        <div class="smp-progress-track">
          <div class="smp-progress-fill" :style="{ width: (player.progress * 100) + '%' }" />
        </div>
      </div>

      <div class="smp-body">

        <div class="smp-cover" @click="player.isMinimized = false">
          <img v-if="miniCover" :src="miniCover" class="smp-cover-img" alt="cover" />
          <n-icon v-else :size="20" color="var(--ct-text-3)"><MusicalNotesOutline /></n-icon>
        </div>


        <n-tooltip trigger="hover" :delay="500">
          <template #trigger>
            <span class="smp-title" @click="player.isMinimized = false">{{ player.displayTitle }}</span>
          </template>
          {{ player.displayTitle }}
        </n-tooltip>


        <div class="smp-play" @click="player.togglePlay()">
          <n-icon :size="18">
            <PauseOutline v-if="player.isPlaying" />
            <PlayOutline v-else />
          </n-icon>
        </div>


        <n-button text class="smp-expand" @click="player.isMinimized = false">
          <template #icon><n-icon :size="16"><ChevronForwardOutline /></n-icon></template>
        </n-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, h } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  HomeOutline, PeopleOutline, DiscOutline,
  PricetagsOutline, MusicalNotesOutline, ListOutline,
  ChevronBackOutline, ChevronForwardOutline,
  PlayOutline, PauseOutline,
} from '@vicons/ionicons5'
import { NIcon } from 'naive-ui'
import { usePlayerStore } from '@/store/playback/player.js'
import { subsonicGetCoverArtUrl } from '@/api/playback/subsonic.js'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const player = usePlayerStore()

const collapsed = ref(false)

const activeKey = computed(() => route.path)

function renderIcon(icon) {
  return () => h(NIcon, null, { default: () => h(icon) })
}

const menuOptions = computed(() => [
  { label: t('player.nav.home'), key: '/player', icon: renderIcon(HomeOutline) },
  { label: t('player.nav.artists'), key: '/player/artists', icon: renderIcon(PeopleOutline) },
  { label: t('player.nav.albums'), key: '/player/albums', icon: renderIcon(DiscOutline) },
  { label: t('player.nav.genres'), key: '/player/genres', icon: renderIcon(PricetagsOutline) },
  { label: t('player.nav.songs'), key: '/player/songs', icon: renderIcon(MusicalNotesOutline) },
  { label: t('player.nav.playlists'), key: '/player/playlists', icon: renderIcon(ListOutline) },
])

function navigate(key) {
  router.push(key)
}

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
.player-sidebar {
  width: 200px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: var(--border-width-strong) solid var(--ct-border);
  background: var(--gradient-card, var(--ct-bg-secondary));
  transition: width 0.2s;
  overflow: hidden;
}
.player-sidebar.collapsed {
  width: 56px;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 12px 8px;
  min-height: 40px;
}
.collapsed .sidebar-header {
  justify-content: center;
  padding: 12px 4px 8px;
}

.sidebar-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ct-text);
  white-space: nowrap;
  letter-spacing: 0.03em;
}

.collapse-btn {
  flex-shrink: 0;
  color: var(--ct-text-2);
}


.sidebar-mini-player {
  margin-top: auto;
  border-top: var(--border-width-default, 2px) solid var(--ct-border);
  background: var(--gradient-button, var(--ct-bg-secondary));
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
  background: var(--ct-border);
  overflow: hidden;
}
.smp-progress-fill {
  height: 100%;
  border-radius: 2px;
  background: var(--gradient-button-primary, var(--ct-accent));
  box-shadow: 0 0 4px rgb(var(--ct-accent-rgb) / 0.4);
  transition: width 0.15s linear;
}

.smp-body {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
}


.smp-cover {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  border: var(--border-width-strong, 3px) solid var(--color-border);
  background: var(--ct-bg-secondary);
  box-shadow: var(--effect-input-inner);
  overflow: hidden;
  cursor: pointer;
}
.smp-cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}


.smp-title {
  flex: 1;
  font-size: var(--text-xs);
  font-weight: 500;
  color: var(--ct-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  min-width: 0;
}
.smp-title:hover {
  color: var(--ct-accent);
}


.smp-play {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--gradient-button-primary, var(--ct-accent));
  color: #fff;
  box-shadow:
    inset 1px 1px 3px rgba(255 255 255 / 0.3),
    inset -2px -2px 4px rgba(0 0 0 / 0.1),
    0 2px 8px rgb(var(--ct-accent-rgb) / 0.25),
    0 4px 16px rgb(var(--ct-accent-rgb) / 0.12);
  cursor: pointer;
  transition: transform 0.15s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.smp-play:hover {
  transform: scale(1.12);
}
.smp-play:active {
  transform: scale(0.9);
}


.smp-expand {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  color: var(--ct-text-3) !important;
}
.smp-expand:hover {
  color: var(--ct-accent) !important;
}
</style>
