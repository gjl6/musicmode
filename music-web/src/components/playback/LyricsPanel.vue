<template>
  <div class="lyrics-panel" :class="{ 'lyrics-panel--visible': visible, 'lyrics-panel--empty': isEmpty }">
    <!-- Header -->
    <div class="lyrics-header">
      <span class="lyrics-header-title">歌词</span>
      <n-button text class="lyrics-close" @click="$emit('close')">
        <template #icon><n-icon :size="16"><CloseOutline /></n-icon></template>
      </n-button>
    </div>

    <!-- Loading -->
    <div v-if="player.lyricsLoading" class="lyrics-status">
      <n-spin :size="20" />
      <span>加载中...</span>
    </div>

    <!-- Empty -->
    <div v-else-if="isEmpty" class="lyrics-status">
      <n-icon :size="24" color="var(--ct-text-3)"><MusicalNotesOutline /></n-icon>
      <span>暂无歌词</span>
    </div>

    <!-- Synced LRC lyrics -->
    <div
      v-else-if="player.lyricsSynced"
      ref="scrollRef"
      class="lyrics-scroll"
    >
      <div class="lyrics-scroll-inner">
        <div
          v-for="(line, i) in player.lyrics"
          :key="i"
          ref="lineRefs"
          class="lyrics-line"
          :class="{
            'lyrics-line--active': i === player.currentLyricIndex,
            'lyrics-line--past': i < player.currentLyricIndex,
          }"
        >
          {{ line.value }}
        </div>
      </div>
    </div>

    <!-- Plain text lyrics -->
    <div v-else class="lyrics-plain">
      <div
        v-for="(line, i) in player.lyrics"
        :key="i"
        class="lyrics-line lyrics-line--static"
      >{{ line.value }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { CloseOutline, MusicalNotesOutline } from '@vicons/ionicons5'
import { usePlayerStore } from '@/store/playback/player.js'

const props = defineProps({
  visible: { type: Boolean, default: false },
})
defineEmits(['close'])

const player = usePlayerStore()

const isEmpty = computed(() =>
  !player.lyricsLoading && player.lyrics.length === 0
)

// ── 同步滚动 ──
const scrollRef = ref(null)

watch(
  () => player.currentLyricIndex,
  (idx) => {
    if (!scrollRef.value || idx < 0) return
    nextTick(() => {
      const el = scrollRef.value.querySelector('.lyrics-line--active')
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      }
    })
  },
)
</script>

<style scoped>
/* ═══════════════════════════════════════════════════════════════
   歌词面板 — Claymorphism 粘土风格
   ═══════════════════════════════════════════════════════════════ */

.lyrics-panel {
  display: flex;
  flex-direction: column;
  max-height: 0;
  overflow: hidden;
  border-top: 1px solid transparent;
  background: var(--gradient-card, var(--ct-bg-secondary));
  border-radius: 0 0 var(--radius-xl) var(--radius-xl);
  transition: max-height 0.35s cubic-bezier(0.4, 0, 0.2, 1),
              border-color 0.35s ease;
}
.lyrics-panel--visible {
  max-height: 320px;
  border-top-color: var(--ct-border);
}
.lyrics-panel--empty.lyrics-panel--visible {
  max-height: 80px;
}

/* ── Header ── */
.lyrics-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px 4px;
  flex-shrink: 0;
}
.lyrics-header-title {
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--ct-text-2);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}
.lyrics-close {
  color: var(--ct-text-3) !important;
}
.lyrics-close:hover {
  color: var(--ct-text) !important;
}

/* ── Status（loading / empty）── */
.lyrics-status {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 20px 0 24px;
  font-size: var(--text-sm);
  color: var(--ct-text-3);
}

/* ═══════ 同步滚动容器 ═══════ */
.lyrics-scroll {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 6px 0;
  mask-image: linear-gradient(
    to bottom,
    transparent 0%,
    black 15%,
    black 85%,
    transparent 100%
  );
  -webkit-mask-image: linear-gradient(
    to bottom,
    transparent 0%,
    black 15%,
    black 85%,
    transparent 100%
  );
}
.lyrics-scroll::-webkit-scrollbar { width: 0; }

.lyrics-scroll-inner {
  display: flex;
  flex-direction: column;
  padding: 40px 0;
}

/* ═══════ 静态文本容器 ═══════ */
.lyrics-plain {
  flex: 1;
  overflow-y: auto;
  padding: 12px 20px 20px;
}

/* ═══════ 歌词行 ═══════ */
.lyrics-line {
  padding: 5px 20px;
  font-size: var(--text-md);
  line-height: 1.7;
  color: var(--ct-text-3);
  text-align: center;
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: default;
  white-space: pre-wrap;
}

/* 已播放行 */
.lyrics-line--past {
  color: var(--ct-text-2);
}

/* 当前行 — 粘土凸起高亮 */
.lyrics-line--active {
  color: var(--ct-accent);
  font-size: calc(var(--text-md) + 2px);
  font-weight: 700;
  padding: 7px 20px;
  background: var(--gradient-button, var(--ct-bg-secondary));
  border-radius: var(--radius-md);
  box-shadow:
    inset 1px 1px 3px rgba(255 255 255 / 0.15),
    inset -2px -2px 5px rgba(0 0 0 / 0.08),
    0 1px 3px rgb(var(--ct-accent-rgb) / 0.08);
  margin: 2px 0;
}

/* 静态歌词行 */
.lyrics-line--static {
  color: var(--ct-text);
  text-align: left;
  padding: 4px 0;
}
</style>
