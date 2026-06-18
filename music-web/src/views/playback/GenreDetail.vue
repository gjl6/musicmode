<template>
  <div class="genre-detail-page">

    <div class="breadcrumb">
      <n-button text class="back-btn" @click="goBack">
        <template #icon><n-icon :size="18"><ChevronBackOutline /></n-icon></template>
      </n-button>
      <span class="breadcrumb-sep">/</span>
      <span class="breadcrumb-link" @click="goGenres">{{ $t('genre.title') }}</span>
      <span class="breadcrumb-sep">/</span>
      <span class="breadcrumb-current">{{ genreName || '—' }}</span>
    </div>

    <n-spin :show="library.genreSongsLoading && !displaySongs.length" size="medium">

      <div class="detail-hero">
        <div class="hero-cover">
          <CoverArt
            :src="coverUrl"
            :alt="genreName || ''"
            :size="200"
          />
        </div>
        <div class="hero-info">
          <div class="hero-name-row">
            <h1>{{ genreName || '—' }}</h1>
          </div>


          <div class="hero-meta">
            <span class="meta-tag count">{{ $t('genre.songCount', { count: library.genreDetail?.songCount ?? library.genreSongTotal }) }}</span>
          </div>


          <div v-if="library.genreDetail?.description" class="hero-intro">
            <p :class="{ 'intro-clamped': !introExpanded }">{{ library.genreDetail.description }}</p>
            <button
              v-if="library.genreDetail.description.length > 150"
              class="intro-toggle"
              @click="introExpanded = !introExpanded"
            >{{ introExpanded ? '收起' : '展开' }}</button>
          </div>


          <n-space style="margin-top:12px">
            <n-button type="primary" size="small" @click="playAll">
              <n-icon :size="16"><PlayOutline /></n-icon>
              {{ $t('genre.playAll') }}
            </n-button>
            <n-button size="small" @click="shuffleAll">
              <n-icon :size="16"><ShuffleOutline /></n-icon>
              {{ $t('genre.shuffle') }}
            </n-button>
            <n-button size="small" @click="showEditModal = true">
              <n-icon :size="16"><CreateOutline /></n-icon>
              编辑
            </n-button>
          </n-space>
        </div>
      </div>


      <div class="detail-songs">

        <div class="tab-bar">
          <div class="tab-label">{{ $t('song.title') }} ({{ library.genreSongTotal }})</div>
          <n-select
            v-model:value="songSort"
            :options="songSortOptions"
            size="small"
            :consistent-menu-width="false"
            style="width:110px"
            @update:value="onSortChange"
          />
        </div>


        <div v-if="songSort === 'alphabetical'" class="letter-bar">
          <button
            class="letter-chip"
            :class="{ active: songLetter === null }"
            @click="onLetterChange(null)"
          >{{ $t('song.all') }}</button>
          <button
            v-for="ch in letterChips"
            :key="ch"
            class="letter-chip"
            :class="{ active: songLetter === ch }"
            @click="onLetterChange(ch)"
          >{{ ch }}</button>
        </div>

        <n-spin :show="library.genreSongsLoading" size="small">
          <SongTable
            v-if="displaySongs.length"
            :songs="displaySongs"
            @play="playSong"
            @addToQueue="addToQueue"
            @toggleFav="toggleSongFav"
            @rate="onRateSong"
            @addToPlaylist="onAddToPlaylist"
          />
          <n-empty
            v-else-if="!library.genreSongsLoading"
            :description="$t('player.noData')"
            size="small"
            style="margin-top:32px"
          />
        </n-spin>

        <n-pagination
          v-if="library.genreSongTotal > songPageSize"
          v-model:page="songPage"
          v-model:page-size="songPageSize"
          :page-sizes="[20, 50, 100]"
          :item-count="library.genreSongTotal"
          size="small"
          show-size-picker
          class="detail-pagination"
          @update:page="loadSongs"
          @update:page-size="onPageSizeChange"
        />
      </div>
    </n-spin>


    <AddToPlaylistModal
      :show="showAddModal"
      :song-ids="addSongIds"
      @update:show="(v) => { showAddModal = v; if (!v) addSongIds = [] }"
      @added="onAddedToPlaylist"
    />


    <n-modal
      v-model:show="showEditModal"
      preset="card"
      title="编辑风格"
      style="width:480px"
      :mask-closable="false"
    >
      <n-form ref="editFormRef" :model="editForm" label-placement="top" size="small">
        <n-form-item label="风格名称">
          <n-input v-model:value="editForm.name" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input
            v-model:value="editForm.description"
            type="textarea"
            :rows="3"
            placeholder="风格描述..."
          />
        </n-form-item>
        <n-form-item label="封面图 URL">
          <n-input v-model:value="editForm.styleImage" placeholder="可留空" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button size="small" @click="showEditModal = false">取消</n-button>
          <n-button type="primary" size="small" :loading="saving" @click="doSave">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PlayOutline, ShuffleOutline, ChevronBackOutline, CreateOutline } from '@vicons/ionicons5'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useLibraryStore } from '@/store/playback/library.js'
import { usePlayerStore } from '@/store/playback/player.js'
import { subsonicGetCoverArtUrl } from '@/api/playback/subsonic.js'
import CoverArt from '@/components/playback/CoverArt.vue'
import SongTable from '@/components/playback/SongTable.vue'
import AddToPlaylistModal from '@/components/playback/AddToPlaylistModal.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const message = useMessage()
const library = useLibraryStore()
const player = usePlayerStore()

const genreName = computed(() => {
  try { return decodeURIComponent(route.params.name || '') } catch { return route.params.name || '' }
})

const coverUrl = computed(() => {
  const artId = library.genreDetail?.coverArt || library.genreDetail?.id
  return artId ? subsonicGetCoverArtUrl(artId, 200) : ''
})

const introExpanded = ref(false)


const showEditModal = ref(false)
const saving = ref(false)
const editForm = ref({
  name: '',
  description: '',
  styleImage: '',
})

watch(showEditModal, (v) => {
  if (v && genreName.value) {
    const detail = library.genreDetail
    editForm.value = {
      name: detail?.name || genreName.value || '',
      description: detail?.description || '',
      styleImage: detail?.styleImage || '',
    }
  }
})

async function doSave() {
  saving.value = true
  try {
    const res = await library.updateGenreDetail(genreName.value, editForm.value)
    if (res?.style) {
      message.success('保存成功')
      showEditModal.value = false
    }
  } catch {
    message.warning('保存失败')
  } finally {
    saving.value = false
  }
}


const letterChips = (() => {
  const chips = []
  for (let c = 65; c <= 90; c++) chips.push(String.fromCharCode(c))
  chips.push('0-9', '#')
  return chips
})()


const songSort = ref('alphabetical')
const songLetter = ref(null)
const songPage = ref(1)
const songPageSize = ref(50)

const songSortOptions = computed(() => [
  { label: t('song.sortByName'), value: 'alphabetical' },
  { label: t('song.sortFrequent'), value: 'frequent' },
  { label: t('song.sortNewest'), value: 'newest' },
])

const displaySongs = computed(() =>
  library.genreSongs.map(s => ({
    ...s,
    _starred: s.id != null ? player.isSongStarred(s.id) : false,
  }))
)

function loadSongs() {
  const name = route.params.name
  if (!name) return
  const offset = (songPage.value - 1) * songPageSize.value
  library.loadGenreDetail(name, {
    letter: songLetter.value,
    sort: songSort.value,
    count: songPageSize.value,
    offset,
  })
}

function onSortChange() {
  if (songSort.value !== 'alphabetical') songLetter.value = null
  songPage.value = 1
  loadSongs()
}

function onLetterChange(letter) {
  songLetter.value = letter
  songPage.value = 1
  loadSongs()
}

function onPageSizeChange() {
  songPage.value = 1
  loadSongs()
}


onMounted(async () => {
  try {
    player.loadFavoriteIds()
    loadSongs()
  } catch (e) {
    console.error('[GenreDetail] 初始化失败:', e)
  }
})


function goBack() { router.back() }
function goGenres() { router.push('/player/genres') }


function playSong(song) {
  if (song.path) player.play(song)
}

function playAll() {
  const name = route.params.name
  if (!name) return
  library.loadGenreDetail(name, { sort: 'alphabetical', count: 500, offset: 0 }).then(() => {
    const first = library.genreSongs[0]
    if (first?.path) player.play(first)
  })
}

function shuffleAll() {
  const name = route.params.name
  if (!name) return
  library.loadGenreDetail(name, { sort: 'alphabetical', count: 500, offset: 0 }).then(() => {
    const songs = library.genreSongs
    if (!songs.length) return
    const idx = Math.floor(Math.random() * songs.length)
    if (songs[idx]?.path) player.play(songs[idx])
  })
}

function addToQueue(song) {
  if (song) player.addToQueue([song])
}


async function toggleSongFav(song) {
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

function onRateSong({ songId, rating }) {
  const list = library.genreSongs
  const idx = list.findIndex(s => Number(s.id) === songId)
  if (idx >= 0) {
    list[idx] = { ...list[idx], userRating: rating }
  }
}


const showAddModal = ref(false)
const addSongIds = ref([])

function onAddToPlaylist(song) {
  addSongIds.value = [Number(song.id)]
  showAddModal.value = true
}

function onAddedToPlaylist() {
  }
</script>

<style scoped>
.genre-detail-page {
  width: min(100% - var(--content-padding-x) * 2, var(--content-max-width));
  margin: 0 auto;
  padding: 32px 0 48px;
}


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
.breadcrumb-current {
  color: var(--ct-text);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}


.detail-hero {
  display: flex;
  gap: 32px;
  align-items: flex-start;
  padding-bottom: 24px;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--ct-border);
}

.hero-cover {
  width: 200px;
  height: 200px;
  flex-shrink: 0;
}

.hero-info {
  flex: 1;
  min-width: 0;
}

.hero-name-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}

.hero-info h1 {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 4px;
  color: var(--ct-text);
  letter-spacing: -0.5px;
}


.hero-meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.meta-tag {
  display: inline-block;
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 5px;
  color: var(--ct-text-3);
  background: var(--ct-bg-secondary);
}
.meta-tag.count {
  color: var(--ct-accent);
  background: rgb(var(--ct-accent-rgb) / 0.1);
}


.hero-intro {
  margin-top: 8px;
  padding: 10px 14px;
  border-radius: 8px;
  background: var(--ct-bg-secondary);
  border: 1px solid var(--ct-border);
  max-width: 560px;
}
.hero-intro p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--ct-text-2);
  white-space: pre-wrap;
  word-break: break-word;
}
.intro-clamped {
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.intro-toggle {
  display: inline-block;
  margin-top: 4px;
  padding: 0;
  border: none;
  background: none;
  color: var(--ct-accent);
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
}
.intro-toggle:hover {
  text-decoration: underline;
}


.detail-songs {
  margin-top: 24px;
}


.tab-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0 4px;
  border-bottom: 1px solid var(--ct-border);
  margin-bottom: 8px;
}

.tab-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--ct-text);
}


.letter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  padding: 6px 0 12px;
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
.letter-chip:hover {
  border-color: var(--ct-accent);
  color: var(--ct-accent);
}
.letter-chip.active {
  background: var(--ct-accent);
  border-color: var(--ct-accent);
  color: #fff;
}


.detail-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
