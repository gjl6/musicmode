<template>
  <div class="artist-detail-page">
    <!-- ═══ 页面头部 ═══ -->
    <div class="page-header">
      <div class="header-left">
        <n-button text class="back-btn" @click="goBack">
          <template #icon><n-icon :size="18"><ChevronBackOutline /></n-icon></template>
        </n-button>
        <h1>{{ artist?.name || '—' }}</h1>
        <n-button
          class="hero-fav-btn"
          text
          :title="isStarred ? '取消收藏' : '收藏'"
          @click="toggleArtistFav"
        >
          <template #icon>
            <n-icon :size="22" :color="isStarred ? '#EF4444' : undefined">
              <Heart v-if="isStarred" />
              <HeartOutline v-else />
            </n-icon>
          </template>
        </n-button>
        <span class="header-count">{{ $t('artist.albumCount', { count: artist?.albumCount || 0 }) }} · {{ $t('artist.songCount', { count: artist?.songCount || 0 }) }}</span>
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
        <n-button size="small" @click="showEditModal = true">
          <n-icon :size="15"><CreateOutline /></n-icon>
          编辑
        </n-button>
      </div>
    </div>

    <n-spin :show="!artist" size="medium">
      <template v-if="artist">
        <!-- ═══ Hero 信息 ═══ -->
        <div class="detail-hero">
          <div class="hero-cover">
            <CoverArt
              :src="coverUrl"
              :alt="artist.name || ''"
              :size="180"
              type="artist"
            />
          </div>
          <div class="hero-info">
            <div class="hero-meta">
              <span v-if="genderLabel" class="meta-tag gender">{{ genderLabel }}</span>
              <span v-if="artist.country" class="meta-tag country">{{ artist.country }}</span>
            </div>
            <div v-if="artist.introduction" class="hero-intro">
              <p :class="{ 'intro-clamped': !introExpanded }">{{ artist.introduction }}</p>
              <button
                v-if="artist.introduction.length > 150"
                class="intro-toggle"
                @click="introExpanded = !introExpanded"
              >{{ introExpanded ? '收起' : '展开' }}</button>
            </div>
            <n-space style="margin-top:8px">
              <n-button type="primary" size="small" @click="playAll">
                <n-icon :size="16"><PlayOutline /></n-icon>
                播放全部
              </n-button>
              <n-button size="small" @click="shuffleAll">
                <n-icon :size="16"><ShuffleOutline /></n-icon>
                随机播放
              </n-button>
            </n-space>
          </div>
        </div>

        <!-- ═══ 标签切换 ═══ -->
        <div class="tab-bar">
          <div class="tab-btns">
            <button
              class="tab-btn"
              :class="{ active: activeTab === 'albums' }"
              @click="switchTab('albums')"
            >专辑 ({{ artist.albumCount || 0 }})</button>
            <button
              class="tab-btn"
              :class="{ active: activeTab === 'songs' }"
              @click="switchTab('songs')"
            >歌曲 ({{ artist.songCount || 0 }})</button>
          </div>
          <div v-if="activeTab === 'albums'" class="tab-actions">
            <n-select
              v-model:value="albumSort"
              :options="albumSortOptions"
              size="small"
              :consistent-menu-width="false"
              style="width:100px"
              @update:value="onSortChange"
            />
            <ViewToggle v-model="albumViewMode" />
          </div>
          <div v-if="activeTab === 'songs'" class="tab-actions">
            <n-select
              v-model:value="songSort"
              :options="songSortOptions"
              size="small"
              :consistent-menu-width="false"
              style="width:100px"
              @update:value="onSortChange"
            />
            <ViewToggle v-model="songViewMode" />
          </div>
        </div>

        <!-- ═══ 专辑视图 ═══ -->
        <div v-show="activeTab === 'albums'" class="tab-content">
          <div v-if="albumSort === 'alphabetical'" class="letter-bar">
            <button class="letter-chip" :class="{ active: albumLetter === null }" @click="onAlbumLetter(null)">{{ $t('album.all') }}</button>
            <button v-for="ch in letterChips" :key="ch" class="letter-chip" :class="{ active: albumLetter === ch }" @click="onAlbumLetter(ch)">{{ ch }}</button>
          </div>
          <n-spin :show="library.artistAlbumsLoading" size="small">
            <template v-if="displayAlbums.length">
              <div v-if="albumViewMode === 'grid'" class="album-grid">
                <AlbumCard
                  v-for="album in displayAlbums"
                  :key="album.id"
                  :album="album"
                  :size="160"
                  @click="goAlbum(album.id)"
                />
              </div>
              <AlbumTable
                v-else
                :albums="displayAlbums"
                :page="albumPage"
                :page-size="albumPageSize"
                :is-starred="(id) => player.isAlbumStarred(id)"
                @play="(album) => goAlbum(album.id)"
                @toggleFav="onAlbumToggleFav"
              />
            </template>
            <n-empty v-else :description="$t('player.noData')" size="small" style="margin-top:32px" />
          </n-spin>
          <n-pagination
            v-if="library.artistAlbumTotal > albumPageSize"
            v-model:page="albumPage"
            v-model:page-size="albumPageSize"
            :page-sizes="[20, 40, 60]"
            :item-count="library.artistAlbumTotal"
            size="small"
            show-size-picker
            class="tab-pagination"
            @update:page="loadAlbums"
            @update:page-size="onAlbumPageSizeChange"
          />
        </div>

        <!-- ═══ 歌曲视图 ═══ -->
        <div v-show="activeTab === 'songs'" class="tab-content">
          <div v-if="songSort === 'alphabetical'" class="letter-bar">
            <button class="letter-chip" :class="{ active: songLetter === null }" @click="onSongLetter(null)">{{ $t('song.all') }}</button>
            <button v-for="ch in letterChips" :key="ch" class="letter-chip" :class="{ active: songLetter === ch }" @click="onSongLetter(ch)">{{ ch }}</button>
          </div>
          <n-spin :show="library.artistSongsLoading" size="small">
            <template v-if="displaySongs.length">
              <SongTable
                v-if="songViewMode === 'list'"
                :songs="displaySongs"
                @play="playSong"
                @addToQueue="addToQueue"
                @toggleFav="toggleSongFav"
                @rate="onRateSong"
                @addToPlaylist="onAddToPlaylist"
              />
              <SongGrid
                v-else
                :songs="displaySongs"
                @play="playSong"
                @addToQueue="addToQueue"
                @toggleFav="toggleSongFav"
                @rate="onRateSong"
                @addToPlaylist="onAddToPlaylist"
              />
            </template>
            <n-empty v-else :description="$t('player.noData')" size="small" style="margin-top:32px" />
          </n-spin>
          <n-pagination
            v-if="library.artistSongTotal > songPageSize"
            v-model:page="songPage"
            v-model:page-size="songPageSize"
            :page-sizes="[20, 50, 100]"
            :item-count="library.artistSongTotal"
            size="small"
            show-size-picker
            class="tab-pagination"
            @update:page="loadSongs"
            @update:page-size="onSongPageSizeChange"
          />
        </div>
      </template>
    </n-spin>

    <!-- ═══ 编辑弹窗 ═══ -->
    <n-modal
      v-model:show="showEditModal"
      preset="card"
      title="编辑艺术家"
      style="width:480px"
      :mask-closable="false"
    >
      <n-form ref="editFormRef" :model="editForm" label-placement="top" size="small">
        <n-form-item label="名称">
          <n-input v-model:value="editForm.name" />
        </n-form-item>
        <n-form-item label="性别/类型">
          <n-select v-model:value="editForm.gender" :options="genderOptions" />
        </n-form-item>
        <n-form-item label="国家/地区">
          <n-input v-model:value="editForm.country" placeholder="如：中国、美国、英国" />
        </n-form-item>
        <n-form-item label="简介">
          <n-input v-model:value="editForm.introduction" type="textarea" :rows="5" placeholder="艺术家简介..." />
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
import { updateArtist } from '@/api/playback/artist.js'
import CoverArt from '@/components/playback/CoverArt.vue'
import AlbumCard from '@/components/playback/AlbumCard.vue'
import AlbumTable from '@/components/playback/AlbumTable.vue'
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

const artist = computed(() => library.artistDetail)

// ═══ 封面 ═══

const coverUrl = computed(() => {
  const artId = artist.value?.coverArt || artist.value?.id
  return artId ? subsonicGetCoverArtUrl(artId, 200) : ''
})

// ═══ 标签切换 ═══

const activeTab = ref('albums')
const tabsLoaded = ref({ albums: false, songs: false })

function switchTab(tab) {
  activeTab.value = tab
  if (!tabsLoaded.value[tab]) {
    tabsLoaded.value[tab] = true
    if (tab === 'albums') loadAlbums()
    else loadSongs()
  }
}

// ═══ 字母索引 ── 共用 A-Z / 0-9 / # ═══

const letterChips = (() => {
  const chips = []
  for (let c = 65; c <= 90; c++) chips.push(String.fromCharCode(c))
  chips.push('0-9', '#')
  return chips
})()

// ═══ 专辑区 ═══

const albumSort = ref('newest')
const albumViewMode = ref('grid')
const albumLetter = ref(null)
const albumPage = ref(1)
const albumPageSize = ref(20)

const albumSortOptions = computed(() => [
  { label: t('album.newest'), value: 'newest' },
  { label: t('album.alphabetical'), value: 'alphabetical' },
  { label: t('album.byYear'), value: 'byYear' },
])

const displayAlbums = computed(() =>
  library.artistAlbums.map(a => ({
    ...a,
    artist: a.artist || artist.value?.name || '',
    artistId: a.artistId || artist.value?.id,
  }))
)

function loadAlbums() {
  const id = route.params.id
  if (!id) return
  const offset = (albumPage.value - 1) * albumPageSize.value
  library.loadArtistAlbums(id, {
    sort: albumSort.value,
    letter: albumLetter.value,
    limit: albumPageSize.value,
    offset,
  })
}

function onSortChange() {
  if (activeTab.value === 'albums') {
    if (albumSort.value !== 'alphabetical') albumLetter.value = null
    albumPage.value = 1
    loadAlbums()
  } else {
    if (songSort.value !== 'alphabetical') songLetter.value = null
    songPage.value = 1
    loadSongs()
  }
}

function onAlbumLetter(letter) {
  albumLetter.value = letter
  albumPage.value = 1
  loadAlbums()
}

function onAlbumPageSizeChange() {
  albumPage.value = 1
  loadAlbums()
}

// ═══ 歌曲区 ═══

const songSort = ref('alphabetical')
const songViewMode = ref('list')
const songLetter = ref(null)
const songPage = ref(1)
const songPageSize = ref(50)

const songSortOptions = computed(() => [
  { label: t('song.sortByName'), value: 'alphabetical' },
  { label: t('song.sortFrequent'), value: 'frequent' },
  { label: t('song.sortNewest'), value: 'newest' },
])

const displaySongs = computed(() =>
  library.artistSongs.map(s => ({
    ...s,
    artist: s.displayArtist || s.artist || artist.value?.name || '',
    album: s.album || '',
    coverArt: s.coverArt || artist.value?.coverArt || artist.value?.id,
    _starred: s.id != null ? player.isSongStarred(s.id) : false,
  }))
)

/** 缓存歌曲列表用于播放（首次加载后保持引用） */
const cachedSongs = ref([])

function loadSongs() {
  const id = route.params.id
  if (!id) return
  const offset = (songPage.value - 1) * songPageSize.value
  library.loadArtistSongs(id, {
    letter: songLetter.value,
    sort: songSort.value,
    limit: songPageSize.value,
    offset,
  })
}

function onSongLetter(letter) {
  songLetter.value = letter
  songPage.value = 1
  loadSongs()
}

function onSongPageSizeChange() {
  songPage.value = 1
  loadSongs()
}

// ═══ 收藏状态 ═══

const isStarred = computed(() => {
  const id = artist.value?.id
  return id != null ? player.isArtistStarred(id) : false
})

const genderLabel = computed(() => {
  const g = artist.value?.gender
  if (g === 1) return t('artist.genderMale')
  if (g === 2) return t('artist.genderFemale')
  if (g === 3) return t('artist.genderGroup')
  return null
})

// ═══ 生命周期 ═══

onMounted(() => {
  loadArtistData()
})

// 监听路由参数变化（同一组件内跳转到另一个艺术家）
watch(() => route.params.id, (newId, oldId) => {
  if (newId && newId !== oldId) {
    loadArtistData()
  }
})

function loadArtistData() {
  const id = route.params.id
  if (id) {
    library.loadArtist(id)
    player.loadFavoriteIds()
    // 首屏自动加载默认标签（专辑）
    tabsLoaded.value = { albums: false, songs: false }
    activeTab.value = 'albums'
    tabsLoaded.value.albums = true
    loadAlbums()
  }
}

// ═══ 导航 ═══

function goBack() { router.back() }
function goAlbum(albumId) {
  if (albumId) router.push(`/player/albums/${albumId}`)
}

// ═══ 播放 ═══

function playSong(song) {
  if (song.path) player.play(song)
}

function playAll() {
  // 触发加载全量歌曲后播放第一首
  const id = route.params.id
  if (!id) return
  library.loadArtistSongs(id, { sort: 'alphabetical', limit: 500, offset: 0 }).then(() => {
    const first = library.artistSongs[0]
    if (first?.path) player.play(first)
  })
}

function shuffleAll() {
  const id = route.params.id
  if (!id) return
  library.loadArtistSongs(id, { sort: 'alphabetical', limit: 500, offset: 0 }).then(() => {
    const songs = library.artistSongs
    if (!songs.length) return
    const idx = Math.floor(Math.random() * songs.length)
    if (songs[idx]?.path) player.play(songs[idx])
  })
}

function addToQueue(song) {
  if (song) player.addToQueue([song])
}

// ═══ 收藏 ═══

async function toggleArtistFav() {
  const id = artist.value?.id
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

async function onAlbumToggleFav(album) {
  const id = album?.id
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
  const list = library.artistSongs
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

const genderOptions = [
  { label: '未知', value: 0 },
  { label: '男', value: 1 },
  { label: '女', value: 2 },
  { label: '组合/团体', value: 3 },
]

const editForm = ref({
  name: '',
  gender: null,
  country: '',
  introduction: '',
})

watch(showEditModal, (v) => {
  if (v && artist.value) {
    editForm.value = {
      name: artist.value.name || '',
      gender: artist.value.gender != null ? artist.value.gender : null,
      country: artist.value.country || '',
      introduction: artist.value.introduction || '',
    }
  }
})

async function doSave() {
  saving.value = true
  try {
    const res = await updateArtist(artist.value.id, editForm.value)
    if (res?.artist) {
      library.artistDetail = res.artist
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
/* ════════════════════════════════════════════════════
   艺术家详情页 — 标签切换 + 分区懒加载 + 方形封面
   ════════════════════════════════════════════════════ */

.artist-detail-page {
  width: min(100% - var(--content-padding-x) * 2, var(--content-max-width));
  margin: 0 auto;
  padding: 24px 0;
}

/* ── 页头 ── */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 16px;
  flex-wrap: wrap;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.back-btn {
  color: var(--ct-text-2) !important;
  margin-right: 2px;
}
.back-btn:hover { color: var(--ct-text) !important; }

.header-left h1 {
  font-size: 22px;
  font-weight: 700;
  margin: 0;
  color: var(--ct-text);
}

.hero-fav-btn {
  flex-shrink: 0;
  color: var(--ct-text-3) !important;
  padding: 0 4px;
  transition: transform 0.15s;
}
.hero-fav-btn:hover { transform: scale(1.15); }

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

/* ── Hero 信息 ── */
.detail-hero {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  padding-bottom: 20px;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--ct-border);
}

.hero-cover {
  width: 180px;
  height: 180px;
  flex-shrink: 0;
}

.hero-info {
  flex: 1;
  min-width: 0;
}

.hero-meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 6px;
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
.meta-tag.gender  { color: var(--ct-accent); background: rgb(var(--ct-accent-rgb) / 0.1); }
.meta-tag.country { color: var(--ct-accent); background: rgb(var(--ct-accent-rgb) / 0.1); }

/* ── 简介 ── */
.hero-intro {
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--ct-bg-secondary);
  border: 1px solid var(--ct-border);
  max-width: 600px;
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
  -webkit-line-clamp: 3;
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
.intro-toggle:hover { text-decoration: underline; }

/* ── 标签切换 ── */
.tab-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  border-bottom: 2px solid var(--ct-border);
}

.tab-btns {
  display: flex;
  gap: 0;
}

.tab-btn {
  padding: 8px 20px;
  border: none;
  background: none;
  color: var(--ct-text-3);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  font-family: inherit;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  transition: color 0.15s, border-color 0.15s;
}
.tab-btn:hover { color: var(--ct-text); }
.tab-btn.active {
  color: var(--ct-accent);
  border-bottom-color: var(--ct-accent);
}

.tab-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

/* ── 标签内容 ── */
.tab-content {
  min-height: 200px;
}

/* ── 字母索引 ── */
.letter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 3px;
  margin-bottom: 14px;
}

.letter-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 30px;
  height: 26px;
  padding: 2px 6px;
  border-radius: 5px;
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

/* ── 专辑网格 ── */
.album-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

/* ── 分页 ── */
.tab-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
