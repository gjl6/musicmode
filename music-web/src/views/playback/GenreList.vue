<template>
  <div class="genre-list-page">
    <!-- ═══ 页面头部 ═══ -->
    <div class="page-header">
      <div class="header-left">
        <h1>{{ $t('genre.title') }}</h1>
        <span class="header-count">{{ $t('genre.count', { count: library.genreTotal }) }}</span>
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

    <!-- ═══ 字母索引（仅按名称排序时显示）═══ -->
    <div v-if="sortType === 'name' && letterChips.length" class="letter-bar">
      <button
        class="letter-chip"
        :class="{ active: currentLetter === null }"
        @click="onLetterChange(null)"
      >{{ $t('genre.all') }}</button>
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
      <div v-if="view === 'grid' && displayGenres.length" class="genre-grid">
        <GenreCard
          v-for="genre in displayGenres"
          :key="genre.genre"
          :genre="genre"
          :size="160"
          @click="goDetail(genre.genre)"
          @play="playGenre(genre)"
        />
      </div>

      <!-- ═══ 列表视图 ═══ -->
      <GenreTable
        v-if="view === 'list' && displayGenres.length"
        :genres="displayGenres"
        :page="currentPage"
        :page-size="pageSize"
        @click="goDetail"
        @play="playGenre"
      />

      <!-- ═══ 空状态 ═══ -->
      <n-empty
        v-if="!library.loading && !displayGenres.length"
        :description="$t('genre.none')"
        size="small"
        style="margin-top:48px"
      />
    </n-spin>

    <!-- ═══ 分页 ═══ -->
    <n-pagination
      v-if="library.genreTotal > 0"
      v-model:page="currentPage"
      v-model:page-size="pageSize"
      :page-sizes="[12, 24, 36, 48]"
      :item-count="library.genreTotal"
      size="small"
      show-size-picker
      class="genre-pagination"
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
import { getGenreSongs } from '@/api/playback/genre.js'
import GenreCard from '@/components/playback/GenreCard.vue'
import GenreTable from '@/components/playback/GenreTable.vue'
import ViewToggle from '@/components/playback/ViewToggle.vue'

const { t } = useI18n()
const router = useRouter()
const message = useMessage()
const library = useLibraryStore()
const player = usePlayerStore()

const searchText = ref('')

function doSearch() {
  const q = searchText.value.trim()
  if (!q) return
  router.push(`/player/search?q=${encodeURIComponent(q)}`)
}

// ═══ 状态 ═══

const view = computed({
  get: () => library.currentView,
  set: (v) => { library.setView(v) },
})

const currentPage = ref(1)
const currentLetter = ref(null)
const pageSize = ref(24)

const sortType = computed({
  get: () => library.genreSortType,
  set: (v) => { library.genreSortType = v },
})

const sortOptions = computed(() => [
  { label: t('genre.sortByName'), value: 'name' },
  { label: t('genre.sortBySongCount'), value: 'songCount' },
])

// ═══ 计算属性 ═══

const genres = computed(() => library.genres)

const displayGenres = computed(() => genres.value)

/** 将后端统计数据转为前端字母 chip 列表 */
const letterChips = computed(() => {
  const list = library.genreLetters
  if (!list.length) return []
  const map = new Map()
  for (const L of list) {
    const letter = L.letter || L.LETTER || Object.values(L)[0]
    const cnt = L.cnt || L.CNT || Object.values(L)[1] || 0
    map.set(String(letter), Number(cnt))
  }
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
  // 并行加载字母统计和风格列表（互不阻塞）
  library.loadGenreLetters()
  doLoad()
})

function doLoad() {
  const offset = (currentPage.value - 1) * pageSize.value
  library.loadGenres({
    sort: sortType.value,
    letter: sortType.value === 'name' ? currentLetter.value : null,
    count: pageSize.value,
    offset,
  })
}

function onSortChange() {
  currentPage.value = 1
  if (sortType.value !== 'name') {
    currentLetter.value = null
  }
  doLoad()
}

function onLetterChange(letter) {
  currentLetter.value = letter
  currentPage.value = 1
  doLoad()
}

function onPageChange() {
  doLoad()
}

function onPageSizeChange() {
  currentPage.value = 1
  doLoad()
}

// ═══ 操作 ═══

async function playGenre(genre) {
  const name = genre?.genre
  if (!name) return
  try {
    const res = await getGenreSongs(name, { sort: 'alphabetical', limit: 500, offset: 0 })
    const songs = res?.songs || []
    const audioSongs = songs.filter(s => s.path)
    if (audioSongs.length > 0) {
      player.playAll(audioSongs, 0)
      return
    }
    message.warning(t('player.noData'))
  } catch {
    message.warning(t('player.loadError'))
  }
}

function goDetail(genreName) {
  if (genreName) router.push(`/player/genres/${encodeURIComponent(genreName)}`)
}
</script>

<style scoped>
/* ════════════════════════════════════════════════════
   风格列表页 — 字母索引 + 服务端分页 + 双视图
   ════════════════════════════════════════════════════ */

.genre-list-page {
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

/* ── 风格网格 ── */
.genre-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

/* ── 分页 ── */
.genre-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
