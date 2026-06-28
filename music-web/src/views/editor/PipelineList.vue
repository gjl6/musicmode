<template>
  <div class="pipeline-list">
    <div class="page-header">
      <h2 class="page-title">{{ $t('nav.operationLog') }}</h2>
      <span class="log-count">{{ filteredCount }} {{ $t('pipelineList.records') }}</span>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <n-input
        v-model:value="searchQuery"
        :placeholder="$t('pipelineList.filterByKeyword')"
        clearable
        class="filter-search"
      >
        <template #prefix>
          <n-icon :size="16"><SearchOutline /></n-icon>
        </template>
      </n-input>
      <n-select
        v-model:value="filterLevel"
        :options="levelOptions"
        :placeholder="$t('pipelineList.filterByLevel')"
        clearable
        class="filter-select"
      />
      <n-select
        v-model:value="filterState"
        :options="stateOptions"
        :placeholder="$t('pipelineList.filterByState')"
        clearable
        class="filter-select"
      />
    </div>

    <!-- 表格 -->
    <n-spin :show="loading">
      <n-data-table
        v-if="!loading && displayList.length > 0"
        :columns="columns"
        :data="pagedData"
        :row-key="(r) => r.pipelineId"
        :row-props="rowProps"
        :expanded-row-keys="expandedKeys"
        size="small"
        :single-line="false"
        class="log-table"
        @update:expanded-row-keys="onExpandUpdate"
      />

      <n-empty
        v-if="!loading && displayList.length === 0"
        :description="$t('pipelineList.noPipelines')"
        size="small"
      />
    </n-spin>

    <!-- 分页 -->
    <n-pagination
      v-if="filteredCount > pageSize"
      v-model:page="currentPage"
      :page-size="pageSize"
      :item-count="filteredCount"
      size="small"
      class="list-pagination"
    />
  </div>
</template>

<script setup>
import { computed, h, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { NTag, NText } from 'naive-ui'
import { SearchOutline } from '@vicons/ionicons5'
import { fetchPipelines } from '@/api/editor/pipeline.js'

const { t } = useI18n()
const router = useRouter()

// ── 数据 ──
const rawData = ref([])
const loading = ref(true)
let timer = null

// ── 筛选状态 ──
const searchQuery = ref('')
const debouncedSearch = ref('')
const filterLevel = ref(null)
const filterState = ref(null)
const currentPage = ref(1)
const expandedKeys = ref([])
const pageSize = 20

let debounceTimer = null
watch(searchQuery, (val) => {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    debouncedSearch.value = val
    currentPage.value = 1
  }, 300)
})
watch([filterLevel, filterState], () => {
  currentPage.value = 1
})

// ── 等级选项 ──
const levelOptions = computed(() => [
  { label: t('pipelineList.allLevels'), value: null },
  { label: t('pipelineList.levelInfo'), value: 'INFO' },
  { label: t('pipelineList.levelSuccess'), value: 'SUCCESS' },
  { label: t('pipelineList.levelWarn'), value: 'WARN' },
  { label: t('pipelineList.levelError'), value: 'ERROR' },
])

// ── 状态选项 ──
const stateOptions = computed(() => [
  { label: t('pipelineList.allStates'), value: null },
  { label: t('pipelineList.stateRunning'), value: 'RUNNING' },
  { label: t('pipelineList.statePaused'), value: 'PAUSED' },
  { label: t('pipelineList.stateCompleted'), value: 'COMPLETED' },
  { label: t('pipelineList.stateFailed'), value: 'FAILED' },
  { label: t('pipelineList.stateCancelled'), value: 'CANCELLED' },
])

// ── 颜色常量（JS 中用，CSS 变量在 v-bind 中不解析） ──
const LevelColors = { INFO: '#2080f0', SUCCESS: '#18a058', WARN: '#f0a020', ERROR: '#d03050' }
const StateColor = {
  READY: 'default',
  RUNNING: 'info',
  PAUSED: 'warning',
  COMPLETED: 'success',
  FAILED: 'error',
  CANCELLED: 'default',
}

// ── 派生字段 ──
function deriveLevel(row) {
  if (row.state === 'FAILED') return 'ERROR'
  if (row.errorModule && row.errorModule.length > 0) return 'WARN'
  if ((row.failedFiles || 0) > 0 && row.state === 'COMPLETED') return 'WARN'
  if (row.state === 'CANCELLED') return 'WARN'
  if (row.state === 'COMPLETED') return 'SUCCESS'
  return 'INFO'
}

function deriveOperation(row) {
  if (row.templateName && row.templateName.length > 0) return row.templateName
  if (row.errorModule && row.errorModule.length > 0) return row.errorModule
  return t('pipelineList.pipelineTask')
}

function deriveDetail(row) {
  const total = row.totalFiles ?? 0
  const success = row.successFiles ?? 0
  const failed = row.failedFiles ?? 0
  const stateKey = 'state' + (row.state || '').charAt(0) + (row.state || '').slice(1).toLowerCase()
  const stateLabel = t('pipelineList.' + stateKey) !== 'pipelineList.' + stateKey ? t('pipelineList.' + stateKey) : row.state
  let detail = stateLabel + ' · ' + t('pipelineList.detailSummary', { total, success, failed })
  if (row.errorMessage && row.errorMessage.length > 0) {
    const msg = row.errorMessage.length > 60 ? row.errorMessage.slice(0, 60) + '…' : row.errorMessage
    detail += ' · ' + msg
  }
  return detail
}

// 给 raw data 增强派生字段
const enrichedData = computed(() =>
  rawData.value.map((row) => ({
    ...row,
    _level: deriveLevel(row),
    _operation: deriveOperation(row),
    _detail: deriveDetail(row),
  }))
)

// ── 筛选链 ──
const displayList = computed(() => {
  let list = enrichedData.value

  // 关键词搜索
  const kw = debouncedSearch.value.trim().toLowerCase()
  if (kw) {
    list = list.filter((r) =>
      r._operation.toLowerCase().includes(kw)
      || r.pipelineId.toLowerCase().includes(kw)
      || (r.templateName && r.templateName.toLowerCase().includes(kw))
      || (r.errorMessage && r.errorMessage.toLowerCase().includes(kw))
    )
  }

  // 等级筛选
  if (filterLevel.value) {
    list = list.filter((r) => r._level === filterLevel.value)
  }

  // 状态筛选
  if (filterState.value) {
    list = list.filter((r) => r.state === filterState.value)
  }

  return list
})

const filteredCount = computed(() => displayList.value.length)

const pagedData = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return displayList.value.slice(start, start + pageSize)
})

// ── 表格列 ──
const columns = computed(() => [
  {
    type: 'expand',
    expandable: (row) => true,
    renderExpand: (row) =>
      h('div', { class: 'expanded-detail' }, [
        h('div', { class: 'expand-row' }, [
          h('span', { class: 'expand-label' }, t('pipelineList.pipelineIdFull')),
          h('span', { class: 'expand-value mono' }, row.pipelineId),
        ]),
        row.errorModule
          ? h('div', { class: 'expand-row' }, [
              h('span', { class: 'expand-label' }, t('pipelineList.errorModule')),
              h('span', { class: 'expand-value error' }, row.errorModule),
            ])
          : null,
        row.errorMessage
          ? h('div', { class: 'expand-row' }, [
              h('span', { class: 'expand-label' }, t('pipelineList.errorDetail')),
              h('span', { class: 'expand-value' }, row.errorMessage),
            ])
          : null,
        h('div', { class: 'expand-row' }, [
          h('span', { class: 'expand-label' }, t('pipelineList.createTime')),
          h('span', { class: 'expand-value' }, formatTime(row.createdAt)),
        ]),
      ].filter(Boolean)),
  },
  {
    title: t('pipelineList.id'),
    key: 'pipelineId',
    width: 100,
    render: (row) => h(NText, { depth: 2, class: 'mono' }, { default: () => (row.pipelineId || '').slice(0, 8) }),
  },
  {
    title: t('pipelineList.operation'),
    key: '_operation',
    width: 130,
    ellipsis: { tooltip: true },
    render: (row) => h(NText, { depth: 2 }, { default: () => row._operation }),
  },
  {
    title: t('pipelineList.operationDetail'),
    key: '_detail',
    ellipsis: { tooltip: true },
    render: (row) => h(NText, {
      depth: 3,
      style: row._level === 'ERROR' ? { color: LevelColors.ERROR } : undefined,
    }, { default: () => row._detail }),
  },
  {
    title: t('pipelineList.level'),
    key: '_level',
    width: 75,
    render: (row) => {
      const typeMap = { INFO: 'info', SUCCESS: 'success', WARN: 'warning', ERROR: 'error' }
      const label = t('pipelineList.level' + row._level.charAt(0) + row._level.slice(1).toLowerCase())
      return h(NTag, { type: typeMap[row._level] || 'default', size: 'small', bordered: false }, { default: () => label })
    },
  },
  {
    title: t('pipelineList.state'),
    key: 'state',
    width: 80,
    render: (row) => {
      const typeMap = StateColor
      const stateKey = 'state' + (row.state || '').charAt(0) + (row.state || '').slice(1).toLowerCase()
      const label = t('pipelineList.' + stateKey)
      return h(NTag, { type: typeMap[row.state] || 'default', size: 'small', bordered: false }, { default: () => label !== 'pipelineList.' + stateKey ? label : row.state })
    },
  },
  {
    title: t('pipelineList.createTime'),
    key: 'createdAt',
    width: 155,
    render: (row) => h(NText, { depth: 3 }, { default: () => formatTime(row.createdAt) }),
  },
])

// ── 行点击 ──
function rowProps(row) {
  return {
    style: 'cursor: pointer;',
    onClick: () => router.push('/pipeline/' + row.pipelineId),
  }
}

function onExpandUpdate(keys) {
  expandedKeys.value = keys
}

// ── 数据加载 ──
async function load() {
  try {
    const data = await fetchPipelines()
    rawData.value = Array.isArray(data) ? data : []
  } catch (e) {
    console.error('[PipelineList] 加载列表失败:', e)
  } finally {
    loading.value = false
  }
}

function formatTime(ts) {
  if (!ts) return '-'
  const d = new Date(ts)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

onMounted(() => {
  load()
  timer = setInterval(load, 5000)
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  clearTimeout(debounceTimer)
})
</script>

<style scoped>
.pipeline-list {
  padding: 16px 24px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.page-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 12px;
}

.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.log-count {
  font-size: 13px;
  color: var(--color-text-tertiary);
}

/* ── 筛选栏 ── */
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
  flex-shrink: 0;
}

.filter-search {
  flex: 1;
  max-width: 280px;
}

.filter-select {
  width: 130px;
  flex-shrink: 0;
}

/* ── 表格 ── */
.log-table {
  flex: 1;
  overflow: auto;
}

/* ── 展开行 ── */
.expanded-detail {
  padding: 8px 16px 12px;
  background: var(--gradient-card, var(--color-surface));
  border-top: var(--border-width-default) solid var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.expand-row {
  display: flex;
  gap: 12px;
  font-size: 12px;
}

.expand-label {
  color: var(--color-text-tertiary);
  min-width: 70px;
  flex-shrink: 0;
}

.expand-value {
  color: var(--color-text-secondary);
  word-break: break-all;
}

.expand-value.mono {
  font-family: var(--font-family-mono);
  font-size: 11px;
}

.expand-value.error {
  color: var(--color-destructive);
}

/* ── 分页 ── */
.list-pagination {
  margin-top: 12px;
  justify-content: flex-end;
  flex-shrink: 0;
}

/* ── 覆盖 n-spin 容器 ── */
:deep(.n-spin-container) {
  flex: 1;
  display: flex;
  flex-direction: column;
}
</style>
