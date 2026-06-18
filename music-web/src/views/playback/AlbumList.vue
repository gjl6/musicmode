<template>
  <div class="album-list-page">

    <div class="page-header">
      <div class="header-left">
        <h1>{{ $t('album.title') }}</h1>
        <span class="header-count">{{ $t('album.count', { count: library.albumTotal }) }}</span>
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
      >{{ $t('album.all') }}</button>
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

      <div v-if="view === 'grid' && displayAlbums.length" class="album-grid">
        <AlbumCard
          v-for="album in displayAlbums"
          :key="album.id"
          :album="album"
          :size="160"
          :starred="player.isAlbumStarred(album.id)"
          :show-fav="true"
          @click="goDetail(album.id)"
          @play="playAlbum(album)"
          @toggle-fav="toggleFav(album)"
        />
      </div>


      <AlbumTable
        v-if="view === 'list' && displayAlbums.length"
        :albums="displayAlbums"
        :loading="library.loading"
        :page="currentPage"
        :page-size="pageSize"
        :is-starred="(id) => player.isAlbumStarred(id)"
        @play="playAlbum"
        @toggle-fav="toggleFav"
      />


      <n-empty
        v-if="!library.loading && !displayAlbums.length"
        :description="$t('player.noData')"
        size="small"
        style="margin-top:48px"
      />
    </n-spin>


    <n-pagination
      v-if="library.albumTotal > 0"
      v-model:page="currentPage"
      v-model:page-size="pageSize"
      :page-sizes="[12, 24, 36, 48]"
      :item-count="library.albumTotal"
      size="small"
      show-size-picker
      class="album-pagination"
      @update:page="onPageChange"
      @update:page-size="onPageSizeChange"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useMessage } from 'naive-ui'
import { useLibraryStore } from '@/store/playback/library.js'
import { usePlayerStore } from '@/store/playback/player.js'
import { subsonicGetCoverArtUrl } from '@/api/playback/subsonic.js'
import { getAlbumSongs } from '@/api/playback/album.js'
import AlbumCard from '@/components/playback/AlbumCard.vue'
import AlbumTable from '@/components/playback/AlbumTable.vue'
import ViewToggle from '@/components/playback/ViewToggle.vue'

const { t } = useI18n()
const router = useRouter()
const message = useMessage()
const library = useLibraryStore()
const player = usePlayerStore()


const view = computed({
  get: () => library.currentView,
  set: (v) => { library.setView(v) },
})

const currentPage = ref(1)
const currentLetter = ref(null)
const pageSize = ref(24)

const sortType = computed({
  get: () => library.albumSortType,
  set: (v) => { library.albumSortType = v },
})

const sortOptions = computed(() => [
  { label: t('album.newest'), value: 'newest' },
  { label: t('album.alphabetical'), value: 'alphabetical' },
  { label: t('album.random'), value: 'random' },
  { label: t('album.byYear'), value: 'byYear' },
  { label: t('album.recentlyPlayed'), value: 'recent' },
])


const albums = computed(() => library.albums)

const displayAlbums = computed(() =>
  albums.value.map(a => ({
    ...a,
    _starred: a.id != null ? player.isAlbumStarred(a.id) : false,
  }))
)


const letterChips = computed(() => {
  const list = library.albumLetters
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


onMounted(async () => {
  player.loadFavoriteIds()
  await library.loadAlbumLetters()
  if (library.albums.length === 0) {
    doLoad()
  }
})

function doLoad() {
  const offset = (currentPage.value - 1) * pageSize.value
  library.loadAlbums({
    sort: sortType.value,
    letter: sortType.value === 'alphabetical' ? currentLetter.value : null,
    limit: pageSize.value,
    offset,
  })
}

function onSortChange() {
  currentPage.value = 1
    if (sortType.value !== 'alphabetical') {
    currentLetter.value = null
  }
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


function getCoverUrl(album) {
  const artId = album.coverArt || album.id
  return artId ? subsonicGetCoverArtUrl(artId, 80) : ''
}

async function playAlbum(album) {
  if (!album?.id) return
  try {
    const res = await getAlbumSongs(album.id)
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

async function toggleFav(album) {
  const id = album?.id
  if (id == null) return
  const wasStarred = player.isAlbumStarred(id)
  try {
    if (wasStarred) {
      await player.unstarAlbum(id)
    } else {
      await player.starAlbum(id)
    }
    message.success(wasStarred ? t('album.unfavorited') : t('album.favorited'))
  } catch {
    message.warning(t('album.favoriteFailed'))
  }
}

function goDetail(id) {
  router.push(`/player/albums/${id}`)
}
</script>

<style scoped>


.album-list-page {
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


.album-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}


.album-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
