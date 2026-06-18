<template>
  <div class="song-list-page">

    <div class="page-header">
      <div class="header-left">
        <h1>{{ $t('song.title') }}</h1>
        <span class="header-count">{{ $t('song.count', { count: library.songTotal }) }}</span>
      </div>
      <div class="header-actions">
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

      <div v-if="view === 'grid' && displaySongs.length" class="song-grid">
        <div
          v-for="song in displaySongs"
          :key="song.id"
          class="song-card"
          @click="goDetail(song.id)"
        >
          <div class="song-card-cover">
            <img
              v-if="getCoverUrl(song)"
              :src="getCoverUrl(song)"
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
                @click.stop="playSong(song)"
              >
                <template #icon>
                  <n-icon :size="24"><PlayOutline /></n-icon>
                </template>
              </n-button>
              <n-button
                circle
                class="card-fav-btn"
                size="tiny"
                :type="song._starred ? 'error' : 'tertiary'"
                @click.stop="toggleFav(song)"
              >
                <template #icon>
                  <n-icon :size="15" :color="song._starred ? '#EF4444' : undefined">
                    <Heart v-if="song._starred" />
                    <HeartOutline v-else />
                  </n-icon>
                </template>
              </n-button>
            </div>
          </div>
          <div class="song-card-body">
            <span class="song-card-title" :title="song.title">{{ song.title || '—' }}</span>
            <span class="song-card-artist" :title="song.artist">{{ song.artist || '—' }}</span>
            <StarRatingComp
              :rating="Number(song.userRating) || 0"
              :song-id="song.id"
              :size="13"
              @rated="(val) => onRateSong({ songId: Number(song.id), rating: val })"
              @click.stop
            />
          </div>
          <div class="song-card-meta">
            <span v-if="song.suffix" class="song-card-format">{{ song.suffix.toUpperCase() }}</span>
            <span class="song-card-duration">{{ formatDuration(song.duration) }}</span>
            <span v-if="song.bitRate" class="song-card-bitrate">{{ song.bitRate }} kbps</span>
          </div>
        </div>
      </div>


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


      <n-empty
        v-if="!library.loading && !displaySongs.length"
        :description="$t('player.noData')"
        size="small"
        style="margin-top:48px"
      />
    </n-spin>


    <AddToPlaylistModal
      :show="showAddModal"
      :song-ids="addSongIds"
      @update:show="(v) => { showAddModal = v; if (!v) addSongIds = [] }"
      @added="onAddedToPlaylist"
    />


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
import { PlayOutline, MusicalNotesOutline, HeartOutline, Heart } from '@vicons/ionicons5'
import { useMessage } from 'naive-ui'
import { useLibraryStore } from '@/store/playback/library.js'
import { usePlayerStore } from '@/store/playback/player.js'
import { subsonicGetCoverArtUrl } from '@/api/playback/subsonic.js'
import SongTable from '@/components/playback/SongTable.vue'
import ViewToggle from '@/components/playback/ViewToggle.vue'
import StarRatingComp from '@/components/playback/StarRating.vue'
import AddToPlaylistModal from '@/components/playback/AddToPlaylistModal.vue'

const { t } = useI18n()
const router = useRouter()
const message = useMessage()
const library = useLibraryStore()
const player = usePlayerStore()


const view = computed({
  get: () => library.currentView,
  set: (v) => { library.setView(v) },
})
const pageSize = ref(24)

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


const songs = computed(() => library.songs)


const displaySongs = computed(() => {
  const fav = player.favoriteIds
  return songs.value.map(s => ({
    ...s,
    _starred: s.id != null ? fav.has(Number(s.id)) : false,
  }))
})


const letterChips = computed(() => {
  const map = new Map()
  for (const L of library.songLetters) {
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


onMounted(async () => {
  await library.loadSongLetters()
    if (library.songs.length === 0) {
    doLoad()
  }
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


function getCoverUrl(song) {
  const artId = song.coverArt || song.albumId || song.id
  return artId ? subsonicGetCoverArtUrl(artId, 160) : ''
}

function formatDuration(s) {
  if (!s || !isFinite(s)) return '—'
  const m = Math.floor(s / 60)
  const sec = Math.floor(s % 60)
  return `${m}:${String(sec).padStart(2, '0')}`
}

function playSong(song) {
  if (song?.id || song?.path) player.play(song)
}

function addToQueue(song) {
  if (song) player.addToQueue([song])
}

function goDetail(id) {
  if (id) router.push(`/player/songs/${id}`)
}

function isFavorited(id) {
  return player.isSongStarred(id)
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


function onRateSong({ songId, rating }) {
  const list = library.songs
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


.song-list-page {
  width: min(100% - var(--content-padding-x) * 2, var(--content-max-width));
  margin: 0 auto;
  padding: 24px 0;
}


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


.song-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}


.song-card {
  display: flex;
  flex-direction: column;
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


.card-play-btn {
  background: rgba(255, 255, 255, 0.92) !important;
  color: var(--ct-accent) !important;
  border: none !important;
  width: 46px !important;
  height: 46px !important;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
  transition: transform 0.15s, box-shadow 0.15s;
}
.card-play-btn:hover {
  transform: scale(1.08);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.35);
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


.song-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
