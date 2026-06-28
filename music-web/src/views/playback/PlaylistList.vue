<template>
  <div class="playlist-list-page">
    <!-- ═══ 页头 ═══ -->
    <div class="page-header">
      <div class="header-left">
        <h1>{{ $t('playlist.title') }}</h1>
        <span class="header-count">{{ $t('playlist.stats', { pl: filteredPlaylists.length, songs: totalSongs }) }}</span>
      </div>
      <div class="header-actions">
        <n-button size="small" @click="doImportM3u">
          <template #icon><n-icon :size="16"><CloudUploadOutline /></n-icon></template>
          {{ $t('playlist.import') }}
        </n-button>
        <n-button size="small" type="primary" @click="showCreate = true">
          <template #icon><n-icon :size="16"><AddOutline /></n-icon></template>
          {{ $t('playlist.create') }}
        </n-button>
      </div>
    </div>

    <!-- ═══ 工具栏 ═══ -->
    <div class="toolbar">
      <n-input
        v-model:value="searchQuery"
        :placeholder="$t('playlist.search')"
        size="small"
        clearable
        class="toolbar-search"
      >
        <template #prefix><n-icon :size="16"><SearchOutline /></n-icon></template>
      </n-input>

      <div class="toolbar-right">
        <n-select
          v-model:value="sortBy"
          :options="sortOptions"
          size="small"
          :consistent-menu-width="false"
          style="width:110px"
        />
        <n-button
          size="tiny"
          :type="favFilter === 'starred' ? 'primary' : 'default'"
          @click="favFilter = favFilter === 'starred' ? 'all' : 'starred'"
        >
          <template #icon>
            <n-icon :size="14">
              <Heart v-if="favFilter === 'starred'" />
              <HeartOutline v-else />
            </n-icon>
          </template>
          {{ $t('playlist.filterStarred') }}
        </n-button>
      </div>
    </div>

    <!-- ═══ 歌单网格 ═══ -->
    <n-spin :show="library.loading" size="medium">
      <div v-if="filteredPlaylists.length" class="playlist-grid">
        <PlaylistCard
          v-for="pl in filteredPlaylists"
          :key="pl.id"
          :playlist="pl"
          :size="180"
          :favorited="player.isPlaylistStarred(pl.id)"
          @click="goDetail(pl.id)"
          @play="handlePlay(pl)"
          @toggle-fav="handleToggleFav(pl)"
        />
      </div>
      <n-empty
        v-else-if="!library.loading"
        :description="searchQuery ? '未找到匹配的歌单' : $t('player.noData')"
        size="small"
        style="margin-top:48px"
      />
    </n-spin>

    <!-- ═══ 新建歌单对话框 ═══ -->
    <n-modal
      v-model:show="showCreate"
      :title="$t('playlist.create')"
      preset="card"
      size="small"
      style="max-width:460px"
    >
      <div class="create-form">
        <!-- 封面选择 -->
        <div class="cover-picker">
          <div class="cover-preview" @click="triggerCoverInput">
            <img v-if="coverPreview" :src="coverPreview" class="cover-img" />
            <div v-else class="cover-placeholder">
              <n-icon :size="28"><ImageOutline /></n-icon>
              <span>点击设置封面</span>
            </div>
            <div class="cover-overlay">
              <n-icon :size="18"><CameraOutline /></n-icon>
            </div>
          </div>
          <input
            ref="coverInputRef"
            type="file"
            accept="image/*"
            style="display:none"
            @change="onCoverFileChange"
          />
          <n-button v-if="coverFile" size="tiny" text type="warning" @click="clearCover">
            移除封面
          </n-button>
        </div>

        <div class="create-fields">
          <n-form-item :label="$t('playlist.name')">
            <n-input v-model:value="newPlName" placeholder="My Playlist" />
          </n-form-item>
          <n-form-item :label="$t('playlist.comment')">
            <n-input v-model:value="newPlComment" placeholder="" />
          </n-form-item>
        </div>
      </div>

      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreate = false">{{ $t('common.cancel') }}</n-button>
          <n-button type="primary" @click="doCreate" :loading="creating">{{ $t('playlist.create') }}</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { AddOutline, CloudUploadOutline, SearchOutline, HeartOutline, Heart, ImageOutline, CameraOutline } from '@vicons/ionicons5'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useLibraryStore } from '@/store/playback/library.js'
import { usePlayerStore } from '@/store/playback/player.js'
import { subsonicCreatePlaylist } from '@/api/playback/subsonic.js'
import { uploadPlaylistCover } from '@/api/playback/playlist.js'
import PlaylistCard from '@/components/playback/PlaylistCard.vue'

const { t } = useI18n()
const router = useRouter()
const library = useLibraryStore()
const player = usePlayerStore()
const message = useMessage()

// ═══ 状态 ═══

const searchQuery = ref('')
const sortBy = ref('updated')
const favFilter = ref('all')

const sortOptions = computed(() => [
  { label: t('playlist.sortUpdated'), value: 'updated' },
  { label: t('playlist.sortName'), value: 'name' },
  { label: t('playlist.sortSongCount'), value: 'songCount' },
])

const playlists = computed(() => library.playlists)

// ── 过滤 + 排序 ──

const filteredPlaylists = computed(() => {
  let list = [...playlists.value]

  // 搜索过滤
  if (searchQuery.value.trim()) {
    const q = searchQuery.value.toLowerCase()
    list = list.filter(pl => pl.name?.toLowerCase().includes(q))
  }

  // 收藏过滤
  if (favFilter.value === 'starred') {
    list = list.filter(pl => player.isPlaylistStarred(pl.id))
  }

  // 排序
  list.sort((a, b) => {
    switch (sortBy.value) {
      case 'name':
        return (a.name || '').localeCompare(b.name || '')
      case 'songCount':
        return (b.songCount || 0) - (a.songCount || 0)
      case 'updated':
      default:
        return (b.changed || b.updated || '').localeCompare(a.changed || a.updated || '')
    }
  })

  return list
})

const totalSongs = computed(() =>
  filteredPlaylists.value.reduce((sum, pl) => sum + (pl.songCount || 0), 0)
)

// ═══ 生命周期 ═══

onMounted(() => {
  library.loadPlaylists()
  player.loadFavoriteIds()
})

// ═══ 导航 ═══

function goDetail(id) {
  router.push(`/player/playlists/${id}`)
}

// ═══ 播放 ═══

async function handlePlay(pl) {
  if (!pl.id) return
  await library.loadPlaylist(pl.id)
  const entries = library.playlistDetail?.entry || []
  if (entries.length) {
    player.playAll(entries, 0)
  }
}

// ═══ 收藏 ═══

async function handleToggleFav(pl) {
  const id = pl?.id
  if (id == null) return
  try {
    if (player.isPlaylistStarred(id)) {
      await player.unstarPlaylist(id)
    } else {
      await player.starPlaylist(id)
    }
  } catch {
    message.warning(t('player.favoriteFailed'))
  }
}

// ═══ 创建歌单 ═══

const showCreate = ref(false)
const newPlName = ref('')
const newPlComment = ref('')
const creating = ref(false)
const coverFile = ref(null)
const coverPreview = ref('')
const coverInputRef = ref(null)

function triggerCoverInput() {
  coverInputRef.value?.click()
}

function onCoverFileChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  coverFile.value = file
  const reader = new FileReader()
  reader.onload = (ev) => { coverPreview.value = ev.target?.result || '' }
  reader.readAsDataURL(file)
  // 重置 input 以便重复选择同一文件
  e.target.value = ''
}

function clearCover() {
  coverFile.value = null
  coverPreview.value = ''
}

async function doCreate() {
  if (!newPlName.value.trim()) return
  creating.value = true
  try {
    // 1) 通过 Subsonic API 创建歌单
    const resp = await subsonicCreatePlaylist(newPlName.value.trim())
    const pl = resp?.['subsonic-response']?.playlist
    const plId = pl?.id

    // 2) 如果有封面图，上传
    if (plId && coverFile.value) {
      try {
        await uploadPlaylistCover(plId, coverFile.value)
      } catch { /* 封面上传失败不影响创建 */ }
    }

    message.success('已创建')
    showCreate.value = false
    newPlName.value = ''
    newPlComment.value = ''
    coverFile.value = null
    coverPreview.value = ''
    library.loadPlaylists()
  } catch { message.error('创建歌单失败') }
  finally { creating.value = false }
}

// ═══ M3U 导入 ═══

function doImportM3u() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.m3u,.m3u8'
  input.onchange = async (e) => {
    const file = e.target.files?.[0]
    if (!file) return
    try {
      const text = await file.text()
      const plName = file.name.replace(/\.(m3u8?)$/i, '')
      const pl = await subsonicCreatePlaylist(plName)
      if (!pl?.['subsonic-response']?.playlist) {
        message.warning('创建歌单失败')
        return
      }
      message.success(`已从 "${plName}" 创建歌单（M3U 导入功能完善中）`)
      library.loadPlaylists()
    } catch { message.error('M3U 导入失败') }
  }
  input.click()
}
</script>

<style scoped>
.playlist-list-page {
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
  flex-wrap: wrap;
  gap: 8px;
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

/* ── 工具栏 ── */
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.toolbar-search {
  width: 240px;
  flex-shrink: 0;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ── 网格 ── */
.playlist-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}

/* ── 创建弹窗 ── */
.create-form {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.cover-picker {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.cover-preview {
  width: 120px;
  height: 120px;
  border-radius: 10px;
  border: 2px dashed var(--ct-border);
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: border-color 0.2s;
}

.cover-preview:hover {
  border-color: var(--ct-primary);
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: var(--ct-text-3);
  font-size: 11px;
  background: var(--ct-bg-hover);
}

.cover-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0,0,0,0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
  color: white;
}

.cover-preview:hover .cover-overlay {
  opacity: 1;
}

.create-fields {
  flex: 1;
  min-width: 0;
}
</style>
