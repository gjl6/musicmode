<template>
  <aside class="queue-panel" :class="[variant, { collapsed }]">
    <!-- ═══ 折叠态：仅图标 ═══ -->
    <div v-if="collapsed" class="qp-collapsed" @click="collapsed = false" title="展开播放队列">
      <n-icon :size="20"><ListOutline /></n-icon>
      <span v-if="upcomingCount" class="qp-collapsed-badge">{{ upcomingCount }}</span>
    </div>

    <!-- ═══ 展开态 ═══ -->
    <template v-else>
      <!-- 头部 -->
      <div class="qp-header">
        <span class="qp-header-title">播放队列</span>
        <div class="qp-header-actions">
          <n-button v-if="queue.length" text size="tiny" type="error" @click="player.clearQueue()" title="清空队列">
            <template #icon><n-icon :size="14"><TrashOutline /></n-icon></template>
          </n-button>
          <n-button text size="tiny" class="qp-collapse-btn" @click="collapsed = true" title="收起队列">
            <template #icon><n-icon :size="16"><ChevronForwardOutline /></n-icon></template>
          </n-button>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="!queue.length" class="qp-empty">
        <div class="qp-empty-icon">
          <n-icon :size="36" color="var(--ct-text-3)"><MusicalNotesOutline /></n-icon>
        </div>
        <span class="qp-empty-text">队列为空</span>
        <span class="qp-empty-hint">播放歌曲后将显示在此处</span>
      </div>

      <!-- ═══ 队列内容 ═══ -->
      <template v-else>
        <!-- ── 正在播放：粘土卡片 ── -->
        <div class="qp-now-section">
          <div class="qp-now-card">
            <!-- 圆形旋转封面（居中顶部） -->
            <div ref="coverEl" class="qp-cover" :class="{ spinning: player.isPlaying }">
              <img
                v-if="nowCover"
                :src="nowCover"
                class="qp-cover-img"
                alt="cover"
              />
              <n-icon v-else :size="48" color="var(--ct-text-3)"><MusicalNotesOutline /></n-icon>
            </div>

            <!-- 歌曲信息：标题左 + 艺术家右 + 收藏 -->
            <div class="qp-now-meta">
              <n-tooltip trigger="hover" :delay="600">
                <template #trigger>
                  <span
                    class="qp-now-title"
                    :class="{ clickable: player.current?.id }"
                    @click="goSongDetail"
                  >{{ player.displayTitle }}</span>
                </template>
                {{ player.displayTitle }}
              </n-tooltip>
              <n-tooltip trigger="hover" :delay="600">
                <template #trigger>
                  <span
                    class="qp-now-artist"
                    :class="{ clickable: player.current?.artistId }"
                    @click="goArtistDetail"
                  >{{ player.displayArtist }}</span>
                </template>
                {{ player.displayArtist }}
              </n-tooltip>
              <n-button
                text
                size="tiny"
                class="qp-now-fav"
                :class="{ active: player.isFavorited }"
                @click.stop="player.toggleFavorite()"
                :title="player.isFavorited ? '取消收藏' : '收藏'"
              >
                <template #icon>
                  <n-icon :size="16" :color="player.isFavorited ? '#EF4444' : undefined">
                    <Heart v-if="player.isFavorited" />
                    <HeartOutline v-else />
                  </n-icon>
                </template>
              </n-button>
            </div>
          </div>
        </div>

        <!-- ── 接下来播放列表 ── -->
        <div class="qp-section qp-section--grow">
          <span class="qp-section-label">
            接下来 · {{ upcoming.length }} 首
          </span>
          <div class="qp-list">
            <div
              v-for="(s, i) in upcoming"
              :key="s._queueKey || i"
              class="qp-item"
              :class="{
                'qp-item--dragging': dragIndex === i,
                'qp-item--dragover': dragOverIndex === i,
              }"
              draggable="true"
              @dragstart="onDragStart($event, i)"
              @dragover.prevent="onDragOver($event, i)"
              @dragend="onDragEnd"
              @drop="onDrop($event, i)"
              @dblclick="player.playIndex(currentIndex + 1 + i)"
            >
              <n-icon :size="12" color="var(--ct-text-3)" class="qp-item-grip"><MenuOutline /></n-icon>
              <span class="qp-item-idx">{{ currentIndex + 2 + i }}</span>
              <div class="qp-item-thumb-wrap">
                <img
                  v-if="getThumb(s)"
                  :src="getThumb(s)"
                  class="qp-item-thumb"
                  alt=""
                />
                <n-icon v-else :size="14" color="var(--ct-text-3)"><MusicalNotesOutline /></n-icon>
              </div>
              <div class="qp-item-info">
                <span class="qp-item-title" :title="s.title">{{ s.title || '—' }}</span>
                <span class="qp-item-artist" :title="s.displayArtist || s.artist">{{ s.displayArtist || s.artist || '—' }}</span>
              </div>
              <span class="qp-item-dur">{{ formatDur(s.duration) }}</span>
              <div class="qp-item-actions">
                <n-button text size="tiny" class="qp-item-play" @click.stop="player.playIndex(currentIndex + 1 + i)" title="播放">
                  <template #icon><n-icon :size="14"><PlayOutline /></n-icon></template>
                </n-button>
                <n-button text size="tiny" class="qp-item-remove" @click.stop="player.removeFromQueue(currentIndex + 1 + i)" title="移除">
                  <template #icon><n-icon :size="14"><CloseOutline /></n-icon></template>
                </n-button>
              </div>
            </div>
          </div>
        </div>

        <!-- ── 已播放（折叠） ── -->
        <div v-if="history.length" class="qp-section">
          <button class="qp-section-label qp-history-toggle" @click="showHistory = !showHistory">
            已播放 · {{ history.length }} 首
            <n-icon :size="12" class="qp-history-arrow" :class="{ open: showHistory }"><ChevronDownOutline /></n-icon>
          </button>
          <div v-if="showHistory" class="qp-list">
            <div
              v-for="(s, i) in history"
              :key="s._queueKey || ('h' + i)"
              class="qp-item qp-item--past"
              @dblclick="player.playIndex(i)"
            >
              <span class="qp-item-idx">{{ i + 1 }}</span>
              <div class="qp-item-info">
                <span class="qp-item-title" :title="s.title">{{ s.title || '—' }}</span>
                <span class="qp-item-artist">{{ s.displayArtist || s.artist || '—' }}</span>
              </div>
              <span class="qp-item-dur">{{ formatDur(s.duration) }}</span>
            </div>
          </div>
        </div>
      </template>
    </template>
  </aside>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import {
  MusicalNotesOutline, PlayOutline, CloseOutline,
  HeartOutline, Heart, ListOutline,
  ChevronForwardOutline, ChevronDownOutline,
  TrashOutline, MenuOutline,
} from '@vicons/ionicons5'
import { usePlayerStore } from '@/store/playback/player.js'
import { subsonicGetCoverArtUrl } from '@/api/playback/subsonic.js'

const props = defineProps({
  /** 外部控制折叠状态（v-model） */
  modelValue: { type: Boolean, default: undefined },
  /** 视觉变体：sidebar（默认粘土侧栏）| immersive（全屏暗色沉浸） */
  variant: { type: String, default: 'sidebar' },
})

const emit = defineEmits(['update:modelValue'])

const router = useRouter()
const player = usePlayerStore()

// collapsed：优先外部控制，否则内部自治
const _internalCollapsed = ref(false)
const collapsed = computed({
  get: () => props.modelValue !== undefined ? props.modelValue : _internalCollapsed.value,
  set: (v) => {
    _internalCollapsed.value = v
    emit('update:modelValue', v)
  },
})

const showHistory = ref(false)

// ── 同步 CSS 变量给 MiniPlayer 定位用 ──
function syncPanelWidth() {
  document.documentElement.style.setProperty(
    '--queue-panel-width',
    collapsed.value ? '42px' : '280px'
  )
}
watch(collapsed, syncPanelWidth)
onMounted(syncPanelWidth)
onUnmounted(() => {
  document.documentElement.style.removeProperty('--queue-panel-width')
})

const queue = computed(() => player.queue)
const currentIndex = computed(() => player.currentIndex)
const upcomingCount = computed(() => {
  const q = player.queue
  const idx = player.currentIndex
  if (idx < 0 || idx >= q.length - 1) return 0
  return q.length - idx - 1
})

/** 即将播放（当前曲目之后） */
const upcoming = computed(() => {
  const q = player.queue
  const idx = player.currentIndex
  if (idx < 0 || idx >= q.length - 1) return []
  return q.slice(idx + 1)
})

/** 已播放（当前曲目之前） */
const history = computed(() => {
  const q = player.queue
  const idx = player.currentIndex
  if (idx <= 0) return []
  return q.slice(0, idx)
})

/** 当前播放封面 */
const nowCover = computed(() => {
  const artId = player.current?.coverArt || player.current?.albumId
  return artId ? subsonicGetCoverArtUrl(artId, 200) : null
})

/** 列表项封面缩略图 */
function getThumb(song) {
  const artId = song?.coverArt || song?.albumId
  return artId ? subsonicGetCoverArtUrl(artId, 64) : null
}

function formatDur(sec) {
  if (!sec || !isFinite(sec)) return '—'
  const s = Number(sec)
  return `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`
}

function goSongDetail() {
  const id = player.current?.id
  if (id) router.push(`/player/songs/${id}`)
}

function goArtistDetail() {
  const id = player.current?.artistId
  if (id) router.push(`/player/artists/${id}`)
}

// ── 拖拽排序 ──
const dragIndex = ref(null)
const dragOverIndex = ref(null)

function onDragStart(e, i) {
  dragIndex.value = i
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', String(i))
}

function onDragOver(e, i) {
  if (dragIndex.value === null) return
  dragOverIndex.value = i
}

function onDragEnd() {
  dragIndex.value = null
  dragOverIndex.value = null
}

function onDrop(_e, toI) {
  const fromI = dragIndex.value
  if (fromI === null || fromI === toI) {
    onDragEnd()
    return
  }
  const q = player.queue
  const idx = player.currentIndex
  const from = idx + 1 + fromI
  const to = idx + 1 + toI
  const [item] = q.splice(from, 1)
  q.splice(to, 0, item)
  onDragEnd()
}

// ── 封面旋转（reduced-motion 下用 JS rAF 驱动，绕过浏览器动画节流）──
const coverEl = ref(null)
const isReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
let _rafId = null
const SPIN_SECONDS = 12

function startCoverSpin() {
  if (_rafId !== null || !coverEl.value) return
  const startTime = performance.now()
  const tick = (now) => {
    if (!coverEl.value) { _rafId = null; return }
    const elapsed = (now - startTime) / 1000
    const deg = (elapsed / SPIN_SECONDS) * 360 % 360
    coverEl.value.style.transform = `translateZ(0) rotate(${deg}deg)`
    _rafId = requestAnimationFrame(tick)
  }
  _rafId = requestAnimationFrame(tick)
}

function stopCoverSpin() {
  if (_rafId !== null) {
    cancelAnimationFrame(_rafId)
    _rafId = null
  }
}

watch(() => player.isPlaying, async (playing) => {
  if (!isReducedMotion) return
  await nextTick()
  if (playing) {
    startCoverSpin()
  } else {
    stopCoverSpin()
  }
})

// v-if 切换导致 cover 元素重建后，自动恢复旋转
watch(coverEl, async (el) => {
  if (el && player.isPlaying && isReducedMotion) {
    await nextTick()
    startCoverSpin()
  }
})

onMounted(async () => {
  await nextTick()
  if (isReducedMotion && player.isPlaying) startCoverSpin()
})

onUnmounted(() => {
  stopCoverSpin()
})
</script>

<style scoped>
/* ═══════════════════════════════════════════════════════════════
   QueuePanel — 侧边播放队列面板（粘土风格）
   ═══════════════════════════════════════════════════════════════ */

.queue-panel {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border: var(--border-width-strong, 3px) solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--gradient-card, var(--ct-bg-secondary));
  box-shadow: var(--effect-card-inner), var(--effect-card-outer);
  transition: width var(--transition-slow);
  overflow: hidden;
}
.queue-panel.collapsed {
  width: 42px;
}

/* ═══ 折叠态 ═══ */
.qp-collapsed {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 16px;
  gap: 6px;
  color: var(--ct-text-2);
  cursor: pointer;
  transition: color var(--transition-fast);
  position: relative;
}
.qp-collapsed:hover {
  color: var(--ct-accent);
}
.qp-collapsed-badge {
  font-size: 10px;
  font-weight: 700;
  color: var(--ct-accent);
  font-feature-settings: 'tnum';
}

/* ═══ 头部 ═══ */
.qp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px 10px;
  flex-shrink: 0;
  border-bottom: var(--border-width-default) solid var(--color-border);
}
.qp-header-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ct-text);
}
.qp-header-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}
.qp-collapse-btn {
  color: var(--ct-text-3) !important;
}
.qp-collapse-btn:hover {
  color: var(--ct-text) !important;
}

/* ═══ 空状态 ═══ */
.qp-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px 16px;
  text-align: center;
}
.qp-empty-icon {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--gradient-button, var(--ct-bg-secondary));
  box-shadow: var(--effect-input-inner);
  margin-bottom: 4px;
}
.qp-empty-text {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--ct-text-3);
}
.qp-empty-hint {
  font-size: var(--text-2xs);
  color: var(--ct-text-3);
  opacity: 0.6;
}

/* ═══════════════════════════════════════════════════════════════
   正在播放 — 粘土卡片
   ═══════════════════════════════════════════════════════════════ */
.qp-now-section {
  flex-shrink: 0;
  padding: 14px 10px 10px;
}

.qp-now-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 16px 14px 14px;
  border-radius: var(--radius-xl);
  border: var(--border-width-strong, 3px) solid var(--color-border);
  background: var(--gradient-card);
  box-shadow: var(--effect-card-inner), var(--effect-card-outer);
}

/* ── 圆形旋转封面（无光环，直接旋转）── */
.qp-cover {
  width: 120px;
  height: 120px;
  flex-shrink: 0;
  border-radius: 50%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  border: var(--border-width-strong, 3px) solid var(--color-border);
  background: var(--gradient-button, var(--ct-bg-secondary));
  box-shadow: var(--effect-input-inner), 0 4px 14px rgba(22 163 74 / 0.12);
  /* GPU 合成层，避免重绘引起的卡顿 */
  transform: translateZ(0);
  backface-visibility: hidden;
}

.qp-cover.spinning {
  animation: qp-cover-spin 12s linear infinite;
}

@media (prefers-reduced-motion: reduce) {
  .qp-cover.spinning {
    /* reduced-motion 下用 JS rAF 驱动，更流畅 */
    animation: none;
  }
}

.qp-cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

/* ── 歌曲信息行：标题左 · 艺术家右 · 收藏 ── */
.qp-now-meta {
  width: 100%;
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.qp-now-title {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--ct-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.3;
  flex-shrink: 1;
  min-width: 0;
}
.qp-now-title.clickable {
  cursor: pointer;
  transition: color var(--transition-fast);
}
.qp-now-title.clickable:hover {
  color: var(--ct-accent);
}

.qp-now-artist {
  font-size: var(--text-xs);
  color: var(--ct-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-left: auto;
  flex-shrink: 0;
  max-width: 45%;
}
.qp-now-artist.clickable {
  cursor: pointer;
  transition: color var(--transition-fast);
}
.qp-now-artist.clickable:hover {
  color: var(--ct-accent);
}

.qp-now-fav {
  flex-shrink: 0;
  color: var(--ct-text-3) !important;
  transition: transform var(--transition-fast);
  width: 28px;
  height: 28px;
  border-radius: var(--radius-md) !important;
}
.qp-now-fav:hover {
  transform: scale(1.15);
  background: var(--gradient-button, var(--ct-bg-secondary)) !important;
}

/* ═══════════════════════════════════════════════════════════════
   队列分区
   ═══════════════════════════════════════════════════════════════ */
.qp-section {
  flex-shrink: 0;
}
.qp-section--grow {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.qp-section-label {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 10px 14px 6px;
  font-size: var(--text-2xs);
  font-weight: 600;
  color: var(--ct-text-3);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

/* ═══ 队列列表 ═══ */
.qp-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 6px 8px;
}
.qp-list::-webkit-scrollbar { width: 4px; }
.qp-list::-webkit-scrollbar-thumb {
  background: var(--color-scrollbar);
  border-radius: 2px;
}

/* ── 队列项：粘土小卡片 ── */
.qp-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  margin-bottom: 2px;
  border-radius: var(--radius-md);
  border: 2px solid transparent;
  background: var(--gradient-button, var(--ct-bg-secondary));
  box-shadow: var(--effect-button-inner), 1px 1px 3px rgba(22 163 74 / 0.04);
  cursor: pointer;
  transition: all var(--transition-fast);
}
.qp-item:hover {
  border-color: var(--color-border);
  background: var(--gradient-card);
  box-shadow: var(--effect-card-inner), var(--effect-card-outer);
  transform: translateY(-1px);
}
.qp-item:active {
  transform: scale(0.98);
  box-shadow: var(--effect-press);
}
.qp-item--past {
  opacity: 0.45;
}
.qp-item--past:hover {
  opacity: 0.75;
  transform: translateY(-1px);
}

/* ── 拖拽状态 ── */
.qp-item--dragging {
  opacity: 0.35;
  cursor: grabbing;
}
.qp-item--dragover {
  border-color: var(--ct-accent) !important;
  box-shadow: var(--effect-card-inner), 0 0 0 1px var(--ct-accent);
}
.qp-item-grip {
  cursor: grab;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity var(--transition-fast);
}
.qp-item:hover .qp-item-grip {
  opacity: 0.6;
}

.qp-item-idx {
  width: 18px;
  font-size: var(--text-2xs);
  color: var(--ct-text-3);
  text-align: center;
  flex-shrink: 0;
  font-feature-settings: 'tnum';
}

.qp-item-thumb-wrap {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  background: var(--gradient-input, var(--ct-bg));
  box-shadow: var(--effect-input-inner);
  overflow: hidden;
}
.qp-item-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.qp-item-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.qp-item-title {
  font-size: var(--text-xs);
  font-weight: 500;
  color: var(--ct-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.qp-item-artist {
  font-size: var(--text-2xs);
  color: var(--ct-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.qp-item-dur {
  font-size: var(--text-2xs);
  color: var(--ct-text-3);
  font-feature-settings: 'tnum';
  flex-shrink: 0;
}

.qp-item-actions {
  display: flex;
  gap: 1px;
  opacity: 0;
  transition: opacity var(--transition-fast);
}
.qp-item:hover .qp-item-actions {
  opacity: 1;
}
.qp-item-play,
.qp-item-remove {
  color: var(--ct-text-2) !important;
  width: 24px;
  height: 24px;
}
.qp-item-play:hover { color: var(--ct-accent) !important; }
.qp-item-remove:hover { color: var(--color-destructive) !important; }

/* ═══ 已播放折叠 ═══ */
.qp-history-toggle {
  width: 100%;
  border: none;
  background: none;
  cursor: pointer;
  font-family: inherit;
  color: var(--ct-text-3);
}
.qp-history-toggle:hover {
  color: var(--ct-text-2);
}
.qp-history-arrow {
  transition: transform var(--transition-fast);
}
.qp-history-arrow.open {
  transform: rotate(180deg);
}

/* ═══════════════════════════════════════════════════════════════
   Variant: immersive — 全屏暗色沉浸
   ═══════════════════════════════════════════════════════════════ */
.queue-panel.immersive {
  border: 1px solid rgba(255 255 255 / 0.06);
  background: rgba(0 0 0 / 0.25);
  box-shadow: none;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.immersive .qp-collapsed {
  color: rgba(255 255 255 / 0.5);
}
.immersive .qp-collapsed:hover {
  color: rgba(255 255 255 / 0.85);
}
.immersive .qp-collapsed-badge {
  color: rgba(255 255 255 / 0.7);
}

/* 头部 */
.immersive .qp-header {
  border-bottom-color: rgba(255 255 255 / 0.06);
}
.immersive .qp-header-title {
  color: rgba(255 255 255 / 0.85);
}
.immersive .qp-collapse-btn {
  color: rgba(255 255 255 / 0.45) !important;
}
.immersive .qp-collapse-btn:hover {
  color: rgba(255 255 255 / 0.8) !important;
}

/* 空状态 */
.immersive .qp-empty-text {
  color: rgba(255 255 255 / 0.5);
}
.immersive .qp-empty-hint {
  color: rgba(255 255 255 / 0.3);
}
.immersive .qp-empty-icon {
  background: rgba(255 255 255 / 0.06);
  box-shadow: none;
}

/* 正在播放卡片 */
.immersive .qp-now-card {
  border-color: rgba(255 255 255 / 0.08);
  background: rgba(255 255 255 / 0.05);
  box-shadow: none;
}
.immersive .qp-cover {
  border-color: rgba(255 255 255 / 0.1);
  background: rgba(255 255 255 / 0.04);
  box-shadow: none;
}
.immersive .qp-now-title {
  color: rgba(255 255 255 / 0.9);
}
.immersive .qp-now-artist {
  color: rgba(255 255 255 / 0.45);
}
.immersive .qp-now-fav {
  color: rgba(255 255 255 / 0.4) !important;
}

/* 队列分区标签 */
.immersive .qp-section-label {
  color: rgba(255 255 255 / 0.35);
}

/* 队列项 */
.immersive .qp-item {
  border-color: transparent;
  background: rgba(255 255 255 / 0.04);
  box-shadow: none;
}
.immersive .qp-item:hover {
  border-color: rgba(255 255 255 / 0.1);
  background: rgba(255 255 255 / 0.08);
  box-shadow: none;
}
.immersive .qp-item-title {
  color: rgba(255 255 255 / 0.8);
}
.immersive .qp-item-artist {
  color: rgba(255 255 255 / 0.35);
}
.immersive .qp-item-dur {
  color: rgba(255 255 255 / 0.3);
}
.immersive .qp-item-idx {
  color: rgba(255 255 255 / 0.3);
}
.immersive .qp-item--past {
  opacity: 0.3;
}
.immersive .qp-item--dragging {
  opacity: 0.2;
}
.immersive .qp-item--dragover {
  border-color: rgba(255 255 255 / 0.4) !important;
  box-shadow: 0 0 0 1px rgba(255 255 255 / 0.15);
}
.immersive .qp-item-play,
.immersive .qp-item-remove {
  color: rgba(255 255 255 / 0.4) !important;
}
.immersive .qp-item-play:hover {
  color: rgba(255 255 255 / 0.8) !important;
}
.immersive .qp-item-remove:hover {
  color: #EF4444 !important;
}

/* 已播放折叠按钮 */
.immersive .qp-history-toggle {
  color: rgba(255 255 255 / 0.35);
}
.immersive .qp-history-toggle:hover {
  color: rgba(255 255 255 / 0.6);
}

/* 折叠态 */
.immersive.collapsed {
  background: rgba(0 0 0 / 0.15);
}
</style>

<style>
/* 封面旋转 — 全局 keyframes，确保不被 scoped 干扰 */
@keyframes qp-cover-spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}
</style>
