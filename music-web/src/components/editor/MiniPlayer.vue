<template>
  <div class="mini-player" :class="{ visible: player.hasTrack }">
    <!-- 封面 -->
    <img
      v-if="coverUrl"
      :src="coverUrl"
      class="player-cover"
      alt="cover"
    />
    <div v-else class="player-cover player-cover--placeholder">
      <n-icon :size="20"><MusicalNotesOutline /></n-icon>
    </div>

    <!-- 曲目信息 -->
    <div class="player-track" @click="openEditor(player.currentFile)" :title="$t('edit.clickToEdit')">
      <span class="player-track-name">{{ displayTrackName }}</span>
      <span class="player-track-artist">{{ displayArtist }}</span>
    </div>

    <!-- 播放控制 -->
    <div class="player-controls">
      <n-button size="tiny" text @click="player.togglePlay()">
        <template #icon>
          <n-icon :size="22">
            <PauseOutline v-if="player.isPlaying" />
            <PlayOutline v-else />
          </n-icon>
        </template>
      </n-button>
    </div>

    <!-- 进度条 -->
    <div class="player-progress-bar" @click="seekProgress">
      <div class="player-progress-fill" :style="{ width: (player.progress * 100) + '%' }" />
    </div>

    <!-- 时间 -->
    <span class="player-time">{{ formatTime(player.currentTime) }} / {{ formatTime(player.duration) }}</span>

    <!-- 音量 -->
    <n-button size="tiny" text @click="player.toggleMute()" class="player-volume-btn">
      <template #icon>
        <n-icon :size="16">
          <VolumeMuteOutline v-if="player.muted || player.volume === 0" />
          <VolumeHighOutline v-else />
        </n-icon>
      </template>
    </n-button>

    <!-- 关闭 -->
    <n-button size="tiny" text @click="player.stop()" class="player-close-btn">
      <template #icon><n-icon :size="16"><CloseOutline /></n-icon></template>
    </n-button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  MusicalNotesOutline, PlayOutline, PauseOutline,
  VolumeHighOutline, VolumeMuteOutline, CloseOutline,
} from '@vicons/ionicons5'
import { useEditorPlayerStore } from '@/store/editor/player.js'
import { useEditAction } from '@/composables/editor/useEditAction.js'
import { songField, artistNames } from '@/utils/musicMeta.js'
import { useArtistConfig } from '@/composables/editor/useArtistConfig.js'
import { getCoverUrl } from '@/utils/mediaUrl.js'

const player = useEditorPlayerStore()
const { joinSeparator } = useArtistConfig()
const { openEditor } = useEditAction()

const coverUrl = computed(() => getCoverUrl(songField(player.currentFile?.meta, 'coverPath')))

const displayTrackName = computed(() =>
  songField(player.currentFile?.meta, 'title') || player.currentFile?.name || '—'
)

const displayArtist = computed(() =>
  artistNames(player.currentFile?.meta, joinSeparator.value || undefined) || '—'
)

function formatTime(seconds) {
  if (!seconds || !isFinite(seconds)) return '0:00'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${String(s).padStart(2, '0')}`
}

function seekProgress(e) {
  const bar = e.currentTarget
  const rect = bar.getBoundingClientRect()
  const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
  player.seek(ratio * player.duration)
}
</script>

<style scoped>
.mini-player {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 48px;
  padding: 6px 16px;
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
  background: var(--gradient-card, var(--ct-card-bg));
  border-top: var(--border-width-strong) solid var(--ct-border);
  box-shadow: var(--shadow-player);
  transform: translateY(100%);
  transition: transform var(--transition-player);
}
.mini-player.visible {
  transform: translateY(0);
}

.player-cover {
  width: 36px; height: 36px;
  border-radius: 4px;
  object-fit: cover;
  flex-shrink: 0;
  background: var(--ct-bg-secondary);
}
.player-cover--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ct-text-3);
}

.player-track {
  flex-shrink: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
  transition: background 0.1s;
}
.player-track:hover { background: var(--sb-bg-hover); }

.player-track-name {
  font-size: 12px; font-weight: 500;
  color: var(--ct-text);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.player-track-artist {
  font-size: 10.5px;
  color: var(--ct-text-2);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

.player-controls { flex-shrink: 0; }

.player-progress-bar {
  flex: 1; min-width: 60px; height: 4px;
  border-radius: 2px;
  background: var(--ct-border);
  cursor: pointer;
  position: relative; overflow: hidden;
}
.player-progress-bar:hover { height: 6px; }

.player-progress-fill {
  height: 100%;
  border-radius: 2px;
  background: rgb(var(--ct-accent-rgb));
  transition: width 0.1s linear;
}

.player-time {
  flex-shrink: 0;
  font-size: 10.5px;
  color: var(--ct-text-2);
  font-feature-settings: 'tnum';
  min-width: 80px; text-align: center; white-space: nowrap;
}

.player-volume-btn,
.player-close-btn { flex-shrink: 0; }
</style>
