<template>
  <div class="workbench">
    <Sidebar />
    <EditDrawer />
    <main class="content">
      <!-- 快速工具条 -->
      <ToolBar @tool-click="handleToolClick" />

      <!-- 路径栏 + 列管理 -->
      <PathBar :prefs="prefs" @play-selected="handlePlaySelected" @edit-selected="openEditor(fileStore.selectedFiles[0])" />

      <!-- 空状态引导 -->
      <div v-if="!fileStore.currentPath" class="placeholder">
        <div class="placeholder-inner">
          <n-icon :size="40" color="var(--ct-text-2)"><MusicalNotesOutline /></n-icon>
          <p class="placeholder-title">{{ $t('workbench.selectFile') }}</p>
          <p class="placeholder-hint">{{ $t('workbench.selectFileHint') }}</p>
        </div>
      </div>

      <!-- 表格区 -->
      <template v-if="fileStore.currentPath">
        <div class="table-body">
          <n-spin :show="fileStore.metadataLoading" size="medium">
            <n-data-table
              :columns="visibleColumns"
              :data="fileStore.pageFiles"
              :row-key="(row) => row.id"
              :row-props="(row) => ({ class: fileStore.selectedIds.has(row.id) ? 'row-selected' : '', style: 'cursor: pointer', onClick: () => handleRowClick(row) })"
              :bordered="false"
              :single-line="false"
              size="small"
            />
          </n-spin>
          <n-empty
            v-if="!fileStore.metadataLoading && fileStore.pageFiles.length === 0"
            :description="$t('workbench.emptyDir')"
            size="small"
          />
        </div>

        <!-- 分页 -->
        <div v-if="fileStore.totalFiles > 0" class="table-footer">
          <n-pagination
            size="small"
            :page="fileStore.currentPage"
            :page-size="fileStore.pageSize"
            :item-count="fileStore.totalFiles"
            :page-sizes="[20, 50, 100, 200]"
            show-size-picker
            show-quick-jumper
            @update:page="onPageChange"
            @update:page-size="onPageSizeChange"
          />
        </div>
      </template>

      <!-- 迷你播放器 -->
      <MiniPlayer />
    </main>

    <!-- 工具模态框容器 -->
    <ToolModals ref="toolModalsRef" @done="onToolDone" />
  </div>
</template>

<script setup>
import { computed, h, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { NIcon } from 'naive-ui'
import { MusicalNotesOutline } from '@vicons/ionicons5'
import Sidebar from '@/components/editor/file/Sidebar.vue'
import EditDrawer from '@/components/editor/metadata/EditDrawer.vue'
import ToolBar from '@/components/editor/layout/ToolBar.vue'
import PathBar from '@/components/editor/layout/PathBar.vue'
import MiniPlayer from '@/components/editor/MiniPlayer.vue'
import ToolModals from '@/components/editor/tool/ToolModals.vue'
import { useFileStore } from '@/store/editor/file.js'
import { useAppStore } from '@/store/editor/app.js'
import { useEditorPlayerStore } from '@/store/editor/player.js'
import { useColumnPreferences } from '@/composables/editor/useColumnPreferences.js'
import { useEditAction } from '@/composables/editor/useEditAction.js'
import { songField, albumField, lyricField, styleField, artistNames, hasCover, hasTags } from '@/utils/musicMeta.js'
import { useArtistConfig } from '@/composables/editor/useArtistConfig.js'
import { getCoverUrl } from '@/utils/mediaUrl.js'

const { t } = useI18n()
const { joinSeparator } = useArtistConfig()
const fileStore = useFileStore()
const appStore = useAppStore()
const player = useEditorPlayerStore()
const { openEditor } = useEditAction()

const toolModalsRef = ref(null)

// ── 工具点击 → 委托给 ToolModals ──
function handleToolClick(toolKey) {
  toolModalsRef.value?.open(toolKey)
}

function onToolDone() {
  if (fileStore.currentPath) fileStore.loadPage(fileStore.currentPage)
}

// ── 格式化工具 ──
function formatDuration(seconds) {
  if (seconds == null || seconds === '' || isNaN(seconds) || seconds === 0) return '—'
  const s = Number(seconds)
  return `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`
}
function formatFileSize(bytes) {
  if (bytes == null || bytes === 0) return '—'
  let size = Number(bytes)
  if (size < 1024) return `${size} B`
  size /= 1024; if (size < 1024) return `${size.toFixed(1)} KB`
  size /= 1024; if (size < 1024) return `${size.toFixed(1)} MB`
  size /= 1024; return `${size.toFixed(1)} GB`
}
function formatSampleRate(hz) {
  if (hz == null || hz === 0) return '—'
  return `${(Number(hz) / 1000).toFixed(1)} kHz`
}
function formatChannels(ch) {
  if (ch == null || ch === 0) return '—'
  return ch === 1 ? 'Mono' : ch === 2 ? 'Stereo' : `${ch}ch`
}
function dash(v) {
  if (v == null || v === '' || v === 0) return '—'
  return String(v)
}

// ── 列定义 ──
const allColumns = computed(() => [
  { key: 'hasCover', title: t('workbench.cols.hasCover'), width: 52, defaultVisible: true, align: 'center',
    render: (r) => {
      const src = getCoverUrl(songField(r.meta, 'coverPath'))
      if (!src) return h('span', { class: 'cell-dash' }, '—')
      return h('img', { src, class: 'cover-thumb' })
    }},
  { key: 'name', title: t('workbench.cols.name'), width: 200, defaultVisible: true, ellipsis: { tooltip: true } },
  { key: 'title', title: t('workbench.cols.title'), width: 200, defaultVisible: true, ellipsis: { tooltip: true }, render: (r) => dash(songField(r.meta, 'title')) },
  { key: 'artist', title: t('workbench.cols.artist'), width: 150, defaultVisible: true, ellipsis: { tooltip: true }, render: (r) => dash(artistNames(r.meta, joinSeparator.value || undefined)) },
  { key: 'album', title: t('workbench.cols.album'), width: 180, defaultVisible: true, ellipsis: { tooltip: true }, render: (r) => dash(albumField(r.meta, 'albumName')) },
  { key: 'format', title: t('workbench.cols.format'), width: 80, defaultVisible: true, align: 'center', render: (r) => r.format ? r.format.toUpperCase() : '—' },
  { key: 'duration', title: t('workbench.cols.duration'), width: 80, defaultVisible: true, align: 'center', render: (r) => formatDuration(songField(r.meta, 'duration')) },
  { key: 'year', title: t('workbench.cols.year'), width: 70, defaultVisible: true, align: 'center', render: (r) => dash(songField(r.meta, 'year')) },
  { key: 'genre', title: t('workbench.cols.genre'), width: 120, defaultVisible: true, render: (r) => dash(styleField(r.meta, 'styleName')) },
  { key: 'trackNumber', title: t('workbench.cols.trackNumber'), width: 80, defaultVisible: true, align: 'center', render: (r) => dash(songField(r.meta, 'trackNumber')) },
  { key: 'bitrate', title: t('workbench.cols.bitrate'), width: 80, defaultVisible: true, align: 'center', render: (r) => songField(r.meta, 'bitrate') ? songField(r.meta, 'bitrate') + ' kbps' : '—' },
  { key: 'size', title: t('workbench.cols.size'), width: 90, defaultVisible: false, align: 'right', render: (r) => formatFileSize(r.size) },
  { key: 'modifiedTime', title: t('workbench.cols.modifiedTime'), width: 150, defaultVisible: false, render: (r) => dash(r.modifiedTime) },
  { key: 'path', title: t('workbench.cols.path'), width: 200, defaultVisible: false, ellipsis: { tooltip: true }, render: (r) => dash(r.path) },
  { key: 'sampleRate', title: t('workbench.cols.sampleRate'), width: 90, defaultVisible: false, align: 'center', render: (r) => formatSampleRate(songField(r.meta, 'sampleRate')) },
  { key: 'channels', title: t('workbench.cols.channels'), width: 70, defaultVisible: false, align: 'center', render: (r) => formatChannels(songField(r.meta, 'channels')) },
  { key: 'bitsPerSample', title: t('workbench.cols.bitsPerSample'), width: 80, defaultVisible: false, align: 'center', render: (r) => dash(songField(r.meta, 'bitsPerSample')) },
  { key: 'albumType', title: t('workbench.cols.albumType'), width: 80, defaultVisible: false, align: 'center', render: (r) => dash(albumField(r.meta, 'albumType')) },
  { key: 'company', title: t('workbench.cols.company'), width: 120, defaultVisible: false, render: (r) => dash(albumField(r.meta, 'company')) },
  { key: 'discNumber', title: t('workbench.cols.discNumber'), width: 70, defaultVisible: false, align: 'center', render: (r) => dash(songField(r.meta, 'discNumber')) },
  { key: 'composer', title: t('workbench.cols.composer'), width: 100, defaultVisible: false, ellipsis: { tooltip: true }, render: (r) => dash(songField(r.meta, 'composer')) },
  { key: 'lyricist', title: t('workbench.cols.lyricist'), width: 100, defaultVisible: false, ellipsis: { tooltip: true }, render: (r) => dash(songField(r.meta, 'lyricist')) },
  { key: 'lyrics', title: t('workbench.cols.lyrics'), width: 200, defaultVisible: false, ellipsis: { tooltip: { width: 420, style: { whiteSpace: 'pre-wrap', maxHeight: '400px', overflowY: 'auto' } } },
    render: (r) => {
      const txt = (lyricField(r.meta, 'content') || '').trim()
      return txt ? h('span', { class: 'cell-lyrics' }, txt) : h('span', { class: 'cell-dash' }, '—')
    }},
  { key: 'lrcPath', title: t('workbench.cols.lrcPath'), width: 200, defaultVisible: false, ellipsis: { tooltip: true }, render: (r) => dash(lyricField(r.meta, 'lrcPath')) },
  { key: 'language', title: t('workbench.cols.language'), width: 80, defaultVisible: false, align: 'center', render: (r) => dash(songField(r.meta, 'language')) },
  { key: 'hasTags', title: t('workbench.cols.hasTags'), width: 70, defaultVisible: false, align: 'center',
    render: (r) => hasTags(r.meta)
      ? h('span', { class: 'status-dot ok' })
      : h('span', { class: 'status-dot miss' }) },
  { key: 'status', title: t('workbench.cols.status'), width: 70, defaultVisible: false, align: 'center', render: (r) => hasTags(r.meta) ? 'ok' : 'miss' },
])

const prefs = useColumnPreferences(allColumns)
const visibleColumns = computed(() => prefs.sortedVisibleColumns())

// ── 行选择（单击选中/双击编辑） ──
const clickTimer = ref(null)

function handleRowClick(row) {
  if (clickTimer.value && clickTimer.value.rowId === row.id) {
    clearTimeout(clickTimer.value.timer)
    clickTimer.value = null
    fileStore.setSelectedIds([row.id])
    openEditor(row)
    return
  }
  fileStore.toggleSelect(row.id)
  clickTimer.value = { rowId: row.id, timer: setTimeout(() => { clickTimer.value = null }, 300) }
}

// ── 播放 ──
function handlePlaySelected() {
  const files = fileStore.selectedFiles
  if (files.length === 1) player.play(files[0])
}

// ── 分页 ──
function onPageChange(page) { fileStore.loadPage(page) }
function onPageSizeChange(size) { fileStore.pageSize = size; fileStore.loadPage(1) }

// ── 目录变化 → 自动加载 ──
watch(() => fileStore.currentPath, (path) => {
  if (path) fileStore.loadPage(1)
})
</script>

<style>
.cover-thumb {
  width: 40px; height: 40px;
  border-radius: 4px; object-fit: cover; display: block;
  background: var(--color-bg-secondary);
}
</style>

<style scoped>
.workbench { display: flex; height: 100vh; }

.content {
  flex: 1; display: flex; flex-direction: column;
  background: var(--gradient-page, var(--ct-bg));
  color: var(--ct-text); min-width: 0;
}

/* ── 空状态 ── */
.placeholder { flex: 1; display: flex; align-items: center; justify-content: center; }
.placeholder-inner { text-align: center; display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 48px; }
.placeholder-title { font-size: 14px; font-weight: 500; color: var(--ct-text); margin: 0; letter-spacing: 0.02em; }
.placeholder-hint { font-size: 12px; color: var(--ct-text-2); margin: 0; letter-spacing: 0.02em; }

/* ── 表格 ── */
.table-body { flex: 1; overflow: auto; }
.table-body :deep(.n-data-table) {
  --n-td-color: transparent; --n-th-color: transparent; --n-merged-th-color: transparent;
}
.table-body :deep(.n-data-table-thead) { position: sticky; top: 0; z-index: 10; }
.table-body :deep(.n-data-table-th) {
  font-size: 11px !important; font-weight: 600 !important;
  letter-spacing: 0.04em; text-transform: uppercase;
  color: var(--ct-text-2) !important;
  padding: 8px 12px !important;
  border-bottom: 1px solid var(--ct-border) !important;
  background: var(--ct-bg) !important;
}
.table-body :deep(.n-data-table-td) {
  font-size: 12.5px; letter-spacing: 0.01em;
  padding: 7px 12px !important; border-bottom: none !important;
}
.table-body :deep(.n-data-table-tr:hover td) {
  background: rgb(var(--ct-accent-rgb) / 0.28) !important;
  box-shadow: none !important;
}
/* 选中行 — 左侧指示条 + 品牌色背景 */
.table-body :deep(.row-selected td) {
  background: rgb(var(--ct-accent-rgb) / 0.22) !important;
}
.table-body :deep(.row-selected td:first-child) {
  box-shadow: inset 3px 0 0 var(--color-accent) !important;
}
.table-body :deep(.row-selected:hover td) {
  background: rgb(var(--ct-accent-rgb) / 0.28) !important;
}
.table-body :deep(.n-data-table-wrapper) { border: none !important; }
.table-body::-webkit-scrollbar { width: 6px; height: 6px; }
.table-body::-webkit-scrollbar-track { background: transparent; }
.table-body::-webkit-scrollbar-thumb { background: transparent; border-radius: 10px; transition: background 0.2s; }
.table-body:hover::-webkit-scrollbar-thumb { background: var(--sb-scrollbar); }
.table-body::-webkit-scrollbar-corner { background: transparent; }

/* ── 分页 ── */
.table-footer {
  display: flex; justify-content: flex-end;
  padding: 6px 12px; flex-shrink: 0;
  border-top: var(--border-width-default) solid var(--ct-border);
  background: var(--gradient-button, var(--ct-bg-secondary));
}

/* ── 歌词 ── */
.cell-lyrics {
  display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2;
  -webkit-box-orient: vertical; overflow: hidden;
  font-size: 11.5px; line-height: 1.5; color: var(--ct-text-2); white-space: pre-line;
}
:deep(.cell-dash) { color: var(--ct-text-2); font-style: italic; }

/* 状态点 */
.status-dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; }
.status-dot.ok { background: var(--n-color-success); }
.status-dot.miss { background: var(--n-color-warning); }
</style>
