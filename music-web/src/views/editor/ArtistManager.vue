<template>
  <div class="artist-manager">
    <!-- ═══ 左侧边栏 ═══ -->
    <ArtistSidebar
      :letter="letter"
      :select-mode="selectMode"
      :filter-min-songs="filterMinSongs"
      :filter-max-songs="filterMaxSongs"
      :filter-country="filterCountry"
      :loading="loading"
      :total="total"
      @update:letter="letter = $event"
      @update:select-mode="selectMode = $event"
      @update:filter-min-songs="filterMinSongs = $event"
      @update:filter-max-songs="filterMaxSongs = $event"
      @update:filter-country="filterCountry = $event"
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
            编辑
          </n-button>
        </div>
        <div class="am-tt-right">
          <n-popover trigger="click" placement="bottom-end">
            <template #trigger>
              <n-button size="tiny">
                <template #icon><n-icon :size="15"><SettingsOutline /></n-icon></template>
                显示字段
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
          :data="artists"
          :row-key="(row) => row.id"
          :checked-row-keys="selectedIds"
          :bordered="false"
          :single-line="false"
          size="small"
          class="am-table"
          :row-props="rowProps"
          @update:checked-row-keys="onCheckedChange"
        />
        <div v-if="!loading && artists.length === 0" class="am-empty">
          {{ $t('artistManager.noData') }}
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
              @click="openTool('artistMerge')"
            >
              {{ $t('artistManager.mergeSelected', { n: selectedIds.length }) }}
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
    <ArtistEditDrawer
      ref="editDrawerRef"
      :artist="editingArtist"
      @saved="onArtistSaved"
      @close="onDrawerClosed"
    />

    <!-- ═══ 工具弹窗 ═══ -->
    <n-modal
      v-model:show="enrichVisible"
      preset="card"
      :title="$t('artistManager.toolEnrich')"
      style="max-width:700px;width:700px"
      :mask-closable="true"
      @update:show="v => { if (!v) enrichVisible = false }"
    >
      <ArtistEnrichPanel
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
      :title="$t('artistManager.toolNormalize')"
      style="max-width:600px;width:600px"
      :mask-closable="true"
      @update:show="v => { if (!v) normalizeVisible = false }"
    >
      <ArtistNormalizePanel
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
      :title="$t('artistManager.toolMergePanelTitle')"
      style="max-width:600px;width:600px"
      :mask-closable="false"
      @update:show="v => { if (!v) mergeVisible = false }"
    >
      <ArtistMergePanel
        v-if="mergeVisible"
        :artists="artists"
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
import { useMessage, NButton, NIcon } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { getArtists } from '@/api/editor/artist-manage.js'
import { getCoverUrl } from '@/utils/mediaUrl.js'
import ArtistSidebar from '@/components/editor/artist/ArtistSidebar.vue'
import ArtistEnrichPanel from '@/components/editor/artist/ArtistEnrichPanel.vue'
import ArtistNormalizePanel from '@/components/editor/artist/ArtistNormalizePanel.vue'
import ArtistMergePanel from '@/components/editor/artist/ArtistMergePanel.vue'
import ArtistEditDrawer from '@/components/editor/artist/ArtistEditDrawer.vue'
import ToolBar from '@/components/editor/layout/ToolBar.vue'

const { t } = useI18n()
const message = useMessage()

// ── 过滤状态 ──
const selectMode = ref('all')
const letter = ref(null)
const keyword = ref('')
const filterMinSongs = ref(null)
const filterMaxSongs = ref(null)
const filterCountry = ref('')

// ── 批量工具 ──
const batchTools = computed(() => [
  {
    key: 'artistEnrich',
    label: t('artistManager.toolEnrich'),
    desc: t('artistManager.toolEnrichDesc'),
    icon: CloudDownloadOutline,
  },
  {
    key: 'artistNormalize',
    label: t('artistManager.toolNormalize'),
    desc: t('artistManager.toolNormalizeDesc'),
    icon: TextOutline,
  },
  {
    key: 'artistMerge',
    label: t('artistManager.toolMerge'),
    desc: t('artistManager.toolMergeDesc'),
    icon: LayersOutline,
  },
])

// ── 数据 ──
const artists = ref([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const pageSize = ref(50)
const selectedIds = ref([])

const currentSelection = computed(() => {
  const sel = { mode: selectMode.value }
  if (letter.value) sel.letter = letter.value
  if (keyword.value) sel.keyword = keyword.value
  if (filterMinSongs.value != null) sel.minSongs = filterMinSongs.value
  if (filterMaxSongs.value != null) sel.maxSongs = filterMaxSongs.value
  if (filterCountry.value) sel.country = filterCountry.value
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
  if (filterMinSongs.value != null) params.minSongs = filterMinSongs.value
  if (filterMaxSongs.value != null) params.maxSongs = filterMaxSongs.value
  if (filterCountry.value) params.country = filterCountry.value
  return params
}

async function doQuery() {
  loading.value = true
  selectedIds.value = []
  try {
    const res = await getArtists(buildQueryParams())
    artists.value = res?.artists || []
    total.value = res?.total || 0
  } catch (e) {
    console.error('[ArtistManager] 查询失败:', e)
    message.warning('查询失败')
  } finally {
    loading.value = false
  }
}

// ── 全局搜索（独立） ──
function onGlobalSearch(val) {
  keyword.value = val
  page.value = 1
  // 全局搜索用简单模式，不叠加过滤
  if (val) {
    selectMode.value = 'all'
    letter.value = null
    filterMinSongs.value = null
    filterMaxSongs.value = null
    filterCountry.value = ''
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
const editDrawerRef = ref(null)
const editingArtist = ref({
  id: null, name: '', gender: null, country: '', introduction: '', coverArt: '', enrichSource: '',
})

function openEdit(row) {
  editingArtist.value = row
  editDrawerRef.value?.open(false)
}

function openEditForSelected() {
  if (selectedIds.value.length !== 1) return
  const row = artists.value.find(a => a.id === selectedIds.value[0])
  if (row) openEdit(row)
}

function onArtistSaved() {
  message.success('艺术家已更新')
  doQuery()
}

function onDrawerClosed() {
  // no-op
}

// ── 工具弹窗 ──
const enrichVisible = ref(false)
const normalizeVisible = ref(false)
const mergeVisible = ref(false)

const TOOL_VISIBLE_MAP = {
  artistEnrich: enrichVisible,
  artistNormalize: normalizeVisible,
  artistMerge: mergeVisible,
}

function openTool(key) {
  const vis = TOOL_VISIBLE_MAP[key]
  if (vis) {
    if (key === 'artistMerge' && selectedIds.value.length < 2) {
      message.warning('请至少勾选 2 个艺术家进行合并')
      return
    }
    vis.value = true
  }
}

function onToolDone() {
  message.success(t('artistManager.pipelineSubmitted', { id: '—' }))
  doQuery()
}

function onMergeDone() {
  selectedIds.value = []
  doQuery()
}

// ── GENDER 映射 ──
const GENDER_MAP = { 0: '其他', 1: '男', 2: '女', 3: '组合' }

// ── 列定义 ──
const allColumnDefs = [
  { key: 'coverArt',  title: '封面',       width: 52,  align: 'center', defaultVisible: true },
  { key: 'name',      title: '名称',       width: 140, ellipsis: { tooltip: true }, defaultVisible: true },
  { key: 'introduction', title: '简介',     minWidth: 120, ellipsis: { tooltip: true }, defaultVisible: true },
  { key: 'albumCount',title: '专辑',       width: 48,  align: 'center', defaultVisible: true },
  { key: 'songCount', title: '歌曲',       width: 48,  align: 'center', defaultVisible: true },
  { key: 'gender',    title: '性别',       width: 48,  align: 'center', defaultVisible: true },
  { key: 'country',   title: '国家/地区',  width: 72,  ellipsis: { tooltip: true }, defaultVisible: true },
]

// ── 列可见性 localStorage ──
const STORAGE_KEY = 'music-web-artist-hidden-cols'
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
    case 'introduction': {
      const txt = row.introduction
      if (!txt) return h('span', { style: { color: 'var(--ct-text-3)', fontSize: '11px' } }, '—')
      return txt.length > 60 ? txt.slice(0, 60) + '…' : txt
    }
    case 'albumCount':
      return row.albumCount ?? '—'
    case 'songCount':
      return row.songCount ?? '—'
    case 'gender':
      return GENDER_MAP[row.gender] ?? '—'
    case 'country':
      return row.country ?? '—'
    default:
      return '—'
  }
}

const visibleColumns = computed(() => {
  const cols = [h('div', { style: 'display:none' })] // 占位，让 map 顺序正确
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
.artist-manager {
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
  background: var(--sb-bg-hover) !important;
}

.am-table :deep(.n-data-table-tr.n-data-table-tr--checked) {
  background: rgb(var(--ct-accent-rgb) / 0.1) !important;
}

.am-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
  font-size: 14px;
  color: var(--ct-text-3);
}

/* ── 底栏 ── */
.am-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 40px;
  flex-shrink: 0;
}

.am-footer-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.am-footer-right { margin-left: auto; }

.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
