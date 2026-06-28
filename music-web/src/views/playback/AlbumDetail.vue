<template>
  <div class="album-detail-page">
    <!-- ═══ 面包屑导航 ═══ -->
    <div class="breadcrumb">
      <n-button text class="back-btn" @click="goBack">
        <template #icon><n-icon :size="18"><ChevronBackOutline /></n-icon></template>
      </n-button>
      <span class="breadcrumb-sep">/</span>
      <span
        v-if="album?.artistId"
        class="breadcrumb-link"
        @click="goArtist(album.artistId)"
      >{{ album.artist || '—' }}</span>
      <span v-else class="breadcrumb-text">{{ album?.artist || '—' }}</span>
      <span class="breadcrumb-sep">/</span>
      <span class="breadcrumb-current">{{ album?.name || '—' }}</span>
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

    <n-spin :show="!album" size="medium">
      <template v-if="album">
        <!-- ═══ Hero ═══ -->
        <div class="detail-hero">
          <div class="hero-cover">
            <CoverArt
              :src="coverUrl"
              :alt="album.name || ''"
              :size="200"
            />
          </div>
          <div class="hero-info">
            <div class="hero-name-row">
              <h1>{{ album.name || '—' }}</h1>
              <n-button
                class="hero-fav-btn"
                text
                :title="isStarred ? '取消收藏' : '收藏'"
                @click="toggleFav"
              >
                <template #icon>
                  <n-icon :size="22" :color="isStarred ? '#EF4444' : undefined">
                    <Heart v-if="isStarred" />
                    <HeartOutline v-else />
                  </n-icon>
                </template>
              </n-button>
            </div>
            <p
              v-if="album.artistId"
              class="hero-artist link"
              @click="goArtist(album.artistId)"
            >{{ album.artist || '—' }}</p>
            <p v-else class="hero-artist">{{ album.artist || '—' }}</p>

            <!-- 元数据标签 -->
            <div class="hero-meta">
              <span v-if="album.genre" class="meta-tag genre">{{ album.genre }}</span>
              <span v-if="album.year > 0" class="meta-tag year">{{ album.year }}</span>
              <span v-if="album.language" class="meta-tag lang">{{ album.language }}</span>
              <span v-if="album.company" class="meta-tag company">{{ album.company }}</span>
              <span class="meta-tag count">{{ $t('album.songCount', { count: album.songCount || 0 }) }}</span>
            </div>

            <!-- 简介 -->
            <div v-if="album.introduction" class="hero-intro">
              <p :class="{ 'intro-clamped': !introExpanded }">{{ album.introduction }}</p>
              <button
                v-if="album.introduction.length > 150"
                class="intro-toggle"
                @click="introExpanded = !introExpanded"
              >{{ introExpanded ? '收起' : '展开' }}</button>
            </div>

            <!-- 操作按钮 -->
            <n-space style="margin-top:12px">
              <n-button type="primary" size="small" @click="playAll">
                <n-icon :size="16"><PlayOutline /></n-icon>
                {{ $t('album.playAll') }}
              </n-button>
              <n-button size="small" @click="shuffleAll">
                <n-icon :size="16"><ShuffleOutline /></n-icon>
                {{ $t('album.shuffle') }}
              </n-button>
              <n-button size="small" @click="showEditModal = true">
                <n-icon :size="15"><CreateOutline /></n-icon>
                编辑
              </n-button>
            </n-space>
          </div>
        </div>

        <!-- ═══ 歌曲列表 ═══ -->
        <div class="detail-songs">
          <div class="songs-header">
            <span class="songs-header-title">歌曲 ({{ albumSongsMapped.length }})</span>
            <ViewToggle v-model="songViewMode" />
          </div>
          <template v-if="albumSongsMapped.length">
            <SongTable
              v-if="songViewMode === 'list'"
              :songs="albumSongsMapped"
              @play="playSong"
              @addToQueue="addToQueue"
              @toggleFav="toggleSongFav"
              @rate="onRateSong"
              @addToPlaylist="onAddToPlaylist"
            />
            <SongGrid
              v-else
              :songs="albumSongsMapped"
              @play="playSong"
              @addToQueue="addToQueue"
              @toggleFav="toggleSongFav"
              @rate="onRateSong"
              @addToPlaylist="onAddToPlaylist"
            />
          </template>
          <n-empty v-else :description="$t('player.noData')" size="small" style="margin-top:32px" />
        </div>
      </template>
    </n-spin>

    <!-- ═══ 编辑弹窗 ═══ -->
    <n-modal
      v-model:show="showEditModal"
      preset="card"
      title="编辑专辑"
      style="width:480px"
      :mask-closable="false"
    >
      <n-form ref="editFormRef" :model="editForm" label-placement="top" size="small">
        <n-form-item label="专辑名">
          <n-input v-model:value="editForm.name" />
        </n-form-item>
        <n-form-item label="类型">
          <n-select
            v-model:value="editForm.genre"
            :options="genreOptions"
          />
        </n-form-item>
        <n-form-item label="年份">
          <n-input-number v-model:value="editForm.year" :min="0" :max="2099" style="width:100%" />
        </n-form-item>
        <n-form-item label="语言">
          <n-input v-model:value="editForm.language" placeholder="如：国语、英语、日语" />
        </n-form-item>
        <n-form-item label="唱片公司">
          <n-input v-model:value="editForm.company" />
        </n-form-item>
        <n-form-item label="简介">
          <n-input
            v-model:value="editForm.introduction"
            type="textarea"
            :rows="4"
            placeholder="专辑简介..."
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button size="small" @click="showEditModal = false">取消</n-button>
          <n-button type="primary" size="small" :loading="saving" @click="doSave">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- ═══ 添加到歌单弹窗 ═══ -->
    <AddToPlaylistModal
      :show="showAddModal"
      :song-ids="addSongIds"
      @update:show="(v) => { showAddModal = v; if (!v) addSongIds = [] }"
      @added="onAddedToPlaylist"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PlayOutline, ShuffleOutline, HeartOutline, Heart, CreateOutline, ChevronBackOutline, SearchOutline } from '@vicons/ionicons5'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useLibraryStore } from '@/store/playback/library.js'
import { usePlayerStore } from '@/store/playback/player.js'
import { subsonicGetCoverArtUrl } from '@/api/playback/subsonic.js'
import { updateAlbum, getAlbum } from '@/api/playback/album.js'
import CoverArt from '@/components/playback/CoverArt.vue'
import SongTable from '@/components/playback/SongTable.vue'
import SongGrid from '@/components/playback/SongGrid.vue'
import ViewToggle from '@/components/playback/ViewToggle.vue'
import AddToPlaylistModal from '@/components/playback/AddToPlaylistModal.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const searchText = ref('')

function doSearch() {
  const q = searchText.value.trim()
  if (!q) return
  router.push(`/player/search?q=${encodeURIComponent(q)}`)
}

const message = useMessage()
const library = useLibraryStore()
const player = usePlayerStore()

const album = computed(() => library.albumDetail)

const coverUrl = computed(() => {
  const artId = album.value?.coverArt || album.value?.id
  return artId ? subsonicGetCoverArtUrl(artId, 200) : ''
})

const albumSongsMapped = computed(() =>
  library.albumSongs.map(s => ({
    ...s,
    artist: s.displayArtist || s.artist || album.value?.artist || '',
    album: album.value?.name || '',
    coverArt: s.coverArt || album.value?.coverArt || album.value?.id,
    _starred: s.id != null ? player.isSongStarred(s.id) : false,
  }))
)

const isStarred = computed(() => {
  const id = album.value?.id
  return id != null ? player.isAlbumStarred(id) : false
})

onMounted(() => loadAlbumData())
watch(() => route.params.id, (newId, oldId) => {
  if (newId && newId !== oldId) loadAlbumData()
})

function loadAlbumData() {
  const id = route.params.id
  if (id) {
    library.loadAlbum(id)
    player.loadFavoriteIds()
  }
}

// ═══ 导航 ═══

function goBack() { router.back() }
function goArtist(artistId) {
  if (artistId) router.push(`/player/artists/${artistId}`)
}

// ═══ 播放 ═══

function playSong(song) {
  if (song.path) player.play(song)
}

function playAll() {
  const first = library.albumSongs[0]
  if (first?.path) player.play(first)
}

function shuffleAll() {
  const songs = library.albumSongs
  if (!songs.length) return
  const idx = Math.floor(Math.random() * songs.length)
  const song = songs[idx]
  if (song?.path) player.play(song)
}

function addToQueue(song) {
  if (song) player.addToQueue([song])
}

// ═══ 收藏 ═══

async function toggleFav() {
  const id = album.value?.id
  if (id == null) return
  const wasStarred = player.isAlbumStarred(id)
  try {
    if (wasStarred) {
      await player.unstarAlbum(id)
    } else {
      await player.starAlbum(id)
    }
    message.success(wasStarred ? t('player.unfavorited') : t('player.favorited'))
  } catch {
    message.warning(t('player.favoriteFailed'))
  }
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

// ═══ 评分 ═══

function onRateSong({ songId, rating }) {
  const list = library.albumSongs
  const idx = list.findIndex(s => Number(s.id) === songId)
  if (idx >= 0) {
    list[idx] = { ...list[idx], userRating: rating }
  }
}

// ═══ 添加到歌单 ═══

const showAddModal = ref(false)
const addSongIds = ref([])

function onAddToPlaylist(song) {
  addSongIds.value = [Number(song.id)]
  showAddModal.value = true
}

function onAddedToPlaylist() {}

// ═══ 编辑 ═══

const showEditModal = ref(false)
const saving = ref(false)
const introExpanded = ref(false)
const songViewMode = ref('list')

const genreOptions = [
  { label: '录音室专辑', value: 'ALBUM' },
  { label: '单曲', value: 'SINGLE' },
  { label: 'EP', value: 'EP' },
  { label: '精选集', value: 'COMPILATION' },
  { label: '原声带', value: 'SOUNDTRACK' },
]

const editForm = ref({
  name: '',
  genre: null,
  year: null,
  language: '',
  company: '',
  introduction: '',
})

watch(showEditModal, (v) => {
  if (v && album.value) {
    editForm.value = {
      name: album.value.name || '',
      genre: album.value.albumType || null,
      year: album.value.year > 0 ? album.value.year : null,
      language: album.value.language || '',
      company: album.value.company || '',
      introduction: album.value.introduction || '',
    }
  }
})

async function doSave() {
  saving.value = true
  try {
    const res = await updateAlbum(album.value.id, editForm.value)
    if (res?.album) {
      library.albumDetail = res.album
      message.success('保存成功')
      showEditModal.value = false
    }
  } catch {
    message.warning('保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.album-detail-page {
  width: min(100% - var(--content-padding-x) * 2, var(--content-max-width));
  margin: 0 auto;
  padding: 32px 0 48px;
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
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── Hero ── */
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

.hero-fav-btn {
  flex-shrink: 0;
  color: var(--ct-text-3) !important;
  transition: transform 0.15s;
}
.hero-fav-btn:hover { transform: scale(1.15); }

.hero-info h1 {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 4px;
  color: var(--ct-text);
  letter-spacing: -0.5px;
}

.hero-artist {
  font-size: 14px;
  color: var(--ct-text-2);
  margin: 0 0 8px;
}
.hero-artist.link {
  color: var(--ct-accent);
  cursor: pointer;
}
.hero-artist.link:hover {
  text-decoration: underline;
}

/* ── 元数据标签 ── */
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
.meta-tag.genre { color: var(--ct-accent); background: rgb(var(--ct-accent-rgb) / 0.1); }
.meta-tag.year  { color: var(--ct-accent); background: rgb(var(--ct-accent-rgb) / 0.1); letter-spacing: 0.5px; }

/* ── 简介 ── */
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

/* ── 歌曲列表 ── */
.detail-songs {
  margin-top: 24px;
}

.songs-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.songs-header-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ct-text);
}
</style>
