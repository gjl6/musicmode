<template>
  <div class="song-detail-page">
    <!-- ═══ 面包屑导航 ═══ -->
    <div class="breadcrumb">
      <n-button text class="back-btn" @click="goBack">
        <template #icon><n-icon :size="18"><ChevronBackOutline /></n-icon></template>
      </n-button>
      <span class="breadcrumb-sep">/</span>
      <span v-if="song?.artists && song.artists.length > 1" class="breadcrumb-text">
        <span
          v-for="(a, i) in song.artists"
          :key="a.id"
          class="breadcrumb-link"
          @click="goArtist(a.id)"
        >{{ a.name }}<span v-if="i < song.artists.length - 1"> / </span></span>
      </span>
      <span
        v-else-if="song?.artistId"
        class="breadcrumb-link"
        @click="goArtist(song.artistId)"
      >{{ displayArtist }}</span>
      <span v-else class="breadcrumb-text">{{ displayArtist }}</span>
      <span class="breadcrumb-sep">/</span>
      <span class="breadcrumb-current">{{ song?.title || '—' }}</span>
      <n-input
        v-model:value="searchText"
        :placeholder="$t('player.searchPlaceholder')"
        size="tiny"
        clearable
        round
        style="width:160px;margin-left:auto"
        @keyup.enter="doSearch"
      >
        <template #prefix>
          <n-icon :size="14"><SearchOutline /></n-icon>
        </template>
      </n-input>
    </div>

    <n-spin :show="loading" size="medium">
      <template v-if="song">
        <!-- ═══ 双栏布局 ═══ -->
        <div class="detail-body">
          <!-- ── 左栏：封面 + 信息 ── -->
          <div class="detail-left">
            <div class="cover-wrap">
              <img
                v-if="coverUrl"
                :src="coverUrl"
                :alt="song.title || ''"
                class="cover-img"
              />
              <div v-else class="cover-placeholder">
                <n-icon :size="48" color="var(--ct-text-3)">
                  <MusicalNotesOutline />
                </n-icon>
              </div>
            </div>

            <!-- 艺术家 -->
            <div class="info-block">
              <span class="info-label">艺术家</span>
              <span v-if="song.artists && song.artists.length > 1" class="info-text">
                <span
                  v-for="(a, i) in song.artists"
                  :key="a.id"
                  class="info-link"
                  @click="goArtist(a.id)"
                >{{ a.name }}<span v-if="i < song.artists.length - 1"> / </span></span>
              </span>
              <span
                v-else-if="song.artistId"
                class="info-link"
                @click="goArtist(song.artistId)"
              >{{ displayArtist }}</span>
              <span v-else class="info-text">{{ displayArtist }}</span>
            </div>

            <!-- 专辑 -->
            <div class="info-block">
              <span class="info-label">专辑</span>
              <span
                v-if="song.albumId"
                class="info-link"
                @click="goAlbum(song.albumId)"
              >{{ song.album || '—' }}</span>
              <span v-else class="info-text">{{ song.album || '—' }}</span>
            </div>

            <!-- 元数据标签 -->
            <div class="meta-tags">
              <div v-if="song.duration" class="meta-item">
                <n-icon :size="14"><TimeOutline /></n-icon>
                <span>{{ formatDuration(song.duration) }}</span>
              </div>
              <div v-if="song.year" class="meta-item">
                <n-icon :size="14"><CalendarOutline /></n-icon>
                <span>{{ song.year }}</span>
              </div>
              <div v-if="song.genre" class="meta-item">
                <n-icon :size="14"><PricetagsOutline /></n-icon>
                <span>{{ song.genre }}</span>
              </div>
              <div v-if="song.bitRate" class="meta-item">
                <n-icon :size="14"><PulseOutline /></n-icon>
                <span>{{ song.bitRate }} kbps</span>
              </div>
              <div v-if="song.suffix" class="meta-item">
                <n-icon :size="14"><DocumentOutline /></n-icon>
                <span>{{ song.suffix.toUpperCase() }}</span>
              </div>
              <div v-if="song.track" class="meta-item">
                <n-icon :size="14"><MusicalNoteOutline /></n-icon>
                <span>#{{ song.track }}</span>
              </div>
              <div v-if="song.size" class="meta-item">
                <n-icon :size="14"><ServerOutline /></n-icon>
                <span>{{ formatSize(song.size) }}</span>
              </div>
            </div>

            <!-- 评分 -->
            <div class="info-block">
              <span class="info-label">评分</span>
              <StarRatingComp
                :rating="userRating"
                :song-id="song?.id"
                :size="20"
                @rated="onRated"
              />
            </div>

            <!-- 操作按钮 -->
            <div class="action-btns">
              <n-button type="primary" size="medium" round block @click="playSong">
                <template #icon><n-icon :size="18"><PlayOutline /></n-icon></template>
                播放
              </n-button>
              <n-button size="medium" round block class="action-btn--secondary" @click="addToQueue">
                <template #icon><n-icon :size="18"><AddOutline /></n-icon></template>
                添加到队列
              </n-button>
              <n-button size="medium" round block class="action-btn--secondary" @click="onAddToPlaylist">
                <template #icon><n-icon :size="18"><ListOutline /></n-icon></template>
                添加到歌单
              </n-button>
              <n-button
                size="medium"
                round
                block
                class="action-btn--secondary"
                :type="isFavorited ? 'warning' : 'default'"
                @click="toggleFav"
              >
                <template #icon>
                  <n-icon :size="18" :color="isFavorited ? '#EF4444' : undefined">
                    <Heart v-if="isFavorited" />
                    <HeartOutline v-else />
                  </n-icon>
                </template>
                {{ isFavorited ? '已收藏' : '收藏' }}
              </n-button>
            </div>
          </div>

          <!-- ── 右栏：歌词 ── -->
          <div class="detail-right">
            <!-- 加载中 -->
            <div v-if="lyricsLoading" class="lyrics-status">
              <n-spin :size="20" />
              <span>{{ $t('fullscreen.loading') }}</span>
            </div>

            <!-- 同步 LRC 歌词 -->
            <div
              v-else-if="lyricsSynced && lyricsLines.length"
              ref="lyricsScrollRef"
              class="lyrics-scroll"
            >
              <div class="lyrics-scroll-inner">
                <div
                  v-for="(line, i) in lyricsLines"
                  :key="i"
                  class="lyric-line"
                  :class="{
                    'lyric-line--active': i === activeLine,
                    'lyric-line--past': i < activeLine,
                  }"
                >{{ line.value }}</div>
              </div>
            </div>

            <!-- 纯文本歌词 -->
            <div v-else-if="lyricsLines.length" class="lyrics-plain">
              <div
                v-for="(line, i) in lyricsLines"
                :key="i"
                class="lyric-line lyric-line--static"
              >{{ line.value }}</div>
            </div>

            <!-- 暂无歌词 -->
            <div v-else class="lyrics-status">
              <n-icon :size="32" color="var(--ct-text-3)"><MusicalNotesOutline /></n-icon>
              <span>{{ $t('songDetail.noLyrics') }}</span>
              <p class="lyrics-hint">{{ $t('songDetail.noLyricsHint') }}</p>
            </div>
          </div>
        </div>
      </template>

      <!-- Not found -->
      <n-empty
        v-else-if="!loading"
        :description="$t('player.noData')"
        size="medium"
        style="margin-top:80px"
      />
    </n-spin>

    <!-- 添加到歌单弹窗 -->
    <AddToPlaylistModal
      :show="showAddModal"
      :song-ids="addSongIds"
      :song-title="song?.title || ''"
      @update:show="(v) => { showAddModal = v; if (!v) addSongIds = [] }"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { SearchOutline } from '@vicons/ionicons5'
import {
  MusicalNotesOutline, PlayOutline, AddOutline, HeartOutline, Heart,
  ChevronBackOutline, TimeOutline, CalendarOutline, PricetagsOutline,
  PulseOutline, DocumentOutline, MusicalNoteOutline, ServerOutline,
  ListOutline,
} from '@vicons/ionicons5'
import { usePlayerStore } from '@/store/playback/player.js'
import StarRatingComp from '@/components/playback/StarRating.vue'
import AddToPlaylistModal from '@/components/playback/AddToPlaylistModal.vue'
import {
  subsonicGetSong, subsonicGetCoverArtUrl, subsonicGetLyricsBySongId,
} from '@/api/playback/subsonic.js'

const route = useRoute()
const router = useRouter()

const searchText = ref('')

function doSearch() {
  const q = searchText.value.trim()
  if (!q) return
  router.push(`/player/search?q=${encodeURIComponent(q)}`)
}

const player = usePlayerStore()
const { t } = useI18n()

const song = ref(null)
const loading = ref(false)
const lyricsLines = ref([])
const lyricsSynced = ref(false)
const lyricsLoading = ref(false)
const activeLine = ref(-1)
const isFavorited = computed(() => player.isSongStarred(song.value?.id))
const userRating = computed(() => Number(song.value?.userRating) || 0)
const displayArtist = computed(() => song.value?.displayArtist || song.value?.artist || '—')

const coverUrl = computed(() => {
  const artId = song.value?.coverArt || song.value?.albumId
  return artId ? subsonicGetCoverArtUrl(artId, 300) : ''
})

// ── 格式化 ──

function formatDuration(s) {
  if (!s || !isFinite(s)) return '—'
  const m = Math.floor(s / 60)
  const sec = Math.floor(s % 60)
  return `${m}:${String(sec).padStart(2, '0')}`
}

function formatSize(bytes) {
  if (!bytes) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1048576).toFixed(1)} MB`
}

// ── 数据加载 ──

onMounted(() => loadSongData())
watch(() => route.params.id, (newId, oldId) => {
  if (newId && newId !== oldId) loadSongData()
})

async function loadSongData() {
  const id = route.params.id
  if (!id) return
  loading.value = true
  lyricsLines.value = []
  lyricsSynced.value = false
  activeLine.value = -1
  try {
    const data = await subsonicGetSong(id)
    song.value = data?.['subsonic-response']?.song || data?.song || null
    if (song.value) {
      loadLyrics(song.value.id)
    }
  } catch (e) {
    console.error('[SongDetail] 加载歌曲失败:', e)
  } finally {
    loading.value = false
  }
  player.loadFavoriteIds()
}

async function loadLyrics(songId) {
  lyricsLoading.value = true
  try {
    const res = await subsonicGetLyricsBySongId(songId)
    const sl = res?.['subsonic-response']?.lyricsList?.structuredLyrics
    if (sl && sl.length > 0) {
      lyricsSynced.value = sl[0].synced === true
      lyricsLines.value = sl[0].line || []
    }
  } catch (e) {
    console.warn('[SongDetail] 歌词加载失败:', e)
  } finally {
    lyricsLoading.value = false
  }
}

// ── 歌词时间同步 ──

watch(
  () => [player.currentTime, player.current?.id],
  () => {
    if (!lyricsSynced.value || !song.value) return
    if (player.current?.id !== song.value.id) { activeLine.value = -1; return }
    const t = player.currentTime * 1000
    const lines = lyricsLines.value
    let lo = 0, hi = lines.length - 1, idx = -1
    while (lo <= hi) {
      const mid = (lo + hi) >> 1
      if ((lines[mid].start || 0) <= t) { idx = mid; lo = mid + 1 }
      else { hi = mid - 1 }
    }
    activeLine.value = idx
  },
)

// ── 歌词滚动 ──

const lyricsScrollRef = ref(null)
watch(activeLine, (idx) => {
  if (!lyricsScrollRef.value || idx < 0) return
  nextTick(() => {
    const el = lyricsScrollRef.value.querySelector('.lyric-line--active')
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
})

// ── 操作 ──

function playSong() { if (song.value) player.play(song.value) }
function addToQueue() { if (song.value) player.addToQueue([song.value]) }

async function toggleFav() {
  if (!song.value?.id) return
  try {
    if (isFavorited.value) {
      await player.unstarSong(song.value.id)
    } else {
      await player.starSong(song.value.id)
    }
  } catch { window.$message?.warning(t('player.favoriteFailed')) }
}

function onRated(val) {
  if (song.value) song.value.userRating = val
}

// ── 添加到歌单 ──

const showAddModal = ref(false)
const addSongIds = ref([])

function onAddToPlaylist() {
  if (!song.value?.id) return
  addSongIds.value = [Number(song.value.id)]
  showAddModal.value = true
}

function goBack() { router.back() }
function goArtist(id) { if (id) router.push(`/player/artists/${id}`) }
function goAlbum(id) { if (id) router.push(`/player/albums/${id}`) }
</script>

<style scoped>
/* ════════════════════════════════════════════════════
   歌曲详情页 — 双栏分屏布局
   ════════════════════════════════════════════════════ */

.song-detail-page {
  width: min(100% - var(--content-padding-x) * 2, var(--content-max-width-narrow));
  margin: 0 auto;
  padding: 20px 0 32px;
}

/* ── 面包屑 ── */
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 0 20px;
  font-size: var(--text-sm);
  color: var(--ct-text-3);
}
.back-btn {
  color: var(--ct-text-2) !important;
  margin-right: 4px;
}
.back-btn:hover { color: var(--ct-text) !important; }

.breadcrumb-sep {
  color: var(--ct-border);
  font-size: var(--text-sm);
}
.breadcrumb-link {
  color: var(--ct-text-2);
  cursor: pointer;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.breadcrumb-link:hover { color: var(--ct-accent); text-decoration: underline; }
.breadcrumb-text {
  color: var(--ct-text-2);
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.breadcrumb-current {
  color: var(--ct-text);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ═══ 双栏 ═══ */
.detail-body {
  display: flex;
  gap: 40px;
  align-items: flex-start;
}

/* ── 左栏 ── */
.detail-left {
  flex: 0 0 300px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 封面 */
.cover-wrap {
  width: 300px;
  height: 300px;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0,0,0,0.12);
  background: var(--ct-bg-secondary);
}
.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 信息块 */
.info-block {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: var(--text-md);
}
.info-label {
  font-size: var(--text-xs);
  color: var(--ct-text-3);
  min-width: 40px;
  flex-shrink: 0;
}
.info-link {
  color: var(--ct-accent);
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.info-link:hover { text-decoration: underline; }
.info-text {
  color: var(--ct-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 元数据标签 */
.meta-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 6px;
  font-size: var(--text-xs);
  color: var(--ct-text-2);
  background: var(--ct-bg-secondary);
}
.meta-item .n-icon {
  color: var(--ct-text-3);
}

/* 操作按钮 */
.action-btns {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 4px;
}
.action-btn--secondary {
  color: var(--ct-text-2) !important;
  background: var(--ct-bg-secondary) !important;
  border: none !important;
}
.action-btn--secondary:hover {
  color: var(--ct-text) !important;
  background: var(--ct-bg-hover) !important;
}

/* ── 右栏：歌词 ── */
.detail-right {
  flex: 1;
  min-width: 0;
  min-height: 520px;
  display: flex;
  flex-direction: column;
  border-radius: 12px;
  background: var(--ct-bg-secondary);
  overflow: hidden;
}

/* 歌词状态 */
.lyrics-status {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--ct-text-3);
  font-size: var(--text-md);
}
.lyrics-hint {
  font-size: var(--text-xs);
  color: var(--ct-text-3);
  margin: 0;
}

/* 同步滚动歌词 */
.lyrics-scroll {
  flex: 1;
  overflow-y: auto;
  mask-image: linear-gradient(to bottom, transparent 0%, black 10%, black 90%, transparent 100%);
  -webkit-mask-image: linear-gradient(to bottom, transparent 0%, black 10%, black 90%, transparent 100%);
}
.lyrics-scroll::-webkit-scrollbar { width: 0; }

.lyrics-scroll-inner {
  display: flex;
  flex-direction: column;
  padding: 60px 0;
}

.lyric-line {
  padding: 6px 28px;
  font-size: var(--text-lg);
  line-height: 1.8;
  color: var(--ct-text-3);
  text-align: center;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: default;
}
.lyric-line--past { color: var(--ct-text-2); }
.lyric-line--active {
  color: var(--ct-accent);
  font-size: calc(var(--text-lg) + 3px);
  font-weight: 700;
  padding: 8px 28px;
  background: linear-gradient(90deg, transparent, rgb(var(--ct-accent-rgb) / 0.06), transparent);
}

/* 纯文本歌词 */
.lyrics-plain {
  flex: 1;
  overflow-y: auto;
  padding: 20px 28px;
}
.lyric-line--static {
  color: var(--ct-text);
  text-align: left;
  padding: 4px 0;
  font-size: var(--text-md);
}
</style>
