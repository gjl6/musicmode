<template>
  <div class="album-manager">
    <!-- ═══ 左侧边栏 ═══ -->
    <AlbumSidebar
      :letter="letter"
      :select-mode="selectMode"
      :filter-artist-name="filterArtistName"
      :filter-min-songs="filterMinSongs"
      :filter-max-songs="filterMaxSongs"
      :loading="loading"
      :total="total"
      @update:letter="letter = $event"
      @update:select-mode="selectMode = $event"
      @update:filter-artist-name="filterArtistName = $event"
      @update:filter-min-songs="filterMinSongs = $event"
      @update:filter-max-songs="filterMaxSongs = $event"
      @search="onGlobalSearch"
      @query="doQuery"
    />

    <!-- ═══ 主内容区 ═══ -->
    <main class="am-main">
      <!-- 批量工具条 -->
      <ToolBar :tools="batchTools" @tool-click="openTool" />

      <!-- 表格工具栏 -->
      <div class="am-table-toolbar">
        <div class="am-tt-left">
          <n-button
            size="tiny"
            :disabled="selectedIds.length !== 1"
            @click="openEditForSelected"
          >
            <template #icon><n-icon :size="15"><CreateOutline /></n-icon></template>
            {{ $t('albumManager.editAlbum') }}
          </n-button>
        </div>
        <div class="am-tt-right">
          <n-popover trigger="click" placement="bottom-end">
            <template #trigger>
              <n-button size="tiny">
                <template #icon><n-icon :size="15"><SettingsOutline /></n-icon></template>
                {{ $t('artistManager.actions') }}
              </n-button>
            </template>
            <div class="am-col-popover">
              <div class="am-col-actions">
                <n-button size="tiny" text @click="showAllCols">全选</n-button>
                <n-button size="tiny" text @click="hideAllCols">全不选</n-button>
                <n-button size="tiny" text @click="resetCols">重置</n-button>
              </div>
              <n-divider style="margin:6px 0" />
              <div
                v-for="col in allColumnDefs"
                :key="col.key"
                class="am-col-item"
                @click="toggleCol(col.key)"
              >
                <n-icon v-if="!hiddenCols.has(col.key)" :size="14" color="var(--ct-accent)">
                  <CheckmarkOutline />
                </n-icon>
                <n-icon v-else :size="14" color="var(--ct-text-3)">
                  <RemoveOutline />
                </n-icon>
                <span :class="{ 'am-col-hidden-label': hiddenCols.has(col.key) }">{{ col.title }}</span>
              </div>
            </div>
          </n-popover>
        </div>
      </div>

      <!-- 表格 -->
      <n-spin :show="loading" size="medium">
        <n-data-table
          :columns="visibleColumns"
          :data="albums"
          :row-key="(row) => row.id"
          :checked-row-keys="selectedIds"
          :bordered="false"
          :single-line="false"
          size="small"
          class="am-table"
          :row-props="rowProps"
          @update:checked-row-keys="onCheckedChange"
        />
        <div v-if="!loading && albums.length === 0" class="am-empty">
          {{ $t('albumManager.noData') }}
        </div>
      </n-spin>

      <!-- 底栏 -->
      <div class="am-footer">
        <div class="am-footer-left">
          <transition name="fade">
            <n-button
              v-if="selectedIds.length >= 2"
              type="primary"
              size="small"
              @click="openTool('albumMerge')"
            >
              {{ $t('albumManager.mergeSelected', { n: selectedIds.length }) }}
            </n-button>
          </transition>
        </div>
        <div class="am-footer-right">
          <n-pagination
            v-model:page="page"
            v-model:page-size="pageSize"
            :page-sizes="[20, 50, 100, 200]"
            :item-count="total"
            size="small"
            show-size-picker
            @update:page="doQuery"
            @update:page-size="onPageSizeChange"
          />
        </div>
      </div>
    </main>

    <!-- ═══ 编辑抽屉 ═══ -->
    <AlbumEditDrawer
      v-model:show="editDrawerVisible"
      :album="editingAlbum"
      @saved="onAlbumSaved"
    />

    <!-- ═══ Enrich Compare（双击行或按钮触发） ═══ -->
    <n-modal
      v-model:show="enrichCompareVisible"
      preset="card"
      title="Enrich Compare"
      style="max-width:820px;width:820px"
      :mask-closable="true"
    >
      <AlbumEnrichCompare
        v-if="enrichCompareVisible"
        :album-id="editingAlbum?.id"
        :compact="false"
        @applied="enrichCompareVisible = false; onEnrichApplied()"
        @refresh-needed="enrichCompareVisible = false"
      />
    </n-modal>

    <!-- ═══ 工具弹窗 ═══ -->
    <n-modal
      v-model:show="enrichVisible"
      preset="card"
      :title="$t('albumManager.toolEnrich')"
      style="max-width:700px;width:700px"
      :mask-closable="true"
      @update:show="v => { if (!v) enrichVisible = false }"
    >
      <AlbumEnrichPanel
        v-if="enrichVisible"
        :total="total"
        :selected-ids="selectedIds"
        :selection="currentSelection"
        @done="enrichVisible = false; onToolDone()"
      />
    </n-modal>

    <n-modal
      v-model:show="normalizeVisible"
      preset="card"
      :title="$t('albumManager.toolNormalize')"
      style="max-width:600px;width:600px"
      :mask-closable="true"
      @update:show="v => { if (!v) normalizeVisible = false }"
    >
      <AlbumNormalizePanel
        v-if="normalizeVisible"
        :total="total"
        :selected-ids="selectedIds"
        :selection="currentSelection"
        @done="normalizeVisible = false; onToolDone()"
      />
    </n-modal>

    <n-modal
      v-model:show="mergeVisible"
      preset="card"
      :title="$t('albumManager.toolMergePanelTitle')"
      style="max-width:600px;width:600px"
      :mask-closable="false"
      @update:show="v => { if (!v) mergeVisible = false }"
    >
      <AlbumMergePanel
        v-if="mergeVisible"
        :albums="albums"
        :selected-ids="selectedIds"
        @done="mergeVisible = false; onMergeDone()"
      />
    </n-modal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, h } from 'vue'
import {
  SearchOutline, CreateOutline, SettingsOutline,
  CloudDownloadOutline, TextOutline, LayersOutline,
  CheckmarkOutline, RemoveOutline,
} from '@vicons/ionicons5'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { getAlbums } from '@/api/editor/album-manage.js'
import { getCoverUrl } from '@/utils/mediaUrl.js'
import AlbumSidebar from '@/components/editor/album/AlbumSidebar.vue'
import AlbumEnrichPanel from '@/components/editor/album/AlbumEnrichPanel.vue'
import AlbumEnrichCompare from '@/components/editor/album/AlbumEnrichCompare.vue'
import AlbumNormalizePanel from '@/components/editor/album/AlbumNormalizePanel.vue'
import AlbumMergePanel from '@/components/editor/album/AlbumMergePanel.vue'
import AlbumEditDrawer from '@/components/editor/album/AlbumEditDrawer.vue'
import ToolBar from '@/components/editor/layout/ToolBar.vue'

const { t } = useI18n()
const message = useMessage()

// ── 过滤状态 ──
const selectMode = ref('all')
const letter = ref(null)
const keyword = ref('')
const filterArtistName = ref('')
const filterMinSongs = ref(null)
const filterMaxSongs = ref(null)

// ── 批量工具 ──
const batchTools = computed(() => [
  {
    key: 'albumEnrich',
    label: t('albumManager.toolEnrich'),
    desc: t('albumManager.toolEnrichDesc'),
    icon: CloudDownloadOutline,
  },
  {
    key: 'albumNormalize',
    label: t('albumManager.toolNormalize'),
    desc: t('albumManager.toolNormalizeDesc'),
    icon: TextOutline,
  },
  {
    key: 'albumMerge',
    label: t('albumManager.toolMerge'),
    desc: t('albumManager.toolMergeDesc'),
    icon: LayersOutline,
  },
])

// ── 数据 ──
const albums = ref([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const pageSize = ref(50)
const selectedIds = ref([])

const selectedRowsData = computed(() =>
  albums.value.filter(a => selectedIds.value.includes(a.id))
)

const currentSelection = computed(() => {
  const sel = { mode: selectMode.value }
  if (letter.value) sel.letter = letter.value
  if (keyword.value) sel.keyword = keyword.value
  if (filterArtistName.value) sel.artist = filterArtistName.value
  if (filterMinSongs.value != null) sel.minSongs = filterMinSongs.value
  if (filterMaxSongs.value != null) sel.maxSongs = filterMaxSongs.value
  return sel
})

// ── 查询 ──
function buildQueryParams() {
  const params = {
    mode: selectMode.value,
    sort: 'alphabetical',
    offset: (page.value - 1) * pageSize.value,
    limit: pageSize.value,
  }
  if (keyword.value) params.keyword = keyword.value
  if (letter.value) params.letter = letter.value
  if (filterArtistName.value) params.artist = filterArtistName.value
  if (filterMinSongs.value != null) params.minSongs = filterMinSongs.value
  if (filterMaxSongs.value != null) params.maxSongs = filterMaxSongs.value
  return params
}

async function doQuery() {
  loading.value = true
  selectedIds.value = []
  try {
    const res = await getAlbums(buildQueryParams())
    albums.value = res?.albums || []
    total.value = res?.total || 0
  } catch (e) {
    console.error('[AlbumManager] 查询失败:', e)
    message.warning('查询失败')
  } finally {
    loading.value = false
  }
}

// ── 全局搜索（独立） ──
function onGlobalSearch(val) {
  keyword.value = val
  page.value = 1
  if (val) {
    selectMode.value = 'all'
    letter.value = null
    filterMinSongs.value = null
    filterMaxSongs.value = null
    filterArtistName.value = ''
  }
  doQuery()
}

function onPageSizeChange() {
  page.value = 1
  doQuery()
}

function onCheckedChange(keys) {
  selectedIds.value = keys
}

// ── 编辑抽屉 ──
const editDrawerVisible = ref(false)
const editingAlbum = ref({})

function openEdit(row) {
  editingAlbum.value = row
  editDrawerVisible.value = true
}

function openEditForSelected() {
  if (selectedIds.value.length !== 1) return
  const row = albums.value.find(a => a.id === selectedIds.value[0])
  if (row) openEdit(row)
}

function onAlbumSaved() {
  message.success(t('albumManager.editSaved'))
  doQuery()
}

// ── Enrich Compare ──
const enrichCompareVisible = ref(false)

// ── 工具弹窗 ──
const enrichVisible = ref(false)
const normalizeVisible = ref(false)
const mergeVisible = ref(false)

const TOOL_VISIBLE_MAP = {
  albumEnrich: enrichVisible,
  albumNormalize: normalizeVisible,
  albumMerge: mergeVisible,
}

function openTool(key) {
  const vis = TOOL_VISIBLE_MAP[key]
  if (vis) {
    if (key === 'albumMerge' && selectedIds.value.length < 2) {
      message.warning('请至少勾选 2 个专辑进行合并')
      return
    }
    vis.value = true
  }
}

function onToolDone() {
  doQuery()
}

function onMergeDone() {
  selectedIds.value = []
  doQuery()
}

function onEnrichApplied() {
  doQuery()
}

// ── TYPE 映射 ──
const TYPE_MAP = {
  ALBUM: t('albumManager.typeAlbum'),
  SINGLE: t('albumManager.typeSingle'),
  EP: t('albumManager.typeEP'),
  COMPILATION: t('albumManager.typeCompilation'),
  LIVE: t('albumManager.typeLive'),
  SOUNDTRACK: t('albumManager.typeSoundtrack'),
}

// ── 列定义 ──
const allColumnDefs = [
  { key: 'coverArt',       title: t('albumManager.coverArt'),        width: 52,  align: 'center', defaultVisible: true },
  { key: 'name',           title: t('albumManager.editName'),       width: 150, ellipsis: { tooltip: true }, defaultVisible: true },
  { key: 'albumType',      title: t('albumManager.editType'),       width: 60,  align: 'center', defaultVisible: true },
  { key: 'albumYear',      title: t('albumManager.editYear'),       width: 55,  align: 'center', defaultVisible: true },
  { key: 'songCount',      title: t('albumManager.songCount'),       width: 48,  align: 'center', defaultVisible: true },
  { key: 'company',        title: t('albumManager.editCompany'),    width: 90,  ellipsis: { tooltip: true }, defaultVisible: true },
  { key: 'language',       title: t('albumManager.editLanguage'),   width: 60,  align: 'center', defaultVisible: true },
  { key: 'introduction',   title: t('albumManager.editIntroduction'), minWidth: 120, ellipsis: { tooltip: true }, defaultVisible: true },
  { key: 'enrichSource',   title: t('albumManager.enrichSource'),   width: 72,  align: 'center', defaultVisible: false },
]

// ── 列可见性 localStorage ──
const STORAGE_KEY = 'music-web-album-hidden-cols'
function loadHiddenCols() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) return new Set(JSON.parse(raw))
  } catch { /* ignore */ }
  return new Set(allColumnDefs.filter(c => !c.defaultVisible).map(c => c.key))
}
const hiddenCols = ref(loadHiddenCols())

function persistHiddenCols() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify([...hiddenCols.value]))
}

function toggleCol(key) {
  const next = new Set(hiddenCols.value)
  next.has(key) ? next.delete(key) : next.add(key)
  hiddenCols.value = next
  persistHiddenCols()
}

function showAllCols() {
  hiddenCols.value = new Set()
  persistHiddenCols()
}

function hideAllCols() {
  hiddenCols.value = new Set(allColumnDefs.map(c => c.key))
  persistHiddenCols()
}

function resetCols() {
  hiddenCols.value = new Set(allColumnDefs.filter(c => !c.defaultVisible).map(c => c.key))
  persistHiddenCols()
}

function renderCell(colKey, row) {
  switch (colKey) {
    case 'coverArt':
      if (!row.coverArt) return h('span', { style: { color: 'var(--ct-text-3)', fontSize: '11px' } }, '—')
      return h('img', {
        src: getCoverUrl(row.coverArt),
        style: 'width:32px;height:32px;border-radius:5px;object-fit:cover',
      })
    case 'name':
      return row.name ?? '—'
    case 'albumType':
      return TYPE_MAP[row.albumType] ?? row.albumType ?? '—'
    case 'albumYear':
      return row.albumYear ?? '—'
    case 'songCount':
      return row.songCount ?? '—'
    case 'company': {
      const txt = row.company
      if (!txt) return h('span', { style: { color: 'var(--ct-text-3)', fontSize: '11px' } }, '—')
      return txt
    }
    case 'language': {
      const txt = row.language
      if (!txt) return h('span', { style: { color: 'var(--ct-text-3)', fontSize: '11px' } }, '—')
      return txt
    }
    case 'introduction': {
      const txt = row.introduction
      if (!txt) return h('span', { style: { color: 'var(--ct-text-3)', fontSize: '11px' } }, '—')
      return txt.length > 60 ? txt.slice(0, 60) + '…' : txt
    }
    case 'enrichSource':
      return row.enrichSource ?? '—'
    default:
      return '—'
  }
}

const visibleColumns = computed(() => {
  const result = [
    { type: 'selection', width: 40 },
  ]
  for (const def of allColumnDefs) {
    if (!hiddenCols.value.has(def.key)) {
      result.push({
        ...def,
        key: def.key,
        render(row) { return renderCell(def.key, row) },
      })
    }
  }
  return result
})

// ── 行双击 ──
function rowProps(row) {
  return {
    style: 'cursor: pointer',
    ondblclick: () => openEdit(row),
  }
}

// ── 生命周期 ──
onMounted(() => {
  doQuery()
})
</script>

<style scoped>
.album-manager {
  display: flex;
  height: 100%;
}

/* ── 主内容区 ── */
.am-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 12px 16px 10px;
  gap: 8px;
  overflow: hidden;
}

/* ── 表格工具栏 ── */
.am-table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  padding: 4px 0;
}

.am-tt-left, .am-tt-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* ── 列 popover ── */
.am-col-popover {
  width: 200px;
  max-height: 300px;
  overflow-y: auto;
  padding: 4px 0;
}

.am-col-actions {
  display: flex;
  gap: 4px;
  padding: 0 8px;
}

.am-col-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  transition: background 0.1s;
}

.am-col-item:hover { background: var(--sb-bg-hover); }

.am-col-hidden-label {
  color: var(--ct-text-3);
}

/* ── 表格 ── */
.am-table {
  flex: 1;
  min-height: 0;
}

.am-table :deep(.n-data-table-th) {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--ct-bg);
}

.am-table :deep(.n-data-table-tr:hover) {
  background: var(--sb-bg-hover);
}

/* ── 空状态 ── */
.am-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ct-text-2);
  font-size: 14px;
}

/* ── 底栏 ── */
.am-footer {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 4px;
}

.am-footer-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.am-footer-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>
