<template>
  <div class="player-home">

    <div class="home-hero">
      <div class="hero-text">
        <h1>{{ $t('dashboard.welcome') }}, {{ username }}</h1>
      </div>
      <div class="hero-search">
        <n-input
          v-model:value="searchQuery"
          :placeholder="$t('dashboard.quickSearch')"
          size="large"
          clearable
          round
          @keyup.enter="doSearch"
        >
          <template #prefix>
            <n-icon :size="18"><SearchOutline /></n-icon>
          </template>
        </n-input>
      </div>
    </div>

    <n-spin :show="library.loading" size="medium">

      <section class="section">
        <StatsGrid :items="stats" />
      </section>


      <section class="section">
        <div class="section-head">
          <h2>{{ $t('dashboard.recentlyAdded') }}</h2>
          <n-button text type="primary" @click="$router.push('/player/albums')">
            {{ $t('dashboard.viewAll') }}
          </n-button>
        </div>
        <div v-if="library.recentlyAdded.length" class="album-grid">
          <AlbumCard
            v-for="album in library.recentlyAdded.slice(0, 6)"
            :key="album.id"
            :album="album"
            :size="160"
            @click="goAlbum(album.id)"
          />
        </div>
        <n-empty v-else :description="$t('player.noData')" size="small" />
      </section>


      <section class="section">
        <div class="section-head">
          <h2>{{ $t('dashboard.randomPicks') }}</h2>
          <n-button text type="primary" @click="library.loadRandomSongs()">
            <n-icon :size="16"><RefreshOutline /></n-icon>
          </n-button>
        </div>
        <SongTable
          v-if="library.randomPicks.length"
          :songs="library.randomPicks"
          @play="playSong"
          @addToQueue="addToQueue"
        />
        <n-empty v-else :description="$t('player.noData')" size="small" />
      </section>
    </n-spin>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { SearchOutline, RefreshOutline, PeopleOutline, DiscOutline, MusicalNotesOutline, ListOutline } from '@vicons/ionicons5'
import { useLibraryStore } from '@/store/playback/library.js'
import { usePlayerStore } from '@/store/playback/player.js'
import { useAuthStore } from '@/store/auth.js'
import AlbumCard from '@/components/playback/AlbumCard.vue'
import SongTable from '@/components/playback/SongTable.vue'
import StatsGrid from '@/components/playback/StatsGrid.vue'

const { t } = useI18n()
const router = useRouter()
const library = useLibraryStore()
const player = usePlayerStore()
const auth = useAuthStore()

const searchQuery = ref('')

const username = computed(() => auth.user?.username || '')

const stats = computed(() => [
  { icon: PeopleOutline, value: library.artistCount, label: t('dashboard.stats.artists'), color: 'var(--n-color-success)' },
  { icon: DiscOutline, value: library.albumCount, label: t('dashboard.stats.albums'), color: 'var(--ct-accent)' },
  { icon: MusicalNotesOutline, value: library.songCount, label: t('dashboard.stats.songs'), color: 'var(--n-color-warning)' },
  { icon: ListOutline, value: library.playlistCount, label: t('dashboard.stats.playlists'), color: 'var(--n-color-error)' },
])

onMounted(() => {
  library.loadDashboard()
})

function goAlbum(id) {
  router.push(`/player/albums/${id}`)
}

function playSong(song) {
  if (song.path || song.filePath) player.play(song)
}

function addToQueue(song) {
  }

function doSearch() {
  if (!searchQuery.value.trim()) return
  library.search(searchQuery.value.trim()).then(result => {
        console.log('[search]', result)
  })
}
</script>

<style scoped>
.player-home {
  width: min(100% - var(--content-padding-x) * 2, var(--content-max-width));
  margin: 0 auto;
  padding: 24px 0;
}

.home-hero {
  margin-bottom: 32px;
}

.hero-text h1 {
  font-size: 22px;
  font-weight: 700;
  margin: 0 0 16px;
  color: var(--ct-text);
  letter-spacing: -0.5px;
}

.hero-search {
  max-width: 480px;
}

.section {
  margin-bottom: 32px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-head h2 {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  color: var(--ct-text);
  letter-spacing: -0.3px;
}

.album-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}
</style>
