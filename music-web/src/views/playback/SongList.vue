<template>
  <div class="song-list-page">
    <!-- ═══ 页面头部 ═══ -->
    <div class="page-header">
      <div class="header-left">
        <h1>{{ $t('song.title') }}</h1>
        <span class="header-count">{{ $t('song.count', { count: library.songTotal }) }}</span>
      </div>
      <div class="header-actions">
        <n-input
          v-model:value="searchText"
          :placeholder="$t('player.searchPlaceholder')"
          size="small"
          clearable
          round
          style="width:180px"
          @keyup.enter="doSearch"
        >
          <template #prefix>
            <n-icon :size="16"><SearchOutline /></n-icon>
          </template>
        </n-input>
        <n-select
          v-model:value="sortType"
          :options="sortOptions"
          size="small"
          :consistent-menu-width="false"
          style="width:120px"
          @update:value="onSortChange"
        />
        <ViewToggle v-model="view" />
      </div>
    </div>

    <!-- ═══ 字母索引（仅 A-Z 排序时显示）═══ -->
    <div v-if="sortType === 'alphabetical' && letterChips.length" class="letter-bar">
      <button
        class="letter-chip"
        :class="{ active: currentLetter === null }"
        @click="onLetterChange(null)"
      >{{ $t('song.all') }}</button>
      <button
        v-for="ch in letterChips"
        :key="ch.letter"
        class="letter-chip"
        :class="{ active: currentLetter === ch.letter }"
        :disabled="ch.cnt === 0"
        @click="onLetterChange(ch.letter)"
      >{{ ch.letter }}</button>
    </div>

    <n-spin :show="library.loading" size="medium">
      <!-- ═══ 网格视图 ═══ -->
      <div v-if="view === 'grid' && displaySongs.length" class="song-grid">
        <SongCard
          v-for="song in displaySongs"
          :key="song.id"
          :song="song"
          :starred="song._starred"
          @play="playSong(song)"
          @toggle-fav="toggleFav(song)"
          @rate="onRateSong"
        />
      </div>

      <!-- ═══ 表格视图 ═══ -->
      <SongTable
        v-else-if="view === 'list' && displaySongs.length"
        :songs="displaySongs"
        :loading="library.loading"
        @play="playSong"
        @add-to-queue="addToQueue"
        @toggle-fav="toggleFav"
        @rate="onRateSong"
        @add-to-playlist="onAddToPlaylist"
      />

      <!-- ═══ 空状态 ═══ -->
      <n-empty
        v-if="!library.loading && !displaySongs.length"
        :description="$t('player.noData')"
        size="small"
        style="margin-top:48px"
      />
    </n-spin>

    <!-- ═══ 添加到歌单弹窗 ═══ -->
    <AddToPlaylistModal
      :show="showAddModal"
      :song-ids="addSongIds"
      @update:show="(v) => { showAddModal = v; if (!v) addSongIds = [] }"
      @added="onAddedToPlaylist"
    />

    <!-- ═══ 分页 ═══ -->
    <n-pagination
      v-if="library.songTotal > 0"
      v-model:page="currentPage"
      v-model:page-size="pageSize"
      :page-sizes="[12, 24, 36, 48]"
      :item-count="library.songTotal"
      size="small"
      show-size-picker
      class="song-pagination"
      @update:page="onPageChange"
      @update:page-size="onPageSizeChange"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { SearchOutline } from '@vicons/ionicons5'
import { useMessage } from 'naive-ui'
import { useLibraryStore } from '@/store/playback/library.js'
import { usePlayerStore } from '@/store/playback/player.js'
import SongCard from '@/components/playback/SongCard.vue'
import SongTable from '@/components/playback/SongTable.vue'
import ViewToggle from '@/components/playback/ViewToggle.vue'
import AddToPlaylistModal from '@/components/playback/AddToPlaylistModal.vue'

const { t } = useI18n()
const router = useRouter()
const message = useMessage()
const library = useLibraryStore()
const player = usePlayerStore()

// ═══ 状态 ═══

const searchText = ref('')

function doSearch() {
  const q = searchText.value.trim()
  if (!q) return
  router.push(`/player/search?q=${encodeURIComponent(q)}`)
}

const view = computed({
  get: () => library.currentView,
  set: (v) => { library.setView(v) },
})
const pageSize = ref(24)

// 分页/筛选状态托管在 library store 中（跨导航保持）
const currentPage = computed({
  get: () => library.songPage,
  set: (v) => { library.songPage = v },
})
const currentLetter = computed({
  get: () => library.currentLetter,
  set: (v) => { library.currentLetter = v },
})
const sortType = computed({
  get: () => library.songSortType,
  set: (v) => { library.songSortType = v },
})

const sortOptions = computed(() => [
  { label: t('song.sortNewest'), value: 'newest' },
  { label: t('song.sortFrequent'), value: 'frequent' },
  { label: t('song.sortByName'), value: 'alphabetical' },
])

// ═══ 计算属性 ═══

const songs = computed(() => library.songs)

/** 将收藏状态直接合入歌曲数据，确保 data 变化驱动 table 重渲染 */
const displaySongs = computed(() => {
  const fav = player.favoriteIds
  return songs.value.map(s => ({
    ...s,
    _starred: s.id != null ? fav.has(Number(s.id)) : false,
  }))
})

/** 将后端统计数据转为前端字母 chip 列表（A-Z, 0-9, #） */
const letterChips = computed(() => {
  const map = new Map()
  for (const L of library.songLetters) {
    const letter = L.letter || L.LETTER || Object.values(L)[0]
    const cnt = L.cnt || L.CNT || Object.values(L)[1] || 0
    map.set(String(letter), Number(cnt))
  }
  // 按顺序排列：A-Z, 0-9, #
  const result = []
  for (let c = 65; c <= 90; c++) {
    const letter = String.fromCharCode(c)
    result.push({ letter, cnt: map.get(letter) || 0 })
  }
  result.push({ letter: '0-9', cnt: map.get('0-9') || 0 })
  result.push({ letter: '#', cnt: map.get('#') || 0 })
  return result
})

// ═══ 数据加载 ═══

onMounted(async () => {
  await library.loadSongLetters()
  // 仅在首次进入时加载（store 中有数据说明之前已加载过，保持原状态）
  if (library.songs.length === 0) {
    doLoad()
  }
  // 加载已收藏歌曲 ID 列表
  player.loadFavoriteIds()
})

function doLoad() {
  library.loadSongs({
    letter: currentLetter.value,
    sort: sortType.value,
    count: pageSize.value,
    offset: (currentPage.value - 1) * pageSize.value,
  })
}

function onSortChange() {
  currentPage.value = 1
  doLoad()
}

function onLetterChange(letter) {
  currentLetter.value = letter
  currentPage.value = 1
  doLoad()
}

function onPageChange(page) {
  currentPage.value = page
  doLoad()
}

function onPageSizeChange() {
  currentPage.value = 1
  doLoad()
}

// ═══ 操作 ═══

function playSong(song) {
  if (song?.id || song?.path) player.play(song)
}

function addToQueue(song) {
  if (song) player.addToQueue([song])
}

async function toggleFav(song) {
  const id = song?.id
  if (id == null) return
  const wasStarred = player.isSongStarred(id)
  try {
    if (wasStarred) {
      await player.unstarSong(id)
    } else {
      await player.starSong(id)
    }
    message.success(wasStarred ? t('player.unfavorited') : t('player.favorited'))
  } catch {
    message.warning(t('player.favoriteFailed'))
  }
}

/** 评分变更后更新本地 songs 中的 userRating，保持 UI 即时响应 */
function onRateSong({ songId, rating }) {
  const list = library.songs
  const idx = list.findIndex(s => Number(s.id) === songId)
  if (idx >= 0) {
    list[idx] = { ...list[idx], userRating: rating }
  }
}

// ── 添加到歌单 ──

const showAddModal = ref(false)
const addSongIds = ref([])

function onAddToPlaylist(song) {
  addSongIds.value = [Number(song.id)]
  showAddModal.value = true
}

function onAddedToPlaylist() {
  // 歌单数据可能已变更（新增或添加了歌曲），无需额外操作
}
</script>

<style scoped>
/* ════════════════════════════════════════════════════
   歌曲列表页 — 字母索引 + 服务端分页 + 双视图
   ════════════════════════════════════════════════════ */

.song-list-page {
  width: min(100% - var(--content-padding-x) * 2, var(--content-max-width));
  margin: 0 auto;
  padding: 24px 0;
}

/* ── 页头 ── */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  gap: 16px;
  flex-wrap: wrap;
}

.header-left {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-shrink: 0;
}

.header-left h1 {
  font-size: 22px;
  font-weight: 700;
  margin: 0;
  color: var(--ct-text);
}

.header-count {
  font-size: var(--text-sm);
  color: var(--ct-text-3);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

/* ── 字母索引 ── */
.letter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  padding: 6px 0 16px;
}

.letter-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  height: 28px;
  padding: 2px 7px;
  border-radius: 6px;
  border: 1px solid var(--ct-border);
  background: transparent;
  color: var(--ct-text-2);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
  font-family: inherit;
}
.letter-chip:hover:not(:disabled) {
  border-color: var(--ct-accent);
  color: var(--ct-accent);
}
.letter-chip.active {
  background: var(--ct-accent);
  border-color: var(--ct-accent);
  color: #fff;
}
.letter-chip:disabled {
  opacity: 0.3;
  cursor: default;
}

/* ── 歌曲网格 ── */
.song-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

/* ── 分页 ── */
.song-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
