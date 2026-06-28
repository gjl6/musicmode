<template>
  <div class="album-card" @click="$emit('click')">
    <div class="album-card-cover">
      <CoverArt
        :src="coverUrl"
        :alt="album.name || ''"
        :size="coverSize"
      />
      <div class="album-card-overlay">
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
        <n-button
          v-if="showFav"
          circle
          class="card-fav-btn"
          size="tiny"
          :type="starred ? 'error' : 'tertiary'"
          @click.stop="$emit('toggleFav')"
        >
          <template #icon>
            <n-icon :size="14" :color="starred ? '#EF4444' : undefined">
              <Heart v-if="starred" />
              <HeartOutline v-else />
            </n-icon>
          </template>
        </n-button>
      </div>
    </div>
    <div class="album-card-body">
      <span class="album-card-name" :title="album.name">{{ album.name || '—' }}</span>
      <span
        v-if="album.artistId"
        class="album-card-artist link"
        :title="album.artist"
        @click.stop="goArtist(album)"
      >{{ album.artist || '—' }}</span>
      <span v-else class="album-card-artist" :title="album.artist">{{ album.artist || '—' }}</span>
    </div>
    <div class="album-card-meta">
      <span v-if="album.genre" class="album-card-type">类型:{{ album.genre }}</span>
      <span v-if="album.year > 0" class="album-card-year">{{ album.year }}</span>
      <span class="album-card-count">{{ album.songCount || 0 }} 首</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { PlayOutline, HeartOutline, Heart } from '@vicons/ionicons5'
import CoverArt from './CoverArt.vue'
import { subsonicGetCoverArtUrl } from '@/api/playback/subsonic.js'

const router = useRouter()

const props = defineProps({
  album: { type: Object, required: true },
  size: { type: [Number, String], default: 160 },
  starred: { type: Boolean, default: false },
  showFav: { type: Boolean, default: true },
})

defineEmits(['click', 'play', 'toggleFav'])

const coverSize = computed(() => typeof props.size === 'number' ? props.size : Number(props.size) || 160)

const coverUrl = computed(() => {
  const artId = props.album.coverArt || props.album.id
  return artId ? subsonicGetCoverArtUrl(artId, coverSize.value) : ''
})

function goArtist(album) {
  if (album.artistId) router.push(`/player/artists/${album.artistId}`)
}
</script>

<style scoped>
.album-card {
  display: flex;
  flex-direction: column;
  height: 100%;
  border-radius: var(--radius-md, 10px);
  background: var(--gradient-card);
  border: var(--border-width-default, 1px) solid var(--ct-border);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.album-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.album-card:active {
  transform: scale(0.98);
}

/* ── 封面区 ── */
.album-card-cover {
  position: relative;
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--ct-bg-secondary);
  overflow: hidden;
}

/* hover 悬浮层 */
.album-card-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.35);
  opacity: 0;
  transition: opacity 0.2s;
}

.album-card:hover .album-card-overlay {
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
.album-card-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px 4px;
  min-width: 0;
}

.album-card-name {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--ct-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}

.album-card-artist {
  font-size: var(--text-xs);
  color: var(--ct-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.album-card-artist.link {
  cursor: pointer;
}
.album-card-artist.link:hover {
  color: var(--ct-accent);
}

/* ── 元信息 ── */
.album-card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 12px 10px;
}

.album-card-year {
  font-size: 10px;
  font-weight: 600;
  padding: 1px 5px;
  border-radius: 3px;
  color: var(--ct-accent);
  background: rgb(var(--ct-accent-rgb) / 0.1);
  letter-spacing: 0.5px;
}

.album-card-count {
  font-size: var(--text-xs);
  color: var(--ct-text-3);
  font-variant-numeric: tabular-nums;
}

.album-card-type {
  font-size: 10px;
  font-weight: 500;
  color: var(--ct-text-3);
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--ct-bg-secondary);
}
</style>
