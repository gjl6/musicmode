<template>
  <div class="search-results-page">
    <!-- ═══ 页头 ═══ -->
    <div class="page-header">
      <n-button text class="back-btn" @click="$router.back()">
        <template #icon><n-icon :size="18"><ChevronBackOutline /></n-icon></template>
      </n-button>
      <div class="header-input">
        <n-input
          v-model:value="searchText"
          :placeholder="$t('player.searchPlaceholder')"
          size="large"
          clearable
          round
          @keyup.enter="doSearch"
        >
          <template #prefix>
            <n-icon :size="20"><SearchOutline /></n-icon>
          </template>
        </n-input>
      </div>
    </div>

    <!-- ═══ 结果 Tabs ═══ -->
    <n-spin :show="loading" size="medium">
      <n-tabs
        v-model:value="activeTab"
        type="line"
        animated
        class="search-tabs"
      >
        <template #suffix>
          <ViewToggle v-model="view" />
        </template>

        <!-- ═══ 歌曲 ═══ -->
        <n-tab-pane name="song" :tab="songTabLabel">
          <div v-if="view === 'grid' && songs.length" class="result-grid">
            <SongCard
              v-for="song in songs"
              :key="song.id"
              :song="song"
              :starred="player.isSongStarred(song.id)"
              @play="playSong(song)"
              @toggle-fav="toggleFav(song)"
              @rate="onRateSong"
            />
          </div>
          <SongTable
            v-else-if="view === 'list' && songs.length"
            :songs="songs"
            :loading="loading"
            @play="playSong"
            @add-to-queue="addToQueue"
            @toggle-fav="toggleFav"
            @rate="onRateSong"
            @add-to-playlist="onAddToPlaylist"
          />
          <n-empty v-else-if="!loading" :description="$t('player.noData')" size="small" style="margin-top:48px" />
        </n-tab-pane>

        <!-- ═══ 专辑 ═══ -->
        <n-tab-pane name="album" :tab="albumTabLabel">
          <div v-if="view === 'grid' && albums.length" class="result-grid">
            <AlbumCard
              v-for="album in albums"
              :key="album.id"
              :album="album"
              :size="160"
              :starred="player.isAlbumStarred(album.id)"
              :show-fav="true"
              @click="goAlbum(album.id)"
              @play="playAlbum(album)"
              @toggle-fav="toggleAlbumFav(album)"
            />
          </div>
          <AlbumTable
            v-else-if="view === 'list' && albums.length"
            :albums="albums"
            :loading="loading"
            :page="1"
            :page-size="albums.length"
            :is-starred="(id) => player.isAlbumStarred(id)"
            @play="playAlbum"
            @toggle-fav="toggleAlbumFav"
          />
          <n-empty v-else-if="!loading" :description="$t('player.noData')" size="small" style="margin-top:48px" />
        </n-tab-pane>

        <!-- ═══ 艺术家 ═══ -->
        <n-tab-pane name="artist" :tab="artistTabLabel">
          <div v-if="view === 'grid' && artists.length" class="result-grid">
            <ArtistCard
              v-for="artist in artists"
              :key="artist.id"
              :artist="artist"
              :size="160"
              :starred="player.isArtistStarred(artist.id)"
              :show-fav="true"
              @click="goArtist(artist.id)"
              @toggle-fav="toggleArtistFav(artist)"
            />
          </div>
          <ArtistTable
            v-else-if="view === 'list' && artists.length"
            :artists="artists"
            :page="1"
            :page-size="artists.length"
            :is-starred="(id) => player.isArtistStarred(id)"
            @toggle-fav="toggleArtistFav"
          />
          <n-empty v-else-if="!loading" :description="$t('player.noData')" size="small" style="margin-top:48px" />
        </n-tab-pane>

        <!-- ═══ 歌词 ═══ -->
        <n-tab-pane name="lyric" :tab="lyricTabLabel">
          <div v-if="view === 'grid' && lyricSongs.length" class="result-grid">
            <SongCard
              v-for="song in lyricSongs"
              :key="song.id"
              :song="song"
              :starred="player.isSongStarred(song.id)"
              @play="playSong(song)"
              @toggle-fav="toggleFav(song)"
              @rate="onRateLyricSong"
            />
          </div>
          <SongTable
            v-else-if="view === 'list' && lyricSongs.length"
            :songs="lyricSongs"
            :loading="loading"
            @play="playSong"
            @add-to-queue="addToQueue"
            @toggle-fav="toggleFav"
            @rate="onRateLyricSong"
            @add-to-playlist="onAddToPlaylist"
          />
          <n-empty v-else-if="!loading" :description="$t('player.noData')" size="small" style="margin-top:48px" />
        </n-tab-pane>

        <!-- ═══ 歌单 ═══ -->
        <n-tab-pane name="playlist" :tab="playlistTabLabel">
          <div v-if="playlists.length" class="result-grid">
            <PlaylistCard
              v-for="pl in playlists"
              :key="pl.id"
              :playlist="pl"
              :size="180"
              @click="goPlaylist(pl.id)"
              @play="playPlaylist(pl)"
              @toggle-fav="togglePlaylistFav(pl)"
            />
          </div>
          <n-empty v-else-if="!loading" :description="$t('player.noData')" size="small" style="margin-top:48px" />
        </n-tab-pane>
      </n-tabs>
    </n-spin>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useMessage } from 'naive-ui'
import { SearchOutline, ChevronBackOutline } from '@vicons/ionicons5'
import { searchAll } from '@/api/playback/search.js'
import { getAlbumSongs } from '@/api/playback/album.js'
import { getPlaylist } from '@/api/playback/playlist.js'
import { usePlayerStore } from '@/store/playback/player.js'
import SongCard from '@/components/playback/SongCard.vue'
import SongTable from '@/components/playback/SongTable.vue'
import AlbumCard from '@/components/playback/AlbumCard.vue'
import AlbumTable from '@/components/playback/AlbumTable.vue'
import ArtistCard from '@/components/playback/ArtistCard.vue'
import ArtistTable from '@/components/playback/ArtistTable.vue'
import PlaylistCard from '@/components/playback/PlaylistCard.vue'
import ViewToggle from '@/components/playback/ViewToggle.vue'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const message = useMessage()
const player = usePlayerStore()

// ═══ 状态 ═══

const loading = ref(false)
const searchText = ref('')
const activeTab = ref('song')
const view = ref('grid')

const songs     = ref([])
const albums    = ref([])
const artists   = ref([])
const lyricSongs = ref([])
const playlists = ref([])

// ═══ Tab 标签 ═══

const songTabLabel     = computed(() => `${t('song.title')} (${songs.value.length})`)
const albumTabLabel    = computed(() => `${t('album.title')} (${albums.value.length})`)
const artistTabLabel   = computed(() => `${t('artist.title')} (${artists.value.length})`)
const lyricTabLabel    = computed(() => `歌词 (${lyricSongs.value.length})`)
const playlistTabLabel = computed(() => `歌单 (${playlists.value.length})`)

// ═══ 数据加载 ═══

onMounted(() => {
  const q = route.query.q || ''
  if (q) {
    searchText.value = q
    loadResults(q)
  }
  player.loadFavoriteIds()
})

watch(() => route.query.q, (newQ) => {
  if (newQ && newQ !== searchText.value) {
    searchText.value = newQ
    loadResults(newQ)
  }
})

function doSearch() {
  const q = searchText.value.trim()
  if (!q) return
  router.replace({ query: { q } })
  loadResults(q)
}

async function loadResults(query) {
  loading.value = true
  try {
    const res = await searchAll(query)
    songs.value      = res.songs
    albums.value     = res.albums
    artists.value    = res.artists
    lyricSongs.value = res.lyrics
    playlists.value  = res.playlists
    // 自动切到有结果的 tab
    if (songs.value.length > 0) {
      activeTab.value = 'song'
    } else if (albums.value.length > 0) {
      activeTab.value = 'album'
    } else if (artists.value.length > 0) {
      activeTab.value = 'artist'
    } else if (lyricSongs.value.length > 0) {
      activeTab.value = 'lyric'
    } else if (playlists.value.length > 0) {
      activeTab.value = 'playlist'
    }
  } catch (e) {
    console.error('[search] 搜索失败:', e)
    message.warning(t('player.loadError'))
  } finally {
    loading.value = false
  }
}

// ═══ 操作 ═══

// ── 歌曲 ──
function playSong(song) {
  if (song?.id || song?.path || song?.filePath) player.play(song)
}

async function toggleFav(song) {
  const id = song?.id
  if (id == null) return
  try {
    if (player.isSongStarred(id)) {
      await player.unstarSong(id)
    } else {
      await player.starSong(id)
    }
  } catch { /* ignore */ }
}

function onRateSong({ songId, rating }) {
  const idx = songs.value.findIndex(s => Number(s.id) === songId)
  if (idx >= 0) songs.value[idx] = { ...songs.value[idx], userRating: rating }
}

function onRateLyricSong({ songId, rating }) {
  const idx = lyricSongs.value.findIndex(s => Number(s.id) === songId)
  if (idx >= 0) lyricSongs.value[idx] = { ...lyricSongs.value[idx], userRating: rating }
}

function addToQueue(song) {
  if (song) player.addToQueue([song])
}

function onAddToPlaylist(song) {
  message.info(t('player.comingSoon'))
}

// ── 专辑 ──
async function playAlbum(album) {
  if (!album?.id) return
  try {
    const res = await getAlbumSongs(album.id)
    const list = res?.songs || []
    if (list.length > 0) {
      player.playAll(list, 0)
    } else {
      message.warning(t('player.noData'))
    }
  } catch {
    message.warning(t('player.loadError'))
  }
}

async function toggleAlbumFav(album) {
  const id = album?.id
  if (id == null) return
  try {
    if (player.isAlbumStarred(id)) {
      await player.unstarAlbum(id)
    } else {
      await player.starAlbum(id)
    }
  } catch { /* ignore */ }
}

// ── 艺术家 ──
async function toggleArtistFav(artist) {
  const id = artist?.id
  if (id == null) return
  try {
    if (player.isArtistStarred(id)) {
      await player.unstarArtist(id)
    } else {
      await player.starArtist(id)
    }
  } catch { /* ignore */ }
}

// ── 歌词 ──
// (复用 playSong / toggleFav / addToQueue，操作的是 lyricSongs 中的歌曲)

// ── 歌单 ──
function goPlaylist(id) {
  router.push(`/player/playlists/${id}`)
}

async function playPlaylist(pl) {
  if (!pl?.id) return
  try {
    const res = await getPlaylist(pl.id)
    const list = res?.entries || res?.songs || []
    if (list.length > 0) {
      player.playAll(list, 0)
    } else {
      message.warning(t('player.noData'))
    }
  } catch {
    message.warning(t('player.loadError'))
  }
}

async function togglePlaylistFav(pl) {
  // TODO: 歌单收藏功能（待后端支持）
  message.info(t('player.comingSoon'))
}

function goAlbum(id) {
  router.push(`/player/albums/${id}`)
}

function goArtist(id) {
  router.push(`/player/artists/${id}`)
}
</script>

<style scoped>
.search-results-page {
  width: min(100% - var(--content-padding-x) * 2, var(--content-max-width));
  margin: 0 auto;
  padding: 20px 0;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.back-btn {
  flex-shrink: 0;
  color: var(--ct-text-2) !important;
}

.header-input {
  flex: 1;
  max-width: 520px;
}

.search-tabs {
  margin-top: 4px;
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
  padding-top: 12px;
}
</style>
