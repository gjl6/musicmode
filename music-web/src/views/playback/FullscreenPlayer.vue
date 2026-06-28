<template>
  <div class="fullscreen-player">
    <!-- ═══ 背景层 ═══ -->
    <div class="fp-bg-layer">
      <div
        class="fp-bg-cover"
        :class="{ 'fp-bg-cover--visible': coverUrl }"
        :style="{ backgroundImage: coverUrl ? `url(${coverUrl})` : undefined }"
      />
    </div>

    <!-- ═══ 空状态 ═══ -->
    <div v-if="!player.hasTrack" class="fp-empty">
      <div class="fp-empty-card">
        <n-icon :size="48" color="var(--ct-text-3)"><MusicalNotesOutline /></n-icon>
        <p>{{ t('fullscreen.notPlaying') }}</p>
        <n-button size="small" round @click="goBack">
          {{ t('fullscreen.backToLibrary') }}
        </n-button>
      </div>
    </div>

    <!-- ═══ 主布局：全视口 Flex 行（主体 + 侧边队列）═══ -->
    <Transition name="fp-fade">
      <div v-if="player.hasTrack" class="fp-main-area">
        <div class="fp-layout">
        <!-- ── 顶栏 ── -->
        <div class="fp-top">
          <n-button text class="fp-back-btn" @click.stop="goBack">
            <template #icon><n-icon :size="20"><ChevronBackOutline /></n-icon></template>
          </n-button>
          <div class="fp-top-info">
            <span class="fp-top-title">{{ player.displayTitle }}</span>
            <span class="fp-top-artist">{{ player.displayArtist }}</span>
          </div>
          <div class="fp-top-actions">
            <n-button text class="fp-top-btn" @click.stop="$router.push('/player/search')" :title="$t('player.search')">
              <template #icon><n-icon :size="18"><SearchOutline /></n-icon></template>
            </n-button>
            <n-button text class="fp-top-btn" @click.stop="toggleNativeFullscreen">
              <template #icon>
                <n-icon :size="18">
                  <ContractOutline v-if="isNativeFullscreen" />
                  <ExpandOutline v-else />
                </n-icon>
              </template>
            </n-button>
            <n-button text class="fp-top-btn" @click.stop="goToSong">
              <template #icon><n-icon :size="18"><InformationCircleOutline /></n-icon></template>
            </n-button>
          </div>
        </div>

        <!-- ── 主体：左列(封面+进度+控制) / 右列(歌词) ── -->
        <div class="fp-body">
          <!-- 左列 -->
          <div class="fp-left">
            <div class="fp-cover-section">
              <div
                class="fp-cover-frame"
                :class="{ 'fp-cover-frame--spinning': player.isPlaying }"
              >
                <div class="fp-cover-inner">
                  <img
                    v-if="coverUrl"
                    :src="coverUrl"
                    class="fp-cover-img"
                    alt="cover"
                  />
                  <n-icon v-else :size="48" color="var(--ct-text-3)">
                    <MusicalNotesOutline />
                  </n-icon>
                </div>
                <div v-if="coverUrl" class="fp-vinyl-center" />
              </div>
              <div class="fp-cover-meta">
                <span v-if="player.current?.bitRate" class="fp-meta-tag">
                  {{ player.current.bitRate }} kbps
                </span>
                <span v-if="player.current?.suffix" class="fp-meta-tag">
                  {{ player.current.suffix.toUpperCase() }}
                </span>
                <span v-if="player.current?.size" class="fp-meta-tag">
                  {{ formatSize(player.current.size) }}
                </span>
              </div>
            </div>

            <!-- 进度条 -->
            <div class="fp-progress" @click.stop="seekProgress">
              <div class="fp-progress-track">
                <div
                  class="fp-progress-fill"
                  :style="{ width: (player.progress * 100) + '%' }"
                />
                <div
                  class="fp-progress-thumb"
                  :class="{ 'fp-progress-thumb--visible': hoveringProgress }"
                  :style="{ left: (player.progress * 100) + '%' }"
                />
              </div>
              <div class="fp-time-row">
                <span class="fp-time">{{ formatTime(smoothTime) }}</span>
                <span class="fp-time">{{ formatTime(player.duration) }}</span>
              </div>
            </div>

            <!-- 控制栏 -->
            <div class="fp-controls">
              <div class="fp-ctls-side">
                <n-button text class="fp-ctl-btn" @click.stop="player.togglePlayMode()">
                  <template #icon>
                    <span class="fp-mode-wrap" :class="'fp-mode--' + player.playMode">
                      <n-icon :size="16">
                        <RepeatOutline v-if="player.playMode === 'normal'" />
                        <Repeat v-else-if="player.playMode === 'repeatAll' || player.playMode === 'repeatOne'" />
                        <ShuffleOutline v-else />
                      </n-icon>
                      <span v-if="player.playMode === 'repeatOne'" class="fp-mode-badge">1</span>
                    </span>
                  </template>
                </n-button>
                <div class="fp-vol-wrap" ref="volWrapRef">
                  <n-button text class="fp-ctl-btn" @click.stop="volPopShow = !volPopShow">
                    <template #icon>
                      <n-icon :size="16">
                        <VolumeMuteOutline v-if="player.muted || player.volume === 0" />
                        <VolumeLowOutline v-else-if="player.volume < 0.33" />
                        <VolumeMediumOutline v-else-if="player.volume < 0.66" />
                        <VolumeHighOutline v-else />
                      </n-icon>
                    </template>
                  </n-button>
                  <Transition name="fp-fade">
                    <div v-show="volPopShow" class="fp-vol-drop" @click.stop>
                      <n-slider
                        v-model:value="volValue"
                        :min="0"
                        :max="100"
                        :step="1"
                        :tooltip="false"
                        vertical
                        style="height:120px"
                      />
                      <span class="fp-vol-num">{{ volValue }}</span>
                    </div>
                  </Transition>
                </div>
              </div>

              <div class="fp-ctls-center">
                <n-button text class="fp-ctl-btn fp-skip-btn" @click.stop="player.playPrev()" :disabled="!player.hasPrev">
                  <template #icon><n-icon :size="22"><PlaySkipBackOutline /></n-icon></template>
                </n-button>

                <div class="fp-play-btn" @click.stop="player.togglePlay()">
                  <n-icon :size="26">
                    <PauseOutline v-if="player.isPlaying" />
                    <PlayOutline v-else />
                  </n-icon>
                </div>

                <n-button text class="fp-ctl-btn fp-skip-btn" @click.stop="player.playNext()" :disabled="!player.hasNext">
                  <template #icon><n-icon :size="22"><PlaySkipForwardOutline /></n-icon></template>
                </n-button>
              </div>

              <div class="fp-ctls-side fp-ctls-right">
                <n-button text class="fp-ctl-btn" @click.stop="player.toggleFavorite()">
                  <template #icon>
                    <n-icon :size="16" :color="player.isFavorited ? 'var(--color-destructive)' : undefined">
                      <Heart v-if="player.isFavorited" />
                      <HeartOutline v-else />
                    </n-icon>
                  </template>
                </n-button>
              </div>
            </div>
          </div>

          <!-- 右列：歌词 -->
          <div class="fp-lyrics-section">
            <div v-if="player.lyricsLoading" class="fp-lyrics-status">
              <n-spin :size="18" />
              <span>{{ t('fullscreen.loading') }}</span>
            </div>

            <div
              v-else-if="player.lyricsSynced && player.lyrics.length"
              ref="lyricsScrollRef"
              class="fp-lyrics-scroll"
            >
              <div class="fp-lyrics-inner">
                <div
                  v-for="(line, i) in player.lyrics"
                  :key="i"
                  class="fp-lyric-line"
                  :class="{
                    'fp-lyric-line--active': i === player.currentLyricIndex,
                    'fp-lyric-line--past': i < player.currentLyricIndex,
                  }"
                  @click="seekLyric(line.start)"
                >{{ line.value }}</div>
              </div>
            </div>

            <div v-else-if="player.lyrics.length" class="fp-lyrics-plain">
              <div
                v-for="(line, i) in player.lyrics"
                :key="i"
                class="fp-lyric-line fp-lyric-line--static"
              >{{ line.value }}</div>
            </div>

            <div v-else class="fp-lyrics-status">
              <n-icon :size="24" color="var(--ct-text-3)"><MusicalNotesOutline /></n-icon>
              <span>{{ t('fullscreen.noLyrics') }}</span>
            </div>
          </div>
        </div>
      </div>
      <QueuePanel class="fp-queue-sidebar" variant="immersive" />
    </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  MusicalNotesOutline,
  PlayOutline, PauseOutline,
  PlaySkipBackOutline, PlaySkipForwardOutline,
  VolumeHighOutline, VolumeMediumOutline, VolumeLowOutline, VolumeMuteOutline,
  HeartOutline, Heart,
  RepeatOutline, Repeat,
  ShuffleOutline,
  ChevronBackOutline,
  InformationCircleOutline,
  ExpandOutline, ContractOutline,
  SearchOutline,
} from '@vicons/ionicons5'
import { usePlayerStore } from '@/store/playback/player.js'
import { subsonicGetCoverArtUrl } from '@/api/playback/subsonic.js'
import QueuePanel from '@/components/playback/QueuePanel.vue'

const router = useRouter()
const player = usePlayerStore()
const { t } = useI18n()

// ═══ 浏览器全屏 ═══

const isNativeFullscreen = ref(false)

function onFullscreenChange() {
  isNativeFullscreen.value = !!document.fullscreenElement
}

async function toggleNativeFullscreen() {
  try {
    if (!document.fullscreenElement) {
      await document.documentElement.requestFullscreen()
    } else {
      await document.exitFullscreen()
    }
  } catch (e) {
    console.warn('[FullscreenPlayer] 全屏切换失败:', e)
  }
}

// ═══ 键盘快捷键 ═══

function onKeyDown(e) {
  if (!player.hasTrack) return
  if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.isContentEditable) return

  switch (e.code) {
    case 'Space':
      e.preventDefault()
      player.togglePlay()
      break
    case 'ArrowLeft':
      e.preventDefault()
      if (e.ctrlKey) player.seek(player.currentTime - 5)
      else player.playPrev()
      break
    case 'ArrowRight':
      e.preventDefault()
      if (e.ctrlKey) player.seek(player.currentTime + 5)
      else player.playNext()
      break
    case 'ArrowUp':
      e.preventDefault()
      player.setVolume(Math.min(1, player.volume + 0.05))
      volValue.value = Math.round(player.volume * 100)
      break
    case 'ArrowDown':
      e.preventDefault()
      player.setVolume(Math.max(0, player.volume - 0.05))
      volValue.value = Math.round(player.volume * 100)
      break
    case 'Escape':
      goBack()
      break
    case 'KeyM':
      player.toggleMute()
      break
    case 'KeyL':
      player.toggleFavorite()
      break
    case 'KeyF':
      e.preventDefault()
      toggleNativeFullscreen()
      break
  }
}

onMounted(() => {
  document.addEventListener('keydown', onKeyDown)
  document.addEventListener('mousedown', onDocClick)
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKeyDown)
  document.removeEventListener('mousedown', onDocClick)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  stopSmooth()
})

// ═══ rAF 平滑时间 ═══

const smoothTime = ref(0)
let _lastTuTime = 0
let _lastTuStamp = 0
let _smoothRaf = null

function startSmooth() {
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

function stopSmooth() {
  if (_smoothRaf) { cancelAnimationFrame(_smoothRaf); _smoothRaf = null }
}

watch(() => player.isPlaying, (v) => {
  if (v) {
    _lastTuTime = player.currentTime
    _lastTuStamp = performance.now()
    smoothTime.value = _lastTuTime
    startSmooth()
  } else {
    stopSmooth()
  }
})

watch(() => player.currentTime, (t) => {
  _lastTuTime = t; _lastTuStamp = performance.now()
})

watch(() => player.hasTrack, (v) => {
  if (!v) { stopSmooth(); _lastTuTime = 0; _lastTuStamp = 0; smoothTime.value = 0 }
})

// ═══ 封面 ═══

const coverUrl = computed(() => {
  const artId = player.current?.coverArt || player.current?.albumId
  return artId ? subsonicGetCoverArtUrl(artId, 600) : null
})

// ═══ 歌词自动滚动 ═══

const lyricsScrollRef = ref(null)

watch(() => player.currentLyricIndex, (idx) => {
  if (!lyricsScrollRef.value || idx < 0) return
  nextTick(() => {
    const el = lyricsScrollRef.value.querySelector('.fp-lyric-line--active')
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
})

function seekLyric(startMs) {
  if (startMs != null && startMs > 0) player.seek(startMs / 1000)
}

// ═══ 进度条 ═══

const hoveringProgress = ref(false)

function seekProgress(e) {
  const bar = e.currentTarget
  const rect = bar.getBoundingClientRect()
  const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
  const newTime = ratio * player.duration
  player.seek(newTime)
  _lastTuTime = newTime
  _lastTuStamp = performance.now()
  smoothTime.value = newTime
}

// ═══ 音量 ═══

const volPopShow = ref(false)
const volWrapRef = ref(null)
const volValue = ref(Math.round(player.volume * 100))

watch(volValue, (v) => { player.setVolume(v / 100) })
watch(() => player.muted, (m) => { volValue.value = m ? 0 : Math.round(player.volume * 100) })
watch(() => player.volume, (v) => { if (!player.muted) volValue.value = Math.round(v * 100) })

function onDocClick(e) {
  if (!volPopShow.value) return
  if (!volWrapRef.value?.contains(e.target)) volPopShow.value = false
}

// ═══ 工具 ═══

function formatTime(sec) {
  if (!sec || !isFinite(sec) || sec <= 0) return '0:00'
  const s = Math.floor(sec)
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

function formatSize(bytes) {
  if (!bytes) return ''
  if (bytes < 1048576) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / 1048576).toFixed(1)} MB`
}

function goBack() { router.back() }

function goToSong() {
  const song = player.current
  if (song?.id) router.push(`/player/songs/${song.id}`)
}
</script>

<style scoped>
/* ═══════════════════════════════════════════════════════
   全屏播放器 — 全视口粘土沉浸布局
   ═══════════════════════════════════════════════════════ */

.fullscreen-player {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gradient-page);
  color: var(--ct-text);
  user-select: none;
  overflow: hidden;
}

/* ═══ 背景模糊封面 ═══ */
.fp-bg-layer {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

.fp-bg-cover {
  position: absolute;
  inset: -80px;
  background-size: cover;
  background-position: center;
  filter: blur(100px) brightness(0.18);
  opacity: 0;
  transition: opacity 1.5s ease;
}

.fp-bg-cover--visible {
  opacity: 1;
  animation: fpBgZoom 50s ease-in-out alternate infinite;
}

@keyframes fpBgZoom {
  from { transform: scale(1.05); }
  to { transform: scale(1.2); }
}

/* ═══ 过渡 ═══ */
.fp-fade-enter-active,
.fp-fade-leave-active { transition: opacity 0.35s ease; }
.fp-fade-enter-from,
.fp-fade-leave-to { opacity: 0; }

/* ═══════════════════════════════════════════════════════
   主布局 — 全视口 Flex 行（主体 + 队列侧边栏）
   ═══════════════════════════════════════════════════════ */

.fp-main-area {
  position: relative;
  z-index: 5;
  width: 100%;
  height: 100%;
  display: flex;
  overflow: hidden;
}

.fp-layout {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 0 48px;
  overflow: hidden;
}

.fp-queue-sidebar {
  flex-shrink: 0;
  height: 100%;
}

/* ── 顶栏 ── */
.fp-top {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 0 12px;
  flex-shrink: 0;
}

.fp-back-btn {
  color: var(--ct-text-2) !important;
  width: 36px; height: 36px;
  flex-shrink: 0;
  border-radius: var(--radius-sm) !important;
  transition: color var(--transition-fast), background var(--transition-fast);
}
.fp-back-btn:hover { color: var(--ct-text) !important; background: var(--ct-bg-secondary) !important; }

.fp-top-info {
  flex: 0 1 auto;
  min-width: 0;
  max-width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  border-radius: var(--radius-md);
  background: var(--gradient-input);
  box-shadow: var(--effect-input-inner);
  border: var(--border-width-default) solid var(--ct-border);
}
.fp-top-title {
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--ct-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fp-top-artist {
  font-size: var(--text-xs);
  color: var(--ct-text-2);
  flex-shrink: 0;
}
.fp-top-artist::before {
  content: '·';
  margin-right: 8px;
  color: var(--ct-text-3);
}

.fp-top-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  margin-left: auto;
}
.fp-top-btn {
  color: var(--ct-text-2) !important;
  width: 34px; height: 34px;
  border-radius: var(--radius-sm) !important;
  transition: color var(--transition-fast), background var(--transition-fast);
}
.fp-top-btn:hover { color: var(--ct-text) !important; background: var(--ct-bg-secondary) !important; }

/* ── 主体：左列 / 右列 ── */
.fp-body {
  flex: 1;
  display: flex;
  gap: clamp(24px, 4vw, 56px);
  min-height: 0;
  padding: 4px 0;
}

/* ═══ 左列：封面 + 进度条 + 控制 ═══ */
.fp-left {
  flex-shrink: 0;
  width: min(clamp(280px, 36vw, 420px), 55vh);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
}

/* ═══ 封面 ═══ */
.fp-cover-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding-top: 4px;
}

.fp-cover-frame {
  width: 100%;
  aspect-ratio: 1;
  border: var(--border-width-strong) solid var(--ct-border);
  border-radius: 50%;
  background: var(--gradient-card);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
  position: relative;
}

.fp-cover-inner {
  width: 100%; height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  overflow: hidden;
}

.fp-cover-img {
  width: 100%; height: 100%;
  object-fit: cover;
}

.fp-vinyl-center {
  position: absolute;
  top: 50%; left: 50%;
  width: 48px; height: 48px;
  margin: -24px 0 0 -24px;
  border-radius: 50%;
  background: var(--gradient-card);
  border: var(--border-width-default) solid var(--ct-border);
  box-shadow: var(--shadow-sm);
}
.fp-vinyl-center::after {
  content: '';
  position: absolute;
  top: 50%; left: 50%;
  width: 10px; height: 10px;
  margin: -5px 0 0 -5px;
  border-radius: 50%;
  background: var(--ct-text-3);
}

.fp-cover-frame--spinning .fp-cover-inner {
  animation: fpVinylSpin 20s linear infinite;
}

@keyframes fpVinylSpin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.fp-cover-meta {
  display: flex; flex-wrap: wrap; gap: 4px;
  justify-content: center;
}
.fp-meta-tag {
  padding: 2px 8px;
  font-size: var(--text-2xs);
  color: var(--ct-text-3);
  background: var(--ct-bg-secondary);
  border-radius: var(--radius-sm);
}

/* ═══ 右列：歌词 ═══ */
.fp-lyrics-section {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.fp-lyrics-status {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--ct-text-3);
  font-size: var(--text-sm);
}

.fp-lyrics-scroll {
  flex: 1;
  overflow-y: auto;
  mask-image: linear-gradient(to bottom, transparent 0%, black 8%, black 92%, transparent 100%);
  -webkit-mask-image: linear-gradient(to bottom, transparent 0%, black 8%, black 92%, transparent 100%);
}
.fp-lyrics-scroll::-webkit-scrollbar { width: 0; }

.fp-lyrics-inner {
  display: flex;
  flex-direction: column;
  padding: 60px 0;
}

.fp-lyrics-plain {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}
.fp-lyrics-plain::-webkit-scrollbar { width: 4px; }
.fp-lyrics-plain::-webkit-scrollbar-thumb { background: var(--color-scrollbar); border-radius: 2px; }

.fp-lyric-line {
  padding: 6px 24px;
  font-size: clamp(var(--text-md), 2vw, var(--text-lg));
  line-height: 1.8;
  color: var(--ct-text-3);
  text-align: center;
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
}
.fp-lyric-line:hover { color: var(--ct-text-2); }
.fp-lyric-line--past { color: var(--ct-text-2); }
.fp-lyric-line--active {
  color: var(--ct-accent);
  font-size: clamp(var(--text-lg), 2.2vw, calc(var(--text-lg) + 3px));
  font-weight: 700;
  padding: 8px 24px;
}
.fp-lyric-line--static {
  color: var(--ct-text);
  text-align: left;
  padding: 4px 0;
  font-size: var(--text-md);
}

/* ═══ 进度条 ═══ */
.fp-progress {
  flex-shrink: 0;
  width: 100%;
  padding: 16px 0 4px;
}

.fp-progress-track {
  position: relative;
  height: 7px;
  border-radius: 4px;
  background: var(--gradient-input);
  box-shadow: var(--effect-input-inner);
  cursor: pointer;
  margin-bottom: 6px;
  transition: height 0.12s ease;
}
.fp-progress:hover .fp-progress-track { height: 9px; }

.fp-progress-fill {
  height: 100%;
  border-radius: 4px;
  background: var(--gradient-button-primary);
  box-shadow:
    inset 0 1px 2px rgba(255 255 255 / 0.2),
    0 1px 4px rgb(var(--ct-accent-rgb) / 0.3);
  transition: width 0.15s linear;
  position: relative; z-index: 1;
}

.fp-progress-thumb {
  position: absolute;
  top: 50%;
  width: 14px; height: 14px;
  margin-left: -7px; margin-top: -7px;
  border-radius: 50%;
  background: var(--ct-accent);
  box-shadow: 0 0 8px rgb(var(--ct-accent-rgb) / 0.5), var(--shadow-sm);
  opacity: 0;
  transform: scale(0.5);
  transition: opacity 0.15s ease, transform 0.15s ease;
  z-index: 2;
}
.fp-progress-thumb--visible { opacity: 1; transform: scale(1); }

.fp-time-row {
  display: flex; justify-content: space-between;
}
.fp-time {
  font-size: var(--text-2xs);
  font-weight: 500;
  color: var(--ct-text-3);
  font-feature-settings: 'tnum';
}

/* ═══ 控制栏 ═══ */
.fp-controls {
  flex-shrink: 0;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0 0;
}

.fp-ctls-side {
  display: flex; align-items: center; gap: 6px;
}
.fp-ctls-right { justify-content: flex-end; }

.fp-ctls-center {
  display: flex; align-items: center; gap: 28px;
}

.fp-ctl-btn {
  color: var(--ct-text-2) !important;
  width: 38px; height: 38px;
  border-radius: var(--radius-md) !important;
  transition: all var(--transition-fast);
}
.fp-ctl-btn:hover:not(:disabled) {
  color: var(--ct-text) !important;
  background: var(--ct-bg-secondary) !important;
  box-shadow: var(--shadow-sm);
}
.fp-ctl-btn:active:not(:disabled) {
  transform: var(--scale-button-active);
  box-shadow: var(--effect-press);
}
.fp-ctl-btn:disabled { color: var(--ct-text-3) !important; opacity: 0.4; }
.fp-skip-btn { width: 42px; height: 42px; }

.fp-play-btn {
  width: 56px; height: 56px;
  display: flex;
  align-items: center; justify-content: center;
  border-radius: 50%;
  background: var(--gradient-button-primary);
  color: #fff;
  cursor: pointer;
  box-shadow:
    inset 1px 1px 3px rgba(255 255 255 / 0.3),
    inset -2px -2px 4px rgba(0 0 0 / 0.1),
    0 2px 12px rgb(var(--ct-accent-rgb) / 0.3),
    0 4px 20px rgb(var(--ct-accent-rgb) / 0.15);
  transition: all var(--transition-fast);
}
.fp-play-btn:hover {
  transform: scale(1.08);
  box-shadow:
    inset 1px 1px 3px rgba(255 255 255 / 0.3),
    inset -2px -2px 4px rgba(0 0 0 / 0.1),
    0 4px 18px rgb(var(--ct-accent-rgb) / 0.4),
    0 6px 28px rgb(var(--ct-accent-rgb) / 0.2);
}
.fp-play-btn:active {
  transform: scale(0.92);
  box-shadow: var(--effect-press);
}

.fp-mode-wrap { position: relative; display: inline-flex; }
.fp-mode--normal { color: var(--ct-text-2); }
.fp-mode--repeatAll, .fp-mode--repeatOne, .fp-mode--shuffle { color: var(--ct-accent); }
.fp-mode-badge {
  position: absolute; top: -3px; right: -5px;
  font-size: 7px; font-weight: 700;
  color: var(--ct-accent);
}

/* ═══ 音量弹窗 ═══ */
.fp-vol-wrap { position: relative; }
.fp-vol-drop {
  position: absolute;
  bottom: calc(100% + 10px); left: 50%;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 14px 12px;
  border-radius: var(--radius-lg);
  background: var(--gradient-card);
  border: var(--border-width-strong) solid var(--ct-border);
  box-shadow: var(--shadow-lg);
}
.fp-vol-num {
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--ct-text);
  font-feature-settings: 'tnum';
}

/* ═══ 空状态 ═══ */
.fp-empty { position: relative; z-index: 5; }
.fp-empty-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 40px 48px;
  border-radius: var(--radius-xl);
  background: var(--gradient-card);
  border: var(--border-width-strong) solid var(--ct-border);
  box-shadow: var(--shadow-lg);
}
.fp-empty-card p { font-size: var(--text-md); color: var(--ct-text-2); margin: 0; }

/* ═══ 响应式 ═══ */
@media (max-width: 768px) {
  .fp-layout { padding: 0 20px; }

  .fp-body {
    flex-direction: column;
    gap: 12px;
    padding: 4px 0;
  }

  .fp-left {
    width: 100%;
    flex-shrink: 1;
    flex-direction: row;
    align-items: center;
    gap: 16px;
  }

  .fp-cover-frame {
    width: 120px; height: 120px;
  }

  .fp-cover-meta { display: none; }

  .fp-progress { padding: 0; flex: 1; }

  .fp-controls { justify-content: center; gap: 12px; }

  .fp-lyrics-section {
    flex: 1;
    width: 100%;
    min-height: 140px;
  }

  .fp-lyric-line {
    font-size: var(--text-md);
    padding: 4px 16px;
  }
  .fp-lyric-line--active { font-size: var(--text-lg); }}

@media (prefers-reduced-motion: reduce) {
  .fp-cover-frame--spinning .fp-cover-inner { animation: none; }
  .fp-bg-cover--visible { animation: none; }
  .fp-lyric-line { transition: none; }
}
</style>
