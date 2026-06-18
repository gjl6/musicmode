<template>
  <div class="playlist-detail-page">
    <n-spin :show="!pl" size="medium">
      <template v-if="pl">

        <div class="detail-hero">
          <n-button text class="back-btn" @click="$router.push('/player/playlists')">
            <template #icon><n-icon :size="20"><ArrowBackOutline /></n-icon></template>
          </n-button>
          <PlaylistCover
            :entries="entries"
            :size="220"
          />
          <div class="hero-info">

            <div class="hero-name-row">
              <h1 v-if="!editingName" @dblclick="startEditName" :title="$t('playlist.edit')">
                {{ pl.name || '—' }}
              </h1>
              <n-input
                v-else
                ref="nameInputRef"
                v-model:value="editNameValue"
                size="large"
                class="hero-name-input"
                @blur="commitEditName"
                @keyup.enter="commitEditName"
                @keyup.escape="editingName = false"
              />

              <n-button
                class="hero-fav-btn"
                text
                @click="toggleFavPlaylist"
                :title="isFav ? $t('playlist.unfavorited') : $t('playlist.favorited')"
              >
                <template #icon>
                  <n-icon :size="22" :color="isFav ? '#EF4444' : undefined">
                    <Heart v-if="isFav" />
                    <HeartOutline v-else />
                  </n-icon>
                </template>
              </n-button>
            </div>


            <p v-if="!editingComment && pl.comment" class="hero-comment" @dblclick="startEditComment">
              {{ pl.comment }}
            </p>
            <p v-else-if="!editingComment && !pl.comment" class="hero-comment hero-comment--empty"
              @dblclick="startEditComment">
              双击添加描述...
            </p>
            <n-input
              v-if="editingComment"
              ref="commentInputRef"
              v-model:value="editCommentValue"
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 3 }"
              class="hero-comment-input"
              placeholder="歌单描述..."
              @blur="commitEditComment"
              @keyup.enter="commitEditComment"
              @keyup.escape="editingComment = false"
            />


            <div class="hero-meta">
              <n-tag size="small" :bordered="false">
                {{ $t('playlist.songCount', { count: pl.songCount || entries.length || 0 }) }}
              </n-tag>
              <n-tag v-if="totalDurationText" size="small" :bordered="false">
                {{ totalDurationText }}
              </n-tag>
              <n-tag v-if="totalSizeText" size="small" :bordered="false">
                {{ totalSizeText }}
              </n-tag>
              <n-tag v-if="pl.owner" size="small" :bordered="false">{{ pl.owner }}</n-tag>
              <n-tag v-if="pl.public" size="small" type="success" :bordered="false">Public</n-tag>
            </div>


            <n-space style="margin-top:12px">
              <n-button type="primary" size="small" @click="playAll" :disabled="!entries.length">
                <template #icon><n-icon :size="16"><PlayOutline /></n-icon></template>
                {{ $t('album.playAll') }}
              </n-button>
              <n-button size="small" @click="$router.push(`/player/playlists/${pl.id}/edit`)">
                <template #icon><n-icon :size="16"><CreateOutline /></n-icon></template>
                {{ $t('playlist.edit') }}
              </n-button>
              <n-button size="small" @click="doExportM3u">
                <template #icon><n-icon :size="16"><DownloadOutline /></n-icon></template>
                {{ $t('playlist.export') }}
              </n-button>
              <n-popconfirm @positive-click="doDelete">
                <template #trigger>
                  <n-button size="small" type="error">
                    <template #icon><n-icon :size="16"><TrashOutline /></n-icon></template>
                    {{ $t('playlist.delete') }}
                  </n-button>
                </template>
                {{ $t('playlist.confirmDelete') }}
              </n-popconfirm>
            </n-space>
          </div>
        </div>


        <div v-if="selectedPositions.size > 0" class="batch-bar">
          <span class="batch-hint">{{ $t('playlist.selected', { count: selectedPositions.size }) }}</span>
          <n-space>
            <n-button size="small" @click="batchAddToQueue" :disabled="!selectedSongs.length">
              <template #icon><n-icon :size="15"><AddOutline /></n-icon></template>
              {{ $t('playlist.batchAddToQueue') }}
            </n-button>
            <n-button size="small" type="error" @click="batchRemove">
              <template #icon><n-icon :size="15"><TrashOutline /></n-icon></template>
              {{ $t('playlist.batchRemove') }}
            </n-button>
          </n-space>
        </div>


        <div class="detail-songs">
          <n-data-table
            v-if="entries.length"
            :columns="songColumns"
            :data="entries"
            :row-key="rowKey"
            :row-props="rowProps"
            :bordered="false"
            size="medium"
          />
          <n-empty v-else :description="$t('player.noData')" size="small" style="margin-top:32px" />
        </div>
      </template>
    </n-spin>
  </div>
</template>

<script setup>
import { computed, h, ref, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useMessage } from 'naive-ui'
import {
  PlayOutline, CreateOutline, TrashOutline, DownloadOutline,
  CloseOutline, HeartOutline, Heart, ArrowUpOutline, ArrowDownOutline,
  ArrowBackOutline, AddOutline,
} from '@vicons/ionicons5'
import {
  NButton, NIcon, NText, NSpace, NTag, NCheckbox, NInput, NDataTable, NPopconfirm,
} from 'naive-ui'
import { useLibraryStore } from '@/store/playback/library.js'
import { usePlayerStore } from '@/store/playback/player.js'
import {
  subsonicDeletePlaylist,
  subsonicUpdatePlaylist,
} from '@/api/playback/subsonic.js'
import { exportM3uUrl, removeSongFromPlaylist, savePlaylistOrder } from '@/api/playback/playlist.js'
import PlaylistCover from '@/components/playback/PlaylistCover.vue'

const route = useRoute()
const router = useRouter()
const library = useLibraryStore()
const player = usePlayerStore()
const message = useMessage()
const { t } = useI18n()

const pl = computed(() => library.playlistDetail)
const entries = computed(() => pl.value?.entry || [])


const isFav = computed(() => player.isPlaylistStarred(pl.value?.id))

async function toggleFavPlaylist() {
  const id = pl.value?.id
  if (id == null) return
  try {
    if (isFav.value) {
      await player.unstarPlaylist(id)
      message.success(t('playlist.unfavorited'))
    } else {
      await player.starPlaylist(id)
      message.success(t('playlist.favorited'))
    }
  } catch {
    message.warning(t('player.favoriteFailed'))
  }
}


const editingName = ref(false)
const editingComment = ref(false)
const editNameValue = ref('')
const editCommentValue = ref('')
const nameInputRef = ref(null)
const commentInputRef = ref(null)

function startEditName() {
  editNameValue.value = pl.value?.name || ''
  editingName.value = true
  nextTick(() => nameInputRef.value?.focus())
}

function startEditComment() {
  editCommentValue.value = pl.value?.comment || ''
  editingComment.value = true
  nextTick(() => commentInputRef.value?.focus())
}

async function commitEditName() {
  editingName.value = false
  const val = editNameValue.value.trim()
  if (!val || val === (pl.value?.name || '')) return
  try {
    await subsonicUpdatePlaylist(pl.value.id, { name: val })
    pl.value.name = val
    message.success('名称已更新')
  } catch { message.warning('更新失败') }
}

async function commitEditComment() {
  editingComment.value = false
  const val = editCommentValue.value.trim()
  if (val === (pl.value?.comment || '')) return
  try {
    await subsonicUpdatePlaylist(pl.value.id, { comment: val })
    pl.value.comment = val
  } catch { message.warning('更新失败') }
}


const totalDurationText = computed(() => {
  const dur = pl.value?.duration
  if (!dur) return ''
  const s = Math.floor(Number(dur))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
  return `${m}:${String(sec).padStart(2, '0')}`
})

const totalSizeText = computed(() => {
  const sz = entries.value.reduce((sum, e) => sum + (Number(e.size) || 0), 0)
  if (!sz) return ''
  if (sz < 1048576) return `${(sz / 1024).toFixed(1)} KB`
  if (sz < 1073741824) return `${(sz / 1048576).toFixed(1)} MB`
  return `${(sz / 1073741824).toFixed(2)} GB`
})


const selectedPositions = ref(new Set())

const selectedSongs = computed(() =>
  entries.value.filter((_, i) => selectedPositions.value.has(i + 1))
)

const isAllSelected = computed(() =>
  entries.value.length > 0 && selectedPositions.value.size === entries.value.length
)

function toggleSelectAll() {
  if (isAllSelected.value) {
    selectedPositions.value = new Set()
  } else {
    selectedPositions.value = new Set(entries.value.map((_, i) => i + 1))
  }
}

function toggleSelectRow(pos) {
  const s = new Set(selectedPositions.value)
  s.has(pos) ? s.delete(pos) : s.add(pos)
  selectedPositions.value = s
}

async function batchRemove() {
    const posList = [...selectedPositions.value].sort((a, b) => b - a)
  const entry = pl.value.entry
  const removed = []
  for (const pos of posList) {
    const idx = pos - 1
    if (idx >= 0 && idx < entry.length) {
      removed.push({ pos, item: entry.splice(idx, 1)[0] })
    }
  }
  selectedPositions.value = new Set()

    let ok = 0
  for (const pos of posList) {
    try { await removeSongFromPlaylist(pl.value.id, pos); ok++ } catch {  }
  }
  if (ok === posList.length) {
    if (ok > 0) message.success(`已移除 ${ok} 首`)
  } else {
        message.warning(`已移除 ${ok}/${posList.length} 首，正在刷新...`)
    library.loadPlaylist(pl.value.id)
  }
}

function batchAddToQueue() {
  const songs = selectedSongs.value
  if (songs.length) {
    player.addToQueue(songs)
    message.success(`已添加 ${songs.length} 首到队列`)
    selectedPositions.value = new Set()
  }
}


const dragRow = ref(null)

const rowKey = (row) => row.id

const rowProps = (row, index) => ({
  draggable: true,
  style: dragRow.value === index ? 'opacity:0.35' : '',
  ondragstart: (e) => {
    dragRow.value = index
    e.dataTransfer.effectAllowed = 'move'
  },
  ondragover: (e) => {
    e.preventDefault()
    e.dataTransfer.dropEffect = 'move'
  },
  ondrop: (e) => {
    e.preventDefault()
    handleDrop(index)
  },
  ondragend: () => { dragRow.value = null },
})

function localMove(fromIndex, toIndex) {
  const entry = pl.value.entry
  if (!entry || fromIndex === toIndex) return
  const [moved] = entry.splice(fromIndex, 1)
  entry.splice(toIndex, 0, moved)
}


async function saveOrder() {
  const id = pl.value?.id
  if (!id) return
  const songIds = pl.value.entry.map(e => e.id)
  try {
    await savePlaylistOrder(id, songIds)
  } catch {
    message.warning('排序保存失败，已恢复')
    library.loadPlaylist(id)
  }
}

async function handleDrop(toIndex) {
  const fromIndex = dragRow.value
  if (fromIndex == null || fromIndex === toIndex) return
  dragRow.value = null

    localMove(fromIndex, toIndex)
  saveOrder()
}


async function moveUp(pos) {
  if (pos <= 1 || !pl.value?.id) return
  localMove(pos - 1, pos - 2)
  saveOrder()
}

async function moveDown(pos) {
  const last = entries.value.length
  if (pos >= last || !pl.value?.id) return
  localMove(pos - 1, pos)
  saveOrder()
}

async function doRemoveSong(position) {
  const entry = pl.value.entry
  const idx = position - 1
  const removed = entry[idx]
    entry.splice(idx, 1)
  try {
    await removeSongFromPlaylist(pl.value.id, position)
    message.success('已移除')
  } catch {
        entry.splice(idx, 0, removed)
    message.warning('移除失败')
  }
}


function formatDuration(s) {
  if (!s || !isFinite(s)) return '—'
  const m = Math.floor(s / 60)
  const sec = Math.floor(s % 60)
  return `${m}:${String(sec).padStart(2, '0')}`
}

function stopBubble(fn) {
  return (e) => { e.stopPropagation(); fn() }
}

const songColumns = computed(() => [
    {
    title: () => h(NCheckbox, {
      checked: isAllSelected.value,
      indeterminate: selectedPositions.value.size > 0 && !isAllSelected.value,
      onUpdateChecked: toggleSelectAll,
      size: 'small',
    }),
    key: '_select',
    width: 34,
    render: (_, idx) => h(NCheckbox, {
      checked: selectedPositions.value.has(idx + 1),
      onUpdateChecked: () => toggleSelectRow(idx + 1),
      size: 'small',
    }),
  },
    {
    title: '#',
    key: 'track',
    width: 44,
    align: 'center',
    render: (_, idx) => h(NText, { depth: 3, style: 'font-size:12px;font-variant-numeric:tabular-nums' },
      { default: () => String(idx + 1) }),
  },
    {
    title: t('song.cols.title'),
    key: 'title',
    width: 260,
    ellipsis: { tooltip: true },
    render: (r) => h('span', {
      class: 'song-title-cell',
      onClick: stopBubble(() => r.id && router.push(`/player/songs/${r.id}`)),
    }, r.title || '—'),
  },
    {
    title: t('song.cols.artist'),
    key: 'artist',
    width: 150,
    ellipsis: { tooltip: true },
    render: (r) => h(NText, { depth: 2, style: 'font-size:13px' },
      { default: () => r.artist || '—' }),
  },
    {
    title: t('song.cols.album'),
    key: 'album',
    width: 170,
    ellipsis: { tooltip: true },
    render: (r) => h(NText, { depth: 2, style: 'font-size:13px' },
      { default: () => r.album || '—' }),
  },
    {
    title: t('song.cols.duration'),
    key: 'duration',
    width: 60,
    align: 'center',
    render: (r) => h(NText, { depth: 3, style: 'font-size:12px;font-variant-numeric:tabular-nums' },
      { default: () => formatDuration(r.duration) }),
  },
    {
    title: '',
    key: 'actions',
    width: 130,
    render: (_, idx) => h('span', { class: 'row-actions' }, [
      h(NButton, {
        size: 'tiny', circle: true, class: 'act-btn act-btn--play',
        onClick: stopBubble(() => playSong(idx)),
      }, {
        icon: () => h(NIcon, { size: 14 }, { default: () => h(PlayOutline) }),
      }),
      h(NButton, {
        size: 'tiny', circle: true, class: 'act-btn',
        title: t('playlist.moveUp'),
        onClick: stopBubble(() => moveUp(idx + 1)),
        disabled: idx === 0,
      }, {
        icon: () => h(NIcon, { size: 13 }, { default: () => h(ArrowUpOutline) }),
      }),
      h(NButton, {
        size: 'tiny', circle: true, class: 'act-btn',
        title: t('playlist.moveDown'),
        onClick: stopBubble(() => moveDown(idx + 1)),
        disabled: idx === entries.value.length - 1,
      }, {
        icon: () => h(NIcon, { size: 13 }, { default: () => h(ArrowDownOutline) }),
      }),
      h(NButton, {
        size: 'tiny', circle: true, class: 'act-btn act-btn--remove',
        onClick: stopBubble(() => doRemoveSong(idx + 1)),
      }, {
        icon: () => h(NIcon, { size: 13 }, { default: () => h(CloseOutline) }),
      }),
    ]),
  },
])


onMounted(() => {
  const id = route.params.id
  if (id) {
    library.loadPlaylist(id)
    player.loadFavoriteIds()
  }
})


function playSong(idx) {
  const song = entries.value[idx]
  if (song?.path) player.play(song)
}

function playAll() {
  const first = entries.value[0]
  if (first?.path) player.play(first)
}

async function doDelete() {
  try {
    await subsonicDeletePlaylist(pl.value.id)
    message.success('已删除')
    router.push('/player/playlists')
  } catch { message.error('删除失败') }
}

function doExportM3u() {
  window.open(exportM3uUrl(pl.value.id), '_blank')
}
</script>

<style scoped>
.playlist-detail-page {
  width: min(100% - var(--content-padding-x) * 2, var(--content-max-width));
  margin: 0 auto;
  padding: 32px 0 48px;
}


.detail-hero {
  display: flex;
  gap: 32px;
  align-items: flex-start;
  padding-bottom: 24px;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--ct-border);
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

.hero-name-row h1 {
  font-size: 28px;
  font-weight: 700;
  margin: 0;
  color: var(--ct-text);
  letter-spacing: -0.5px;
  cursor: default;
  transition: color 0.15s;
}
.hero-name-row h1:hover {
  color: var(--ct-accent);
}

.hero-name-input {
  font-size: 22px !important;
  font-weight: 700 !important;
  min-width: 200px;
}

.hero-fav-btn {
  flex-shrink: 0;
  color: var(--ct-text-3) !important;
  transition: transform 0.15s;
}
.hero-fav-btn:hover { transform: scale(1.15); }

.hero-comment {
  font-size: 14px;
  color: var(--ct-text-2);
  margin: 4px 0 12px;
  cursor: default;
  line-height: 1.6;
  max-width: 560px;
}
.hero-comment:hover { color: var(--ct-accent); }
.hero-comment--empty {
  color: var(--ct-text-3);
  font-style: italic;
  opacity: 0.5;
}

.hero-comment-input {
  margin: 4px 0 12px;
  font-size: 14px !important;
  max-width: 560px;
}

.hero-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 4px;
}


.batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  margin-bottom: 12px;
  border-radius: var(--radius-md, 8px);
  background: var(--ct-bg-secondary);
  border: 1px solid var(--ct-border);
}

.batch-hint {
  font-size: 13px;
  color: var(--ct-text-2);
  font-weight: 500;
}


.back-btn {
  flex-shrink: 0;
  margin-right: -8px;
  color: var(--ct-text-3);
  align-self: flex-start;
}
.back-btn:hover { color: var(--ct-accent); }


:deep(.n-data-table) {
  --n-td-padding: 10px 14px;
  --n-th-padding: 10px 14px;
}

:deep(.n-data-table-wrapper) {
  border-radius: 10px;
  overflow: hidden;
}

:deep(.n-data-table table) {
  border-radius: 10px;
  overflow: hidden;
}

:deep(.n-data-table td) {
  font-size: 13px;
}

:deep(.n-data-table tr) {
  transition: background 0.12s;
}

:deep(.n-data-table tr:hover td) {
  background: var(--ct-bg-hover, rgba(128, 128, 128, 0.06));
}

:deep(.n-data-table .n-data-table-th) {
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--ct-text-3);
  border-bottom: 1px solid var(--ct-border);
}
.row-actions {
  display: inline-flex;
  gap: 2px;
  align-items: center;
  opacity: 0;
  transition: opacity 0.15s;
}
:deep(.n-data-table tr:hover) .row-actions {
  opacity: 1;
}

.act-btn {
  border: none !important;
  background: transparent !important;
  color: var(--ct-text-3) !important;
  transition: color 0.12s, transform 0.12s;
}
.act-btn:hover { color: var(--ct-text) !important; transform: scale(1.15); }
.act-btn--play {
  opacity: 0.5 !important;
  color: var(--ct-accent) !important;
}
.act-btn--play:hover { opacity: 1 !important; }
.act-btn--remove:hover { color: #EF4444 !important; }

.song-title-cell {
  color: var(--ct-text);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.song-title-cell:hover { color: var(--ct-accent); text-decoration: underline; }

.detail-songs {
  margin-top: 0;
}


@media (max-width: 768px) {
  .playlist-detail-page {
    padding: 16px 16px 32px;
  }
  .detail-hero {
    flex-direction: column;
    gap: 16px;
  }
  .hero-name-row h1 {
    font-size: 22px;
  }
}
</style>
