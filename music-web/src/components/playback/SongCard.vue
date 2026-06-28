<template>
  <div class="song-card" @click="goDetail">
    <!-- 封面 -->
    <div class="song-card-cover">
      <img
        v-if="coverUrl"
        :src="coverUrl"
        class="song-card-img"
        :alt="song.title || ''"
      />
      <n-icon v-else :size="36" color="var(--ct-text-3)">
        <MusicalNotesOutline />
      </n-icon>
      <div class="song-card-overlay">
        <n-button
          circle
          class="card-play-btn"
          size="large"
          @click.stop="$emit('play')"
        >
          <template #icon>
            <n-icon :size="24"><PlayOutline /></n-icon>
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
            <n-icon :size="15" :color="starred ? '#EF4444' : undefined">
              <Heart v-if="starred" />
              <HeartOutline v-else />
            </n-icon>
          </template>
        </n-button>
      </div>
    </div>

    <!-- 信息 -->
    <div class="song-card-body">
      <span class="song-card-title" :title="song.title">{{ song.title || '—' }}</span>
      <!-- 多艺术家：每个独立可点击 -->
      <span v-if="song.artists && song.artists.length > 1" class="song-card-artist">
        <span
          v-for="(a, i) in song.artists"
          :key="a.id"
          class="song-card-artist-chip"
          :class="{ link: a.id }"
          :title="a.name"
          @click.stop="goArtist(a.id)"
        >{{ a.name }}<span v-if="i < song.artists.length - 1"> / </span></span>
      </span>
      <!-- 单艺术家（向后兼容） -->
      <span
        v-else
        class="song-card-artist"
        :class="{ link: song.artistId }"
        :title="displayArtist"
        @click.stop="goArtist"
      >{{ displayArtist }}</span>
      <slot name="rating">
        <StarRatingComp
          v-if="showRating"
          :rating="Number(song.userRating) || 0"
          :song-id="song.id"
          :size="13"
          @rated="(val) => $emit('rate', { songId: Number(song.id), rating: val })"
          @click.stop
        />
      </slot>
    </div>

    <!-- 元信息 -->
    <div class="song-card-meta">
      <span v-if="song.suffix" class="song-card-format">{{ song.suffix.toUpperCase() }}</span>
      <span class="song-card-duration">{{ formatDuration(song.duration) }}</span>
      <span v-if="song.bitRate" class="song-card-bitrate">{{ song.bitRate }} kbps</span>
      <span v-if="showExtraActions" class="song-card-extra">
        <button class="card-mini-btn" title="添加到队列" @click.stop="$emit('addToQueue')">
          <n-icon :size="13"><AddOutline /></n-icon>
        </button>
        <button class="card-mini-btn" title="添加到歌单" @click.stop="$emit('addToPlaylist')">
          <n-icon :size="13"><ListOutline /></n-icon>
        </button>
      </span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { PlayOutline, MusicalNotesOutline, HeartOutline, Heart, AddOutline, ListOutline } from '@vicons/ionicons5'
import { subsonicGetCoverArtUrl } from '@/api/playback/subsonic.js'
import StarRatingComp from './StarRating.vue'

const router = useRouter()

const props = defineProps({
  song: { type: Object, required: true },
  starred: { type: Boolean, default: false },
  showFav: { type: Boolean, default: true },
  showRating: { type: Boolean, default: true },
  showExtraActions: { type: Boolean, default: false },
})

defineEmits(['play', 'toggleFav', 'rate', 'addToQueue', 'addToPlaylist'])

const displayArtist = computed(() =>
  props.song.displayArtist || props.song.artist || '—'
)

const coverUrl = computed(() => {
  const artId = props.song.coverArt || props.song.albumId || props.song.id
  return artId ? subsonicGetCoverArtUrl(artId, 160) : ''
})

function formatDuration(s) {
  if (!s || !isFinite(s)) return '—'
  const m = Math.floor(s / 60)
  const sec = Math.floor(s % 60)
  return `${m}:${String(sec).padStart(2, '0')}`
}

function goDetail() {
  if (props.song.id) router.push(`/player/songs/${props.song.id}`)
}

function goArtist(artistId) {
  const id = artistId || props.song.artistId
  if (id) router.push(`/player/artists/${id}`)
}
</script>

<style scoped>
.song-card {
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
.song-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}
.song-card:active {
  transform: scale(0.98);
}

/* 封面区 */
.song-card-cover {
  position: relative;
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--ct-bg-secondary);
  overflow: hidden;
}
.song-card-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.song-card-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.35);
  opacity: 0;
  transition: opacity 0.2s;
}
.song-card:hover .song-card-overlay {
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

/* 信息区 */
.song-card-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px 4px;
  min-width: 0;
}
.song-card-title {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--ct-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.song-card-artist {
  font-size: var(--text-xs);
  color: var(--ct-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 多艺术家 chip — 允许多行，每个独立可点 */
.song-card-artist:has(.song-card-artist-chip) {
  white-space: normal;
  line-height: 1.5;
}
.song-card-artist-chip {
  display: inline;
}
.song-card-artist-chip.link {
  cursor: pointer;
}
.song-card-artist-chip.link:hover {
  color: var(--ct-accent);
}
.song-card-artist.link {
  cursor: pointer;
}
.song-card-artist.link:hover {
  color: var(--ct-accent);
}

/* 元信息 */
.song-card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 12px 10px;
  gap: 6px;
}
.song-card-format {
  font-size: 10px;
  font-weight: 600;
  padding: 1px 5px;
  border-radius: 3px;
  color: var(--ct-accent);
  background: rgb(var(--ct-accent-rgb) / 0.1);
  letter-spacing: 0.5px;
}
.song-card-duration {
  font-size: var(--text-xs);
  color: var(--ct-text-3);
  font-variant-numeric: tabular-nums;
}
.song-card-bitrate {
  font-size: 11px;
  color: var(--ct-text-3);
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--ct-bg-secondary);
}

/* 额外操作按钮（网格模式） */
.song-card-extra {
  display: flex;
  gap: 2px;
  margin-left: auto;
}
.card-mini-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: none;
  background: transparent;
  color: var(--ct-text-3);
  cursor: pointer;
  transition: color 0.15s, background 0.15s;
}
.card-mini-btn:hover {
  color: var(--ct-accent);
  background: rgb(var(--ct-accent-rgb) / 0.08);
}
</style>
