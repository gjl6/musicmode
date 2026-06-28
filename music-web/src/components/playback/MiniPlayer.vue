<template>
  <div
    v-show="!player.isMinimized"
    class="playback-bar"
    :class="{ visible: player.hasTrack, 'is-hovering': isHovering }"
    @mouseenter="isHovering = true"
    @mouseleave="isHovering = false"
  >
    <!-- ── 顶部进度条（常驻）── -->
    <div class="bar-progress" @click="seekProgress">
      <div class="bar-progress-track">
        <div class="bar-progress-fill" :style="{ width: (player.progress * 100) + '%' }" />
      </div>
    </div>

    <!-- ═══ 视图堆叠：歌词 / 控制栏交叉淡入淡出 ═══ -->
    <div class="bar-view-stack">

      <!-- ── 歌词视图（歌词模式 + 未 hover）── -->
      <div
        class="bar-lyrics-view"
        :class="{ active: showLyrics && !isHovering && player.hasTrack }"
      >
        <!-- 加载中 -->
        <div v-if="player.lyricsLoading" class="bar-lyrics-status">
          <n-spin :size="14" />
          <span>加载中...</span>
        </div>

        <!-- 歌词 — 对角线错开，逐字变色 -->
        <div
          v-else-if="player.lyrics.length > 0"
          class="bar-lyrics-lines"
        >
          <!-- 左上 -->
          <span class="bar-lyric-line is-top-left" :class="topLeftClass">
            <span
              v-for="(char, ci) in topLeftChars"
              :key="ci"
              class="bar-lyric-char"
              :class="{ filled: isEvenIndex && ci < currentLineFilledCount }"
            >{{ char }}</span>
          </span>
          <!-- 右下 -->
          <span class="bar-lyric-line is-bottom-right" :class="bottomRightClass">
            <span
              v-for="(char, ci) in bottomRightChars"
              :key="ci"
              class="bar-lyric-char"
              :class="{ filled: !isEvenIndex && ci < currentLineFilledCount }"
            >{{ char }}</span>
          </span>
        </div>

        <!-- 暂无歌词 -->
        <span v-else class="bar-lyrics-none">暂无歌词</span>
      </div>

      <!-- ── 控制视图（默认模式 / 歌词模式 hover）── -->
      <div class="bar-body" :class="{ active: !showLyrics || isHovering }">
        <!-- 左侧：封面 + 曲目信息 -->
        <div class="bar-left">
          <div
            class="bar-cover-wrap"
            :class="{ clickable: player.current?.id }"
            title="打开播放详情页"
            @click="goFullscreen"
          >
            <img
              v-if="coverUrl"
              :src="coverUrl"
              class="bar-cover"
              alt="cover"
            />
            <n-icon v-else :size="22" color="var(--ct-text-3)"><MusicalNotesOutline /></n-icon>
          </div>
          <div class="bar-track">
            <n-tooltip trigger="hover" :delay="500">
              <template #trigger>
                <span
                  class="bar-track-title"
                  :class="{ clickable: player.current?.id }"
                  @click="goSongDetail"
                >{{ player.displayTitle }}</span>
              </template>
              {{ player.displayTitle }}
            </n-tooltip>
            <n-tooltip trigger="hover" :delay="500">
              <template #trigger>
                <span
                  class="bar-track-artist"
                  :class="{ clickable: player.current?.artistId }"
                  @click="goArtistDetail"
                >{{ player.displayArtist }}</span>
              </template>
              {{ player.displayArtist }}
            </n-tooltip>
          </div>
        </div>

        <!-- 中间：播放控制 -->
        <div class="bar-center">
          <n-button text class="bar-btn bar-btn--skip" @click="player.playPrev()">
            <template #icon><n-icon :size="20"><PlaySkipBackOutline /></n-icon></template>
          </n-button>

          <div class="bar-play-wrap" @click="player.togglePlay()">
            <n-icon :size="22">
              <PauseOutline v-if="player.isPlaying" />
              <PlayOutline v-else />
            </n-icon>
          </div>

          <n-button text class="bar-btn bar-btn--skip" @click="player.playNext()">
            <template #icon><n-icon :size="20"><PlaySkipForwardOutline /></n-icon></template>
          </n-button>
        </div>

        <!-- 右侧：功能区 -->
        <div class="bar-right">
          <!-- 音量 -->
          <div class="bar-vol-wrap" ref="volWrapRef">
            <n-button text class="bar-btn" @click.stop="volPopShow = !volPopShow">
              <template #icon>
                <n-icon :size="18">
                  <VolumeMuteOutline v-if="player.muted || player.volume === 0" />
                  <VolumeLowOutline v-else-if="player.volume < 0.33" />
                  <VolumeMediumOutline v-else-if="player.volume < 0.66" />
                  <VolumeHighOutline v-else />
                </n-icon>
              </template>
            </n-button>
            <div
              v-if="volPopShow"
              class="bar-vol-drop"
              @click.stop
              @mousedown.stop
            >
              <n-slider
                v-model:value="volValue"
                :min="0"
                :max="100"
                :step="1"
                :tooltip="false"
                vertical
                style="height: 110px"
              />
              <span class="bar-vol-num">{{ volValue }}</span>
            </div>
          </div>

          <!-- 收藏 -->
          <n-button text class="bar-btn" @click="player.toggleFavorite()">
            <template #icon>
              <n-icon :size="18" :color="player.isFavorited ? '#EF4444' : undefined">
                <Heart v-if="player.isFavorited" />
                <HeartOutline v-else />
              </n-icon>
            </template>
          </n-button>

          <!-- 播放模式切换 -->
          <n-button text class="bar-btn" @click="player.togglePlayMode()">
            <template #icon>
              <span class="bar-mode-icon" :class="'bar-mode--' + player.playMode">
                <n-icon :size="18">
                  <RepeatOutline v-if="player.playMode === 'normal'" />
                  <Repeat v-else-if="player.playMode === 'repeatAll'" />
                  <Repeat v-else-if="player.playMode === 'repeatOne'" />
                  <ShuffleOutline v-else />
                </n-icon>
                <span v-if="player.playMode === 'repeatOne'" class="bar-mode-badge">1</span>
              </span>
            </template>
          </n-button>

          <!-- 歌词模式切换 -->
          <n-button
            text
            class="bar-btn"
            :class="{ 'bar-btn--active': showLyrics }"
            @click="toggleLyrics"
          >
            <template #icon><n-icon :size="18"><DocumentTextOutline /></n-icon></template>
          </n-button>

          <!-- 缩小 -->
          <n-button text class="bar-btn" @click="player.isMinimized = true">
            <template #icon><n-icon :size="18"><ChevronBackOutline /></n-icon></template>
          </n-button>

          <!-- 关闭 -->
          <n-button text class="bar-btn bar-btn--close" @click="player.stop()">
            <template #icon><n-icon :size="18"><CloseOutline /></n-icon></template>
          </n-button>
        </div>
      </div><!-- /bar-body -->

    </div><!-- /bar-view-stack -->
  </div>
</template>

<script setup>
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  MusicalNotesOutline,
  PlayOutline, PauseOutline,
  PlaySkipBackOutline, PlaySkipForwardOutline,
  VolumeHighOutline, VolumeMediumOutline, VolumeLowOutline, VolumeMuteOutline,
  HeartOutline, Heart,
  RepeatOutline, Repeat,
  ShuffleOutline,
  ChevronBackOutline,
  CloseOutline,
  DocumentTextOutline,
} from '@vicons/ionicons5'
import { usePlayerStore } from '@/store/playback/player.js'
import { subsonicGetCoverArtUrl } from '@/api/playback/subsonic.js'

const router = useRouter()
const player = usePlayerStore()

// ── Hover 状态 ──
const isHovering = ref(false)

// 停止播放时自动恢复完整模式、关闭歌词
watch(() => player.hasTrack, (v) => {
  if (!v) {
    player.isMinimized = false
    showLyrics.value = false
  }
})

// ── 音量弹窗控制 ──
const volPopShow = ref(false)
const volWrapRef = ref(null)

const volValue = ref(Math.round(player.volume * 100))

watch(volValue, (v) => {
  player.setVolume(v / 100)
})

watch(() => player.muted, (m) => {
  volValue.value = m ? 0 : Math.round(player.volume * 100)
})
watch(() => player.volume, (v) => {
  if (!player.muted) volValue.value = Math.round(v * 100)
})

function onDocClick(e) {
  if (!volPopShow.value) return
  const wrap = volWrapRef.value
  if (!wrap) return
  if (!wrap.contains(e.target)) {
    volPopShow.value = false
  }
}
onMounted(() => document.addEventListener('mousedown', onDocClick))
onUnmounted(() => {
  document.removeEventListener('mousedown', onDocClick)
  stopSmoothTimer()
})

// ── 歌词模式 ──
const showLyrics = ref(false)

function toggleLyrics() {
  showLyrics.value = !showLyrics.value
  if (showLyrics.value) {
    isHovering.value = false
  }
}

function goSongDetail() {
  const id = player.current?.id
  if (id) router.push(`/player/songs/${id}`)
}

function goArtistDetail() {
  const id = player.current?.artistId
  if (id) router.push(`/player/artists/${id}`)
}

function goFullscreen() {
  router.push('/player/fullscreen')
}

// ── rAF 平滑时间驱动（60fps 插值，消除 4Hz timeupdate 卡顿）──
const smoothTime = ref(0)

let _lastTuTime = 0       // 最近一次 timeupdate 的 player.currentTime
let _lastTuStamp = 0      // 最近一次 timeupdate 的 performance.now()
let _smoothRaf = null

function startSmoothTimer() {
  if (_smoothRaf) return
  const tick = () => {
    if (_lastTuStamp > 0) {
      const elapsed = (performance.now() - _lastTuStamp) / 1000
      smoothTime.value = Math.min(_lastTuTime + elapsed, player.duration || Infinity)
    }
    _smoothRaf = requestAnimationFrame(tick)
  }
  _smoothRaf = requestAnimationFrame(tick)
}

function stopSmoothTimer() {
  if (_smoothRaf) {
    cancelAnimationFrame(_smoothRaf)
    _smoothRaf = null
  }
}

// 播放/暂停 → 启动/停止 rAF
watch(() => player.isPlaying, (v) => {
  if (v) {
    _lastTuTime = player.currentTime
    _lastTuStamp = performance.now()
    smoothTime.value = _lastTuTime
    startSmoothTimer()
  } else {
    stopSmoothTimer()
  }
})

// timeupdate → 校准锚点
watch(() => player.currentTime, (t) => {
  _lastTuTime = t
  _lastTuStamp = performance.now()
})

// 停止播放时重置
watch(() => player.hasTrack, (v) => {
  if (!v) {
    stopSmoothTimer()
    _lastTuTime = 0
    _lastTuStamp = 0
    smoothTime.value = 0
  }
})

/** 同步歌词：当前行 */
const currentLyricLine = computed(() => {
  const idx = player.currentLyricIndex
  if (idx >= 0 && idx < player.lyrics.length) return player.lyrics[idx]
  return player.lyrics.length > 0 ? player.lyrics[0] : null
})

/** 同步歌词：下一行 */
const nextLyricLine = computed(() => {
  const idx = player.currentLyricIndex
  if (idx >= 0 && idx < player.lyrics.length - 1) return player.lyrics[idx + 1]
  return null
})

/** 纯文本歌词：按播放进度百分比映射到对应两行 */
const plainLyricLines = computed(() => {
  const lines = player.lyrics
  if (lines.length === 0) return []
  const pct = player.progress || 0
  const idx = Math.min(Math.floor(pct * lines.length), lines.length - 1)
  const next = Math.min(idx + 1, lines.length - 1)
  return [lines[idx], lines[next]]
})

/** 统一：当前行文本 */
const displayCurrentLine = computed(() => {
  if (player.lyricsSynced) return currentLyricLine.value?.value || ''
  return plainLyricLines.value[0]?.value || ''
})

/** 统一：后一行文本 */
const displayNextLine = computed(() => {
  if (player.lyricsSynced) return nextLyricLine.value?.value || ''
  return plainLyricLines.value[1]?.value || ''
})

// ── 奇偶交替布局 ──
const isEvenIndex = computed(() => player.currentLyricIndex % 2 === 0)

const topLeftChars = computed(() =>
  (isEvenIndex.value ? displayCurrentLine.value : displayNextLine.value).split('')
)
const bottomRightChars = computed(() =>
  (isEvenIndex.value ? displayNextLine.value : displayCurrentLine.value).split('')
)

const topLeftClass = computed(() => isEvenIndex.value ? 'is-active' : 'is-preview')
const bottomRightClass = computed(() => isEvenIndex.value ? 'is-preview' : 'is-active')

// ── 逐字填色：当前行已填色字数 ──
const currentLineFilledCount = computed(() => {
  const current = currentLyricLine.value
  const next = nextLyricLine.value
  const text = current?.value || ''
  if (!text) return 0

  if (player.lyricsSynced && current?.start != null) {
    const lineStart = current.start
    const lineEnd = next?.start != null ? next.start : lineStart + 5000
    const elapsed = Math.max(0, smoothTime.value * 1000 - lineStart)
    const progress = Math.min(elapsed / Math.max(1, lineEnd - lineStart), 1)
    return Math.floor(progress * text.length)
  }

  // 纯文本：按歌曲总进度模拟
  const idx = player.currentLyricIndex
  const total = player.lyrics.length
  if (total === 0) return 0
  const lineProgress = Math.max(0, Math.min(1,
    ((player.progress || 0) - idx / total) / (1 / total)
  ))
  return Math.floor(lineProgress * text.length)
})

// ── 封面 URL ──
const coverUrl = computed(() => {
  const artId = player.current?.coverArt || player.current?.albumId
  return artId ? subsonicGetCoverArtUrl(artId, 120) : null
})

// ── 进度条 seek ──
function seekProgress(e) {
  const bar = e.currentTarget
  const rect = bar.getBoundingClientRect()
  const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
  player.seek(ratio * player.duration)
}
</script>

<style scoped>
/* ═══════════════════════════════════════════════════════════════
   播放控制栏 — Claymorphism 粘土风格
   双层视图：歌词 / 控制栏交叉淡入淡出
   ═══════════════════════════════════════════════════════════════ */

.playback-bar {
  position: fixed;
  bottom: 12px;
  left: calc(200px + (100vw - 200px - var(--queue-panel-width, 0px)) / 2);
  width: calc(100vw - 240px - var(--queue-panel-width, 0px));
  max-width: 820px;
  z-index: 50;
  display: flex;
  flex-direction: column;
  border-radius: var(--radius-xl);
  border: var(--border-width-strong, 3px) solid var(--color-border);
  background: var(--gradient-card, var(--ct-card-bg));
  box-shadow:
    var(--effect-card-inner),
    var(--effect-card-outer, 0 -4px 20px rgba(0 0 0 / 0.1));
  transform: translateX(-50%) translateY(calc(100% + 12px));
  transition: transform var(--transition-player);
}
.playback-bar.visible {
  transform: translateX(-50%) translateY(0);
}

/* ── 进度条（常驻）── */
.bar-progress {
  cursor: pointer;
  padding: 0 16px;
  flex-shrink: 0;
}
.bar-progress-track {
  height: 5px;
  border-radius: 3px;
  background: var(--ct-border);
  overflow: hidden;
  transition: height 0.15s ease;
}
.bar-progress:hover .bar-progress-track {
  height: 7px;
}
.bar-progress-fill {
  height: 100%;
  border-radius: 3px;
  background: var(--gradient-button-primary, var(--ct-accent));
  box-shadow: 0 0 8px rgb(var(--ct-accent-rgb) / 0.5);
  transition: width 0.15s linear;
}

/* ═══════ 视图堆叠容器 ═══════ */
.bar-view-stack {
  position: relative;
  height: 72px;
  flex-shrink: 0;
}

/* ═══════ 歌词视图 ═══════ */
.bar-lyrics-view {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 20px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.3s ease;
}
.bar-lyrics-view.active {
  opacity: 1;
  pointer-events: auto;
}

/* ── 对角线布局 ── */
.bar-lyrics-lines {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  width: 100%;
  height: 100%;
  padding: 4px 0;
}

.bar-lyric-line {
  overflow: hidden;
  white-space: nowrap;
  line-height: 1.5;
  max-width: 100%;
}

/* 左上 */
.bar-lyric-line.is-top-left {
  align-self: flex-start;
  text-align: left;
  padding-right: 40px;
}

/* 右下 — 首字对齐中间线 */
.bar-lyric-line.is-bottom-right {
  margin-left: 50%;
  text-align: left;
  max-width: 50%;
}

/* ── 逐字 ── */
.bar-lyric-char {
  font-size: 20px;
  transition: color 0.25s ease, font-weight 0.25s ease;
  letter-spacing: 1px;
}

/* 当前行（填色中）*/
.bar-lyric-line.is-active .bar-lyric-char {
  color: var(--ct-text-3);
}
.bar-lyric-line.is-active .bar-lyric-char.filled {
  color: var(--ct-accent);
  font-weight: 700;
}

/* 预览行（静态淡色）*/
.bar-lyric-line.is-preview .bar-lyric-char {
  color: var(--ct-text-3);
  opacity: 0.45;
}

/* 加载 / 空状态 */
.bar-lyrics-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-xs);
  color: var(--ct-text-3);
}
.bar-lyrics-none {
  font-size: var(--text-sm);
  color: var(--ct-text-3);
}

/* ═══════ 控制视图（bar-body）═══════ */
.bar-body {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 0 28px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.3s ease;
}
.bar-body.active {
  opacity: 1;
  pointer-events: auto;
}

/* ═══════ 左侧：封面 + 信息 ═══════ */
.bar-left {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 0 0 260px;
  min-width: 0;
}

.bar-cover-wrap {
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  border: var(--border-width-strong, 3px) solid var(--color-border);
  background: var(--gradient-button, var(--ct-bg-secondary));
  box-shadow: var(--effect-input-inner);
  overflow: hidden;
}
.bar-cover-wrap.clickable {
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}
.bar-cover-wrap.clickable:hover {
  transform: scale(1.08);
  box-shadow: var(--effect-button-inner), var(--effect-button-outer);
}
.bar-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.bar-track {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 3px;
}
.bar-track-title {
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--ct-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bar-track-title.clickable {
  cursor: pointer;
}
.bar-track-title.clickable:hover {
  color: var(--ct-accent);
}
.bar-track-artist {
  font-size: var(--text-xs);
  color: var(--ct-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bar-track-artist.clickable {
  cursor: pointer;
}
.bar-track-artist.clickable:hover {
  color: var(--ct-accent);
}

/* ═══════ 中间：播放控制 ═══════ */
.bar-center {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  min-width: 0;
}

/* 粘土按钮基础样式 */
.bar-btn {
  color: var(--ct-text-2) !important;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md) !important;
  transition: color var(--transition-fast), transform var(--transition-fast), box-shadow var(--transition-fast);
}
.bar-btn:hover {
  color: var(--ct-text) !important;
  background: var(--gradient-button, var(--ct-bg-secondary)) !important;
  box-shadow: var(--effect-button-inner), var(--effect-button-outer);
}
.bar-btn:active {
  transform: var(--scale-button-active);
  box-shadow: var(--effect-press) !important;
}

.bar-btn--skip {
  color: var(--ct-text-2) !important;
  width: 38px;
  height: 38px;
}
.bar-btn--skip:hover {
  color: var(--ct-accent) !important;
}

/* 歌词按钮激活态 */
.bar-btn--active {
  color: var(--ct-accent) !important;
  background: var(--gradient-button, var(--ct-bg-secondary)) !important;
  box-shadow: var(--effect-button-inner), var(--effect-button-outer);
}

/* 播放键 — 粘土凸起圆形 */
.bar-play-wrap {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--gradient-button-primary, var(--ct-accent));
  color: #fff;
  box-shadow:
    inset 2px 2px 5px rgba(255 255 255 / 0.35),
    inset -3px -3px 6px rgba(0 0 0 / 0.15),
    0 4px 12px rgb(var(--ct-accent-rgb) / 0.3),
    0 8px 24px rgb(var(--ct-accent-rgb) / 0.15);
  cursor: pointer;
  transition: transform var(--transition-button-active, 0.2s cubic-bezier(0.34, 1.56, 0.64, 1)),
              box-shadow var(--transition-fast);
  user-select: none;
}
.bar-play-wrap:hover {
  transform: translateY(-3px);
  box-shadow:
    inset 2px 2px 5px rgba(255 255 255 / 0.4),
    inset -3px -3px 6px rgba(0 0 0 / 0.12),
    0 6px 20px rgb(var(--ct-accent-rgb) / 0.4),
    0 12px 32px rgb(var(--ct-accent-rgb) / 0.2);
}
.bar-play-wrap:active {
  transform: scale(0.92);
  box-shadow:
    inset 3px 3px 8px rgba(0 0 0 / 0.2),
    inset -1px -1px 3px rgba(255 255 255 / 0.2) !important;
}

/* ═══════ 右侧：功能区 ═══════ */
.bar-right {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  margin-right: 4px;
}

/* 音量按钮 + 下拉面板 */
.bar-vol-wrap {
  position: relative;
}
.bar-vol-drop {
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  margin-bottom: 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 14px 12px;
  border-radius: var(--radius-lg);
  background: var(--gradient-card, var(--ct-card-bg));
  border: var(--border-width-strong, 3px) solid var(--color-border);
  box-shadow:
    var(--effect-card-inner),
    var(--effect-card-outer, var(--shadow-lg));
}
.bar-vol-drop :deep(.n-slider) {
  --n-fill-color: var(--ct-accent);
  --n-fill-color-hover: var(--ct-accent);
  --n-rail-color: var(--ct-border);
}
.bar-vol-num {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--ct-text);
  font-feature-settings: 'tnum';
  min-width: 24px;
  text-align: center;
}

/* 播放模式图标 + badge */
.bar-mode-icon {
  position: relative;
  display: inline-flex;
}
.bar-mode--normal {
  color: var(--ct-text-3);
}
.bar-mode--repeatAll,
.bar-mode--repeatOne,
.bar-mode--shuffle {
  color: var(--ct-accent);
}
.bar-mode-badge {
  position: absolute;
  top: -2px;
  right: -4px;
  font-size: 8px;
  font-weight: 700;
  line-height: 1;
  color: var(--ct-accent);
}

/* 关闭按钮 hover 显红 */
.bar-btn--close:hover {
  color: var(--color-destructive) !important;
  background: rgba(var(--color-destructive-rgb) / 0.08) !important;
}
</style>
