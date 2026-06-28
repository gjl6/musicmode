<template>
  <div class="playlist-card" @click="$emit('click')">
    <!-- 封面区 + 悬停 overlay -->
    <div class="card-cover-wrap">
      <PlaylistCover
        :entries="playlist.entry || []"
        :size="size"
        :fallback-art="playlist.coverArt || (playlist.id ? 'playlist-' + playlist.id : '')"
      />
      <div class="cover-overlay">
        <n-button
          class="overlay-play-btn"
          circle
          size="small"
          @click.stop="$emit('play')"
        >
          <template #icon>
            <n-icon :size="20"><PlayOutline /></n-icon>
          </template>
        </n-button>
      </div>
      <n-button
        class="card-fav-btn"
        circle
        size="tiny"
        :type="favorited ? 'error' : 'tertiary'"
        @click.stop="$emit('toggleFav')"
      >
        <template #icon>
          <n-icon :size="15" :color="favorited ? '#EF4444' : undefined">
            <Heart v-if="favorited" />
            <HeartOutline v-else />
          </n-icon>
        </template>
      </n-button>
    </div>

    <!-- 信息区 -->
    <div class="card-info">
      <div class="card-title" :title="playlist.name">{{ playlist.name || '—' }}</div>
      <div class="card-meta">
        <span>{{ $t('playlist.songCount', { count: playlist.songCount || 0 }) }}</span>
        <span v-if="durationText" class="meta-sep">·</span>
        <span v-if="durationText">{{ durationText }}</span>
      </div>
      <div v-if="playlist.owner" class="card-owner">{{ playlist.owner }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { PlayOutline, HeartOutline, Heart } from '@vicons/ionicons5'
import PlaylistCover from './PlaylistCover.vue'

const props = defineProps({
  playlist: { type: Object, required: true },
  size: { type: [Number, String], default: 180 },
  favorited: { type: Boolean, default: false },
})

defineEmits(['click', 'play', 'toggleFav'])

const durationText = computed(() => {
  const dur = props.playlist?.duration
  if (!dur) return ''
  const s = Math.floor(Number(dur))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  if (h > 0) return `${h} 小时 ${m} 分钟`
  return `${m} 分钟`
})
</script>

<style scoped>
.playlist-card {
  cursor: pointer;
  border-radius: var(--radius-lg, 10px);
  transition: transform 0.2s ease;
}
.playlist-card:hover {
  transform: translateY(-2px);
}

/* ── 封面 + overlay ── */
.card-cover-wrap {
  position: relative;
  width: fit-content;
  border-radius: var(--radius-lg, 10px);
  overflow: hidden;
}

.cover-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.35);
  opacity: 0;
  transition: opacity 0.18s ease;
}

.card-cover-wrap:hover .cover-overlay {
  opacity: 1;
}

/* 播放按钮 */
.overlay-play-btn {
  background: rgba(255, 255, 255, 0.92) !important;
  color: var(--ct-accent, #6366F1) !important;
  border: none !important;
  width: 42px !important;
  height: 42px !important;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
  transition: transform 0.15s;
}
.overlay-play-btn:hover {
  transform: scale(1.1);
}

/* 收藏按钮 */
.card-fav-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  background: rgba(255, 255, 255, 0.85) !important;
  border: none !important;
  backdrop-filter: blur(4px);
  transition: transform 0.15s;
}
.card-fav-btn:hover {
  transform: scale(1.12);
}

/* ── 信息区 ── */
.card-info {
  padding: 10px 4px 4px;
  min-width: 0;
}

.card-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ct-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--ct-text-3);
  margin-top: 2px;
}

.meta-sep {
  color: var(--ct-text-3);
  opacity: 0.5;
}

.card-owner {
  font-size: 11px;
  color: var(--ct-text-3);
  margin-top: 1px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
