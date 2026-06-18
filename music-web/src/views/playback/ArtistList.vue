<template>
  <div class="artist-list-page">

    <div class="page-header">
      <div class="header-left">
        <h1>{{ $t('artist.title') }}</h1>
        <span class="header-count">{{ $t('artist.count', { count: library.artistTotal }) }}</span>
      </div>
      <div class="header-actions">
        <n-select
          v-model:value="sortType"
          :options="sortOptions"
          size="small"
          :consistent-menu-width="false"
          style="width:110px"
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

      <div v-if="view === 'grid' && displayArtists.length" class="artist-grid">
        <ArtistCard
          v-for="artist in displayArtists"
          :key="artist.id"
          :artist="artist"
          :size="160"
          :starred="artist._starred"
          :show-fav="true"
          @click="goDetail(artist.id)"
          @toggle-fav="toggleFav(artist)"
        />
      </div>


      <ArtistTable
        v-if="view === 'list' && displayArtists.length"
        :artists="displayArtists"
        :page="currentPage"
        :page-size="pageSize"
        :is-starred="(id) => player.isArtistStarred(id)"
        @toggle-fav="toggleFav"
      />


      <n-empty
        v-if="!library.loading && !displayArtists.length"
        :description="$t('player.noData')"
        size="small"
        style="margin-top:48px"
      />
    </n-spin>


    <n-pagination
      v-if="library.artistTotal > 0"
      v-model:page="currentPage"
      v-model:page-size="pageSize"
      :page-sizes="[20, 40, 60, 80]"
      :item-count="library.artistTotal"
      size="small"
      show-size-picker
      class="artist-pagination"
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
import ArtistCard from '@/components/playback/ArtistCard.vue'
import ArtistTable from '@/components/playback/ArtistTable.vue'
import ViewToggle from '@/components/playback/ViewToggle.vue'

const { t } = useI18n()
const router = useRouter()
const message = useMessage()
const library = useLibraryStore()
const player = usePlayerStore()

const view = ref('grid')
const artists = computed(() => library.artists)


const displayArtists = computed(() =>
  artists.value.map(a => ({
    ...a,
    _starred: a.id != null ? player.isArtistStarred(a.id) : false,
  }))
)


const sortType = ref('alphabetical')
const sortOptions = computed(() => [
  { label: t('artist.sortByName'), value: 'alphabetical' },
  { label: t('artist.sortByAlbumCount'), value: 'albumCount' },
  { label: t('artist.sortBySongCount'), value: 'songCount' },
])

function onSortChange() {
    if (sortType.value !== 'alphabetical') {
    currentLetter.value = null
  }
  currentPage.value = 1
  doLoad()
}


const currentLetter = ref(null)


const letterChips = computed(() => {
  const list = library.artistLetters
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

function onLetterChange(letter) {
  currentLetter.value = letter
  currentPage.value = 1
  doLoad()
}


const currentPage = ref(1)
const pageSize = ref(40)

function onPageChange() {
  doLoad()
}

function onPageSizeChange() {
  currentPage.value = 1
  doLoad()
}


function doLoad() {
  const offset = (currentPage.value - 1) * pageSize.value
  library.loadArtists({
    letter: sortType.value === 'alphabetical' ? currentLetter.value : null,
    sort: sortType.value,
    count: pageSize.value,
    offset,
  })
}

onMounted(async () => {
  player.loadFavoriteIds()
  if (library.artistLetters.length === 0) {
        library.loadArtists({ count: pageSize.value, offset: 0 })
  }
  if (library.artists.length === 0) {
    doLoad()
  }
})


function goDetail(id) {
  router.push(`/player/artists/${id}`)
}

async function toggleFav(artist) {
  const id = artist?.id
  if (id == null) return
  const wasStarred = player.isArtistStarred(id)
  try {
    if (wasStarred) {
      await player.unstarArtist(id)
    } else {
      await player.starArtist(id)
    }
    message.success(wasStarred ? t('player.unfavorited') : t('player.favorited'))
  } catch {
    message.warning(t('player.favoriteFailed'))
  }
}
</script>

<style scoped>


.artist-list-page {
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


.artist-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}


.artist-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
