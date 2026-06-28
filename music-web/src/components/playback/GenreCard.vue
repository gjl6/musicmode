<template>
  <div class="genre-card" @click="$emit('click')">
    <div class="genre-card-cover">
      <CoverArt
        :src="coverUrl"
        :alt="genre.genre || ''"
        :size="coverSize"
      />
      <div class="genre-card-overlay">
        <n-button
          circle
          class="card-play-btn"
          :size="size >= 160 ? 'large' : 'medium'"
          @click.stop="$emit('play')"
        >
          <template #icon>
            <n-icon :size="size >= 160 ? 24 : 18"><PlayOutline /></n-icon>
          </template>
        </n-button>
      </div>
    </div>
    <div class="genre-card-body">
      <span class="genre-card-name" :title="genre.genre">{{ genre.genre || '—' }}</span>
    </div>
    <div class="genre-card-meta">
      <span class="genre-card-count">{{ $t('genre.songCount', { count: genre.songCount || 0 }) }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { PlayOutline } from '@vicons/ionicons5'
import CoverArt from './CoverArt.vue'
import { subsonicGetCoverArtUrl } from '@/api/playback/subsonic.js'

const props = defineProps({
  genre: { type: Object, required: true },
  size: { type: [Number, String], default: 160 },
})

defineEmits(['click', 'play'])

const coverSize = computed(() => typeof props.size === 'number' ? props.size : Number(props.size) || 160)

const coverUrl = computed(() => {
  const artId = props.genre?.coverArt || props.genre?.id
  return artId ? subsonicGetCoverArtUrl(artId, coverSize.value) : ''
})
</script>

<style scoped>
.genre-card {
  display: flex;
  flex-direction: column;
  border-radius: var(--radius-md, 10px);
  background: var(--gradient-card);
  border: var(--border-width-default, 1px) solid var(--ct-border);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.genre-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.genre-card:active {
  transform: scale(0.98);
}

/* ── 封面区 ── */
.genre-card-cover {
  position: relative;
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--ct-bg-secondary);
  overflow: hidden;
}

/* hover 悬浮层 */
.genre-card-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.35);
  opacity: 0;
  transition: opacity 0.2s;
}

.genre-card:hover .genre-card-overlay {
  opacity: 1;
}

/* 播放按钮 */
.card-play-btn {
  background: rgba(255, 255, 255, 0.92) !important;
  color: var(--ct-accent) !important;
  border: none !important;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
  transition: transform 0.15s, box-shadow 0.15s;
}
.card-play-btn:hover {
  transform: scale(1.08);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.35);
}

/* ── 信息区 ── */
.genre-card-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px 4px;
  min-width: 0;
}

.genre-card-name {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--ct-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}

/* ── 元信息 ── */
.genre-card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 12px 10px;
}

.genre-card-count {
  font-size: var(--text-xs);
  color: var(--ct-text-3);
  font-variant-numeric: tabular-nums;
}
</style>
