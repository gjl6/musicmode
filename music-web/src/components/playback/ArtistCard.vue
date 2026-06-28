<template>
  <div class="artist-card" @click="$emit('click')">
    <div class="artist-card-cover">
      <CoverArt
        :src="coverUrl"
        :alt="artist.name || ''"
        :size="coverSize"
        type="artist"
      />
      <div class="artist-card-overlay">
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
    <div class="artist-card-body">
      <span class="artist-card-name" :title="artist.name">{{ artist.name || '—' }}</span>
    </div>
    <div class="artist-card-meta">
      <span v-if="genderLabel" class="artist-card-tag gender">{{ genderLabel }}</span>
      <span v-if="artist.country" class="artist-card-tag country">{{ artist.country }}</span>
      <span class="artist-card-count">{{ $t('artist.albumCount', { count: artist.albumCount || 0 }) }}</span>
      <span class="artist-card-count">{{ $t('artist.songCount', { count: artist.songCount || 0 }) }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { HeartOutline, Heart } from '@vicons/ionicons5'
import CoverArt from './CoverArt.vue'
import { subsonicGetCoverArtUrl } from '@/api/playback/subsonic.js'

const { t } = useI18n()

const props = defineProps({
  artist: { type: Object, required: true },
  size: { type: [Number, String], default: 160 },
  starred: { type: Boolean, default: false },
  showFav: { type: Boolean, default: false },
})

defineEmits(['click', 'toggleFav'])

const coverSize = computed(() => typeof props.size === 'number' ? props.size : Number(props.size) || 160)

const coverUrl = computed(() => {
  const artId = props.artist.coverArt || props.artist.id
  return artId ? subsonicGetCoverArtUrl(artId, coverSize.value) : ''
})

const genderLabel = computed(() => {
  const g = props.artist.gender
  if (g === 1) return t('artist.genderMale')
  if (g === 2) return t('artist.genderFemale')
  if (g === 3) return t('artist.genderGroup')
  return null
})
</script>

<style scoped>
.artist-card {
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
.artist-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}
.artist-card:active {
  transform: scale(0.98);
}

/* ── 封面区 ── */
.artist-card-cover {
  position: relative;
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--ct-bg-secondary);
  overflow: hidden;
}

/* hover 悬浮层 */
.artist-card-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.25);
  opacity: 0;
  transition: opacity 0.2s;
}
.artist-card:hover .artist-card-overlay {
  opacity: 1;
}

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

/* ── 名称 ── */
.artist-card-body {
  padding: 10px 12px 4px;
  min-width: 0;
}

.artist-card-name {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--ct-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}

/* ── 元信息 ── */
.artist-card-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 2px 12px 10px;
}

.artist-card-tag {
  font-size: 10px;
  font-weight: 500;
  padding: 1px 6px;
  border-radius: 4px;
  color: var(--ct-text-3);
  background: var(--ct-bg-secondary);
}
.artist-card-tag.gender  { color: var(--ct-accent); background: rgb(var(--ct-accent-rgb) / 0.1); }
.artist-card-tag.country { color: var(--ct-accent); background: rgb(var(--ct-accent-rgb) / 0.1); }

.artist-card-count {
  font-size: var(--text-xs);
  color: var(--ct-text-3);
  font-variant-numeric: tabular-nums;
}
</style>
