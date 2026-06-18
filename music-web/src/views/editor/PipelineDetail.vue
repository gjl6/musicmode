<template>
  <div class="pipeline-detail">

    <div class="top-nav">
      <n-button text size="small" @click="$router.push('/pipelines')">
        <n-icon :size="16"><ArrowBackOutline /></n-icon>
        {{ $t('pipelineDetail.backToList') }}
      </n-button>
      <span class="top-nav-title">{{ $t('pipelineDetail.detailTitle') }}</span>
      <span class="top-nav-spacer"></span>
    </div>

    <n-spin :show="loading && !event">
      <template v-if="event">

        <div class="overview-card">
          <div class="overview-top">
            <div class="overview-left">
              <div class="overview-title-row">
                <span class="overview-template">{{ event.templateName || $t('pipelineDetail.pipelineTask') }}</span>
                <n-tag :type="stateTagType" :bordered="false" size="small" class="overview-state-tag">
                  {{ stateLabel }}
                </n-tag>
              </div>
              <div class="overview-meta">
                <span class="meta-id" :title="event.pipelineId">
                  ID: {{ event.pipelineId?.slice(0, 8) }}...
                </span>
                <span class="meta-sep">·</span>
                <span class="meta-time">{{ $t('pipelineDetail.createTime') }}: {{ formatTime(event.createdAt) }}</span>
              </div>
            </div>
            <div v-if="createdAt" class="overview-right">
              <span class="duration-label">{{ $t('pipelineDetail.duration') }}</span>
              <span class="duration-value" :class="{ 'duration-running': isRunning }">{{ durationDisplay }}</span>
            </div>
          </div>


          <n-progress
            type="line"
            :percentage="progressPct"
            :height="10"
            :color="progressColor"
            :indicator-text-color="progressColor"
            rail-color="var(--color-surface-hover)"
            class="main-progress"
          />


          <div class="stats-row">
            <div class="stat">
              <span class="stat-icon stat-icon-total">📁</span>
              <span class="stat-value">{{ event.totalFiles ?? 0 }}</span>
              <span class="stat-label">{{ $t('pipelineDetail.totalItems') }}</span>
            </div>
            <div class="stat">
              <span class="stat-icon stat-icon-success">✓</span>
              <span class="stat-value success">{{ event.successFiles ?? 0 }}</span>
              <span class="stat-label">{{ $t('pipelineDetail.processedItems') }}</span>
            </div>
            <div class="stat">
              <span class="stat-icon stat-icon-error">✗</span>
              <span class="stat-value" :class="{ error: (event.failedFiles ?? 0) > 0 }">{{ event.failedFiles ?? 0 }}</span>
              <span class="stat-label">{{ $t('pipelineDetail.errorCount') }}</span>
            </div>
          </div>
        </div>


        <div v-if="isRunning" class="action-bar">
          <n-button-group>
            <n-button v-if="event.state === 'RUNNING'" size="small" @click="handlePause" :loading="actionLoading">
              <template #icon><n-icon><PauseOutline /></n-icon></template>
              {{ $t('pipelineDetail.pause') }}
            </n-button>
            <n-button v-if="event.state === 'PAUSED'" type="primary" size="small" @click="handleResume" :loading="actionLoading">
              <template #icon><n-icon><PlayOutline /></n-icon></template>
              {{ $t('pipelineDetail.resume') }}
            </n-button>
            <n-button type="error" size="small" @click="handleCancel" :loading="actionLoading">
              <template #icon><n-icon><CloseOutline /></n-icon></template>
              {{ $t('pipelineDetail.cancel') }}
            </n-button>
          </n-button-group>
        </div>


        <div v-if="event.state === 'READY'" class="ready-notice">
          <n-icon :size="18"><TimeOutline /></n-icon>
          <span>{{ $t('pipelineDetail.readyHint') }}</span>
        </div>


        <div v-if="isDedup" class="results-card">
          <div class="results-header">
            <h4 class="results-title">{{ $t('pipelineDetail.dedupResult') }}</h4>
            <n-button
              v-if="isFinished"
              type="warning"
              size="small"
              ghost
              @click="dedupDeleteModalVisible = true"
            >
              <template #icon><n-icon :size="14"><TrashOutline /></n-icon></template>
              {{ $t('pipelineDetail.dedupDelete') }}
            </n-button>
          </div>


          <n-modal
            v-model:show="dedupDeleteModalVisible"
            preset="card"
            :title="$t('pipelineDetail.dedupDelete')"
            style="max-width: 560px; width: 560px"
            :mask-closable="true"
          >
            <div class="dedup-delete-modal-body">

              <div class="dedup-delete-field">
                <span class="dedup-delete-label">{{ $t('pipelineDetail.dedupDeleteRule') }}</span>
                <n-radio-group v-model:value="dedupDeleteRule" size="small">
                  <n-radio-button
                    v-for="opt in deleteRuleOptions"
                    :key="opt.value"
                    :value="opt.value"
                  >
                    {{ opt.label }}
                  </n-radio-button>
                </n-radio-group>
              </div>


              <div v-if="dedupDeleteRule === 'keepByDirectory'" class="dedup-delete-field">
                <span class="dedup-delete-label">{{ $t('pipelineDetail.dedupDeleteTargetDir') }}</span>
                <n-input
                  v-model:value="dedupDeleteDir"
                  size="small"
                  :placeholder="$t('pipelineDetail.dedupDeleteTargetDir')"
                />
              </div>
              <div v-if="dedupDeleteRule === 'keepPreferredFormat'" class="dedup-delete-field">
                <span class="dedup-delete-label">{{ $t('pipelineDetail.dedupDeleteFormatOrder') }}</span>
                <n-dynamic-tags v-model:value="dedupPreferredFormats" />
              </div>


              <n-alert type="warning" :bordered="false" class="dedup-delete-alert">
                <template v-if="dedupDeletePreviewCount > 0">
                  {{ $t('pipelineDetail.dedupDeletePreviewCount', { n: dedupDeletePreviewCount }) }}
                  <ul class="dedup-delete-preview-list">
                    <li v-for="(f, i) in dedupDeletePreview.slice(0, 10)" :key="i">
                      <span class="dedup-delete-preview-name">{{ extractFileName(f.path) }}</span>
                      <span class="dedup-delete-preview-dir">{{ extractDirName(f.path) }}</span>
                    </li>
                    <li v-if="dedupDeletePreview.length > 10">
                      ... 还有 {{ dedupDeletePreview.length - 10 }} 个文件
                    </li>
                  </ul>
                </template>
                <template v-else>
                  {{ $t('pipelineDetail.dedupDeleteNoFiles') }}
                </template>
              </n-alert>
            </div>
            <template #footer>
              <div class="dedup-delete-footer">
                <n-button @click="dedupDeleteModalVisible = false">
                  {{ $t('common.cancel') }}
                </n-button>
                <n-button
                  type="error"
                  :disabled="dedupDeletePreviewCount === 0"
                  @click="showDedupDeleteConfirm"
                >
                  {{ $t('pipelineDetail.dedupDeleteExecute') }}
                </n-button>
              </div>
            </template>
          </n-modal>

          <n-spin :show="dedupLoading">
            <template v-if="dedupStrategyTabs.length > 0">

              <n-tabs v-model:value="dedupActiveStrategy" type="segment" size="small" class="results-tabs">
                <n-tab-pane
                  v-for="tab in dedupStrategyTabs"
                  :key="tab.key"
                  :name="tab.key"
                  :tab="tab.label + ' ' + tab.count"
                />
              </n-tabs>


              <div v-if="dedupPageGroups.length > 0" class="dedup-groups-body">
                <div v-for="g in dedupPageGroups" :key="g.id || (g.strategy + '-' + g.groupIndex)" class="dedup-group-card">
                  <div class="dedup-group-header">
                    <span class="dedup-group-title">
                      {{ $t('pipelineDetail.dedupGroupIndex', { n: g.groupIndex + 1 }) }}
                    </span>
                    <span class="dedup-group-count">{{ $t('pipelineDetail.dedupFileCount', { n: g.fileList.length }) }}</span>
                  </div>
                  <div class="dedup-group-files">
                    <div
                      v-for="(f, fi) in g.fileList"
                      :key="fi"
                      class="dedup-group-file"
                      :class="{ 'dedup-file-primary': fi === 0 }"
                    >
                      <span class="dedup-file-icon">📄</span>
                      <n-tooltip trigger="hover" :delay="300" placement="top-start">
                        <template #trigger>
                          <span class="dedup-file-name">{{ extractFileName(f.path) }}</span>
                        </template>
                        <span class="path-tooltip">{{ f.path }}</span>
                      </n-tooltip>
                      <span class="dedup-file-tag">
                        <n-tag size="tiny" :bordered="false" :type="fi === 0 ? 'info' : 'default'">
                          {{ fi === 0 ? $t('pipelineDetail.dedupPrimary') : $t('pipelineDetail.dedupCopy') }}
                        </n-tag>
                      </span>
                      <span class="dedup-file-dir">{{ extractDirName(f.path) }}</span>
                      <n-button text size="tiny" type="error" class="dedup-file-delete-btn"
                        :disabled="!isFinished"
                        @click="handleDeleteSingleFileDialog(f)">
                        <n-icon :size="14"><TrashOutline /></n-icon>
                      </n-button>
                    </div>
                  </div>
                </div>
              </div>
              <n-empty v-if="!dedupLoading && dedupPageGroups.length === 0"
                :description="$t('pipelineDetail.dedupEmpty')" size="small" class="items-empty" />


              <n-pagination
                v-if="dedupTotalPages > 1"
                v-model:page="dedupCurrentPage"
                :page-size="dedupPageSize"
                :item-count="dedupCurrentStrategyGroups.length"
                size="small"
                class="results-pagination"
              />
            </template>
            <n-empty v-if="!dedupLoading && dedupStrategyTabs.length === 0"
              :description="$t('pipelineDetail.dedupEmpty')" size="small" class="items-empty" />
          </n-spin>
        </div>


        <div v-else class="results-card">
          <div class="results-header">
            <div class="results-header-left">
              <h4 class="results-title">{{ $t('pipelineDetail.moduleResults') }}</h4>
              <span v-if="isRunning && connected" class="live-badge">LIVE ●</span>
            </div>
            <div class="results-header-right">
              <n-input
                v-model:value="searchQuery"
                :placeholder="$t('pipelineDetail.searchItems')"
                clearable
                size="small"
                class="results-search"
              >
                <template #prefix>
                  <n-icon :size="14"><SearchOutline /></n-icon>
                </template>
              </n-input>
            </div>
          </div>


          <n-tabs v-model:value="activeTab" type="segment" size="small" @update:value="onTabChange" class="results-tabs">
            <n-tab-pane name="SUCCESS" :tab="tabLabel('SUCCESS')" />
            <n-tab-pane name="FAILED" :tab="tabLabel('FAILED')" />
          </n-tabs>


          <n-spin :show="itemsLoading">
            <div v-if="displayItems.length > 0" class="items-table-wrapper">
              <div class="items-table-header">
                <span class="col-path">{{ $t('pipelineDetail.itemFileName') }}</span>
                <span class="col-status">{{ $t('pipelineDetail.itemStatus') }}</span>
                <span class="col-action">{{ $t('pipelineDetail.itemAction') }}</span>
              </div>
              <div class="items-table-body" ref="itemsBodyEl">
                <div
                  v-for="item in displayItems"
                  :key="item.id || item.itemKey"
                  class="items-table-row"
                  :class="{ 'row-failed': item.status === 'FAILED' }"
                >
                  <n-tooltip trigger="hover" :delay="400" placement="top-start">
                    <template #trigger>
                      <span class="col-path">{{ extractFileName(item.itemKey) }}</span>
                    </template>
                    <span class="path-tooltip">{{ item.itemKey }}</span>
                  </n-tooltip>
                  <span class="col-status">
                    <n-tag v-if="item.status === 'SUCCESS' || item.status === 'OK'" type="success" size="tiny" :bordered="false">
                      OK
                    </n-tag>
                    <n-tooltip v-else trigger="hover" :delay="400">
                      <template #trigger>
                        <n-tag type="error" size="tiny" :bordered="false" class="error-tag">
                          {{ truncateError(item.errorMsg) }}
                        </n-tag>
                      </template>
                      {{ item.errorMsg }}
                    </n-tooltip>
                  </span>
                  <span class="col-action">
                    <n-button text size="tiny" @click="handleEditFile(item)" :disabled="editingFileId === item.itemKey">
                      <template #icon><n-icon :size="15"><CreateOutline /></n-icon></template>
                    </n-button>
                  </span>
                </div>
              </div>
            </div>
            <n-empty v-if="!itemsLoading && displayItems.length === 0"
              :description="$t('pipelineDetail.noItems')" size="small" class="items-empty" />
          </n-spin>


          <n-pagination
            v-if="useDbMode && dbTotal > pageSize"
            v-model:page="dbCurrentPage"
            :page-size="pageSize"
            :item-count="dbTotal"
            size="small"
            class="results-pagination"
            @update:page="(p) => loadDbPage(p)"
          />
        </div>


        <n-alert v-if="event.errorModule" type="error" class="error-card" :bordered="false">
          <template #header>
            <span class="error-module">[{{ event.errorModule }}]</span>
          </template>
          {{ event.errorMessage }}
        </n-alert>
      </template>

      <n-empty v-if="!loading && !event" :description="$t('pipelineDetail.notFound')" size="small" class="not-found" />
    </n-spin>


    <EditDrawer />
  </div>
</template>

<script setup>
import { computed, h, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useDialog, useMessage } from 'naive-ui'
import {
  ArrowBackOutline, PauseOutline, PlayOutline, CloseOutline, SearchOutline, CreateOutline, TimeOutline, TrashOutline
} from '@vicons/ionicons5'
import { fetchPipeline, fetchPipelineItems, fetchDedupGroups, executeDedupDelete, pausePipeline, resumePipeline, cancelPipeline } from '@/api/editor/pipeline.js'
import { fetchMetadata } from '@/api/editor/music.js'
import { usePipelineWebSocket } from '@/composables/editor/usePipelineWebSocket.js'
import { useEditStore } from '@/store/editor/edit.js'
import { useAppStore } from '@/store/editor/app.js'
import EditDrawer from '@/components/editor/metadata/EditDrawer.vue'

const route = useRoute()
const { t } = useI18n()
const dialog = useDialog()
const message = useMessage()

const { progressEvent, itemEvents, connect, disconnect, connected } = usePipelineWebSocket()
const editStore = useEditStore()
const appStore = useAppStore()

const loading = ref(true)
const actionLoading = ref(false)
const restEvent = ref(null)

const event = computed(() => restEvent.value)
const isDedup = computed(() => restEvent.value?.templateName === 'dedup')

const stateTagTypeMap = { RUNNING: 'info', PAUSED: 'warning', COMPLETED: 'success', FAILED: 'error', CANCELLED: 'default', READY: 'default' }
const stateTagType = computed(() => stateTagTypeMap[event.value?.state] || 'default')

const Colors = { error: '#d03050', success: '#18a058', info: '#2080f0' }
const progressColor = computed(() => {
  const s = event.value?.state
  if (s === 'FAILED') return Colors.error
  if (s === 'COMPLETED') return Colors.success
  return Colors.info
})

const isRunning = computed(() => event.value && (event.value.state === 'RUNNING' || event.value.state === 'PAUSED' || event.value.state === 'READY'))
const isFinished = computed(() => event.value && (event.value.state === 'COMPLETED' || event.value.state === 'FAILED' || event.value.state === 'CANCELLED'))

const progressPct = computed(() => {
  if (!event.value) return 0
  const done = (event.value.successFiles ?? 0) + (event.value.failedFiles ?? 0)
  const total = Math.max(event.value.totalFiles ?? 0, 1)
  return Math.min(100, Math.round(done / total * 100))
})

const stateLabel = computed(() => {
  const s = event.value?.state
  if (!s) return ''
  const key = 'pipelineDetail.state' + s.charAt(0) + s.slice(1).toLowerCase()
  const label = t(key)
  return label !== key ? label : s
})

const createdAt = computed(() => event.value?.createdAt || '')
const now = ref(Date.now())
const frozenDuration = ref(null)
let durationTimer = null

onMounted(() => {
  durationTimer = setInterval(() => { now.value = Date.now() }, 1000)
})
onBeforeUnmount(() => {
  if (durationTimer) { clearInterval(durationTimer); durationTimer = null }
})

function freezeDuration() {
  if (durationTimer) {
    clearInterval(durationTimer)
    durationTimer = null
  }
  if (createdAt.value) {
    try {
      const start = new Date(createdAt.value.replace(' ', 'T') + (createdAt.value.includes('+') ? '' : '+08:00')).getTime()
      if (!isNaN(start)) {
        frozenDuration.value = Math.max(0, Date.now() - start)
      }
    } catch {}
  }
}

watch(isFinished, (finished) => {
  if (finished) freezeDuration()
})

watch(event, (evt) => {
  if (evt && isFinished.value && frozenDuration.value == null) {
    freezeDuration()
  }
})

const durationDisplay = computed(() => {
    const backendMs = event.value?.durationMs
  const state = event.value?.state
    if (state === 'READY' || state === 'PAUSED') {
    if (backendMs > 0) {
      const h = Math.floor(backendMs / 3600000)
      const m = Math.floor((backendMs % 3600000) / 60000)
      const s = Math.floor((backendMs % 60000) / 1000)
      return String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0')
    }
    return '--:--:--'
  }
  if (backendMs > 0) {
    const diff = backendMs
    const h = Math.floor(diff / 3600000)
    const m = Math.floor((diff % 3600000) / 60000)
    const s = Math.floor((diff % 60000) / 1000)
    return String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0')
  }
    if (!createdAt.value) return '--:--:--'
  try {
    const start = new Date(createdAt.value.replace(' ', 'T') + (createdAt.value.includes('+') ? '' : '+08:00')).getTime()
    if (isNaN(start)) return '--:--:--'
    const diff = frozenDuration.value != null ? frozenDuration.value : Math.max(0, now.value - start)
    const h = Math.floor(diff / 3600000)
    const m = Math.floor((diff % 3600000) / 60000)
    const s = Math.floor((diff % 60000) / 1000)
    return String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0')
  } catch { return '--:--:--' }
})

function formatTime(ts) {
  if (!ts) return '-'
  const d = new Date(ts.replace(' ', 'T') + (ts.includes('+') ? '' : '+08:00'))
  if (isNaN(d.getTime())) return ts
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function extractFileName(fullPath) {
  if (!fullPath) return ''
  const idx = Math.max(fullPath.lastIndexOf('/'), fullPath.lastIndexOf('\\'))
  return idx >= 0 ? fullPath.slice(idx + 1) : fullPath
}

function truncateError(msg) {
  if (!msg) return ''
  return msg.length > 40 ? msg.slice(0, 40) + '…' : msg
}


const WS_CAP = 200

const wsAllItems = ref([])

watch(itemEvents, (items) => {
  if (!useDbMode.value && wsAllItems.value.length < WS_CAP) {
    wsAllItems.value = items.slice(-WS_CAP)
  }
})

watch(isFinished, (finished) => {
  if (finished) {
        loadDbPage(1)
  }
})

const useDbMode = computed(() => {
  if (isFinished.value) return true
  if (wsAllItems.value.length >= WS_CAP) return true
  return false
})

const activeTab = ref('SUCCESS')
const searchQuery = ref('')
const itemsLoading = ref(false)

const dbItems = ref([])
const dbTotal = ref(0)
const dbCurrentPage = ref(1)
const pageSize = 50

const successCount = ref(0)
const failedCount = ref(0)

watch(event, (evt) => {
  if (evt) {
    successCount.value = evt.successFiles ?? 0
    failedCount.value = evt.failedFiles ?? 0
  }
})

function tabLabel(status) {
  const n = status === 'SUCCESS' ? successCount.value : failedCount.value
  return status === 'SUCCESS'
    ? t('pipelineDetail.successItems', { n })
    : t('pipelineDetail.failedItems', { n })
}

function matchesSearch(item) {
  const kw = searchQuery.value.trim().toLowerCase()
  if (!kw) return true
  const key = (item.itemKey || '').toLowerCase()
  const fileName = extractFileName(item.itemKey).toLowerCase()
  return key.includes(kw) || fileName.includes(kw)
}

async function loadDbPage(page) {
  itemsLoading.value = true
  dbCurrentPage.value = page
  try {
    const res = await fetchPipelineItems(route.params.id, activeTab.value, page - 1, pageSize)
    dbItems.value = res.items || []
    dbTotal.value = res.total || 0
    if (activeTab.value === 'SUCCESS') successCount.value = res.total || 0
    else failedCount.value = res.total || 0
  } catch (e) {
    console.error('[PipelineDetail] 获取处理结果失败:', e)
  } finally {
    itemsLoading.value = false
  }
}

function onTabChange(tab) {
  activeTab.value = tab
  searchQuery.value = ''
  if (useDbMode.value) {
    loadDbPage(1)
  }
}

const displayItems = computed(() => {
  let items = useDbMode.value ? dbItems.value : wsAllItems.value

    if (!useDbMode.value) {
    items = items.filter(it => it.status === activeTab.value)
  }

    return items.filter(matchesSearch)
})


async function handlePause() {
  actionLoading.value = true
  try {
    await pausePipeline(route.params.id)
        const data = await fetchPipeline(route.params.id)
    restEvent.value = { ...restEvent.value, ...data }
  } catch (e) { console.error(e) }
  actionLoading.value = false
}
async function handleResume() {
  actionLoading.value = true
  try {
    await resumePipeline(route.params.id)
        const data = await fetchPipeline(route.params.id)
    restEvent.value = { ...restEvent.value, ...data }
  } catch (e) { console.error(e) }
  actionLoading.value = false
}
async function handleCancel() {
  actionLoading.value = true
  try {
    await cancelPipeline(route.params.id)
    const data = await fetchPipeline(route.params.id)
    restEvent.value = { ...restEvent.value, ...data }
  } catch (e) { console.error(e) }
  actionLoading.value = false
}

const dedupLoading = ref(false)
const dedupRawGroups = ref([])
const dedupActiveStrategy = ref('')
const dedupCurrentPage = ref(1)
const dedupPageSize = 20

const STRATEGY_LABEL_MAP = {
  hash: 'tool.dedupStrategyHash',
  filename: 'tool.dedupStrategyFileName',
  metadata: 'tool.dedupStrategyMetadata',
  fingerprint: 'tool.dedupStrategyFingerprint',
}

const dedupStrategyTabs = computed(() => {
  const map = new Map()
  for (const g of dedupRawGroups.value) {
    if (!map.has(g.strategy)) map.set(g.strategy, [])
    map.get(g.strategy).push(g)
  }
  return Array.from(map.entries()).map(([key, groups]) => ({
    key,
    label: t(STRATEGY_LABEL_MAP[key] || key),
    count: groups.length,
  }))
})

const dedupCurrentStrategyGroups = computed(() => {
  const groups = dedupRawGroups.value
    .filter(g => g.strategy === dedupActiveStrategy.value)
    .sort((a, b) => a.groupIndex - b.groupIndex)
  return groups.map(g => {
    let paths = []
    let names = []
    try { paths = JSON.parse(g.filePaths) } catch {}
    try { names = JSON.parse(g.fileNames) } catch {}
    const fileList = paths.map((p, i) => ({
      path: p,
      name: names[i] || extractFileName(p),
    }))
    return { ...g, fileList }
  })
})

const dedupPageGroups = computed(() => {
  const start = (dedupCurrentPage.value - 1) * dedupPageSize
  return dedupCurrentStrategyGroups.value.slice(start, start + dedupPageSize)
})

const dedupTotalPages = computed(() =>
  Math.ceil(dedupCurrentStrategyGroups.value.length / dedupPageSize)
)

watch(dedupStrategyTabs, (tabs) => {
  if (tabs.length > 0 && !tabs.find(t => t.key === dedupActiveStrategy.value)) {
    dedupActiveStrategy.value = tabs[0].key
  }
  dedupCurrentPage.value = 1
})

watch(dedupActiveStrategy, () => {
  dedupCurrentPage.value = 1
})

async function loadDedupGroups() {
  dedupLoading.value = true
  try {
    const data = await fetchDedupGroups(route.params.id)
    dedupRawGroups.value = Array.isArray(data) ? data : []
  } catch (e) {
    console.error('[PipelineDetail] 获取去重分组失败:', e)
    dedupRawGroups.value = []
  } finally {
    dedupLoading.value = false
  }
}

const dedupDeleteModalVisible = ref(false)
const dedupDeleteRule = ref('keepFirst')
const dedupDeleteDir = ref('')
const dedupPreferredFormats = ref(['flac', 'wav', 'ape', 'm4a', 'mp3'])
const dedupDeleteExecuting = ref(false)

const deleteRuleOptions = [
  { label: t('pipelineDetail.dedupDeleteRuleKeepFirst'), value: 'keepFirst' },
  { label: t('pipelineDetail.dedupDeleteRuleKeepByDir'), value: 'keepByDirectory' },
  { label: t('pipelineDetail.dedupDeleteRuleKeepFormat'), value: 'keepPreferredFormat' },
]

const audioFormatOptions = [
  { label: 'FLAC', value: 'flac' },
  { label: 'WAV', value: 'wav' },
  { label: 'APE', value: 'ape' },
  { label: 'AIFF', value: 'aiff' },
  { label: 'M4A (ALAC)', value: 'm4a' },
  { label: 'MP3', value: 'mp3' },
  { label: 'OGG', value: 'ogg' },
  { label: 'WMA', value: 'wma' },
]

const dedupDeletePreview = computed(() => {
  const toDelete = []
  const dir = dedupDeleteDir.value ? dedupDeleteDir.value.replace(/\\/g, '/') : ''
  const formats = dedupPreferredFormats.value.length > 0
    ? dedupPreferredFormats.value.map(f => f.toLowerCase())
    : ['flac', 'wav', 'ape', 'aiff', 'm4a', 'mp3', 'ogg', 'wma']

  for (const group of dedupCurrentStrategyGroups.value) {
    const files = group.fileList
    if (files.length <= 1) continue
    let keepIdx = 0
    if (dedupDeleteRule.value === 'keepByDirectory' && dir) {
      const idx = files.findIndex(f => f.path.replace(/\\/g, '/').includes(dir))
      if (idx >= 0) keepIdx = idx
    } else if (dedupDeleteRule.value === 'keepPreferredFormat') {
      let bestRank = Infinity
      files.forEach((f, i) => {
        const ext = (f.path.split('.').pop() || '').toLowerCase()
        const rank = formats.indexOf(ext)
        const r = rank === -1 ? 999 : rank
        if (r < bestRank) { bestRank = r; keepIdx = i }
      })
    }
    files.forEach((f, i) => { if (i !== keepIdx) toDelete.push(f) })
  }
  return toDelete
})

const dedupDeletePreviewCount = computed(() => dedupDeletePreview.value.length)

function handleDeleteSingleFileDialog(file) {
  const fileName = file.name || extractFileName(file.path)
  dialog.warning({
    title: t('pipelineDetail.dedupDelete'),
    content: t('pipelineDetail.dedupDeleteSingleConfirm'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: () => executeDeleteSingleFile(file.path),
  })
}

async function executeDeleteSingleFile(filePath) {
  dedupDeleteExecuting.value = true
  try {
    await executeDedupDelete(route.params.id, {
      action: 'single',
      filePath: filePath,
    })
    message.success(t('common.success'))
    await loadDedupGroups()
  } catch (e) {
    message.error(e?.response?.data?.error || t('common.error'))
  } finally {
    dedupDeleteExecuting.value = false
  }
}


function showDedupDeleteConfirm() {
  if (dedupDeletePreviewCount.value === 0) return
  const fileList = dedupDeletePreview.value.slice(0, 20).map(f =>
    h('div', { style: 'font-size:12px; line-height:1.8;' }, [
      h('span', { style: 'font-weight:500;' }, extractFileName(f.path)),
      h('span', { style: 'color:var(--color-text-tertiary); margin-left:8px;' }, extractDirName(f.path)),
    ])
  )
  if (dedupDeletePreview.value.length > 20) {
    fileList.push(h('div', { style: 'font-size:12px; color:var(--color-text-tertiary); margin-top:4px;' },
      `... 还有 ${dedupDeletePreview.value.length - 20} 个文件`))
  }
  dialog.warning({
    title: t('pipelineDetail.dedupDelete'),
    content: () => h('div', { style: 'display:flex; flex-direction:column; gap:8px;' }, [
      h('p', { style: 'margin:0;' },
        t('pipelineDetail.dedupDeleteConfirm', { n: dedupDeletePreviewCount.value })),
      h('div', { style: 'max-height:260px; overflow-y:auto;' }, fileList),
    ]),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: handleDedupDeleteExecute,
  })
}

async function handleDedupDeleteExecute() {
  if (dedupDeletePreviewCount.value === 0) {
    message.info(t('pipelineDetail.dedupDeleteNoFiles'))
    return
  }
  dedupDeleteExecuting.value = true
  try {
    const ruleOptions = {}
    if (dedupDeleteRule.value === 'keepByDirectory') {
      ruleOptions.directory = dedupDeleteDir.value
    } else if (dedupDeleteRule.value === 'keepPreferredFormat') {
      ruleOptions.preferredFormats = dedupPreferredFormats.value
    }
    const res = await executeDedupDelete(route.params.id, {
      action: 'rule',
      strategy: dedupActiveStrategy.value,
      rule: dedupDeleteRule.value,
      ruleOptions: ruleOptions,
    })
    const deletedFiles = res.deletedFiles ?? 0
    const deletedRecords = res.deletedRecords ?? 0
    message.success(t('pipelineDetail.dedupDeleteResult', { n: deletedFiles, m: deletedRecords }))
    dedupDeleteModalVisible.value = false
    await loadDedupGroups()
  } catch (e) {
    message.error(e?.response?.data?.error || t('common.error'))
  } finally {
    dedupDeleteExecuting.value = false
  }
}


function extractDirName(fullPath) {
  if (!fullPath) return ''
  const idx = Math.max(fullPath.lastIndexOf('/'), fullPath.lastIndexOf('\\'))
  if (idx < 0) return ''
    const parent = fullPath.slice(0, idx)
  const idx2 = Math.max(parent.lastIndexOf('/'), parent.lastIndexOf('\\'))
  return idx2 >= 0 ? parent.slice(idx2 + 1) : parent
}

const editingFileId = ref(null)

async function handleEditFile(item) {
  const filePath = item.itemKey
  if (!filePath) return

  const fileObj = {
    id: filePath,
    path: filePath,
    fileName: extractFileName(filePath),
    name: extractFileName(filePath),
  }

  editingFileId.value = filePath
  appStore.openDrawer()
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      editStore.startEdit(fileObj, {
        title: '', artist: '', album: '', year: 0, genre: '',
      })
      fetchMetadata(filePath)
        .then(res => editStore.refreshMeta(fileObj, res))
        .catch(() => {})
        .finally(() => { editingFileId.value = null })
    })
  })
}


async function load() {
  try {
    const data = await fetchPipeline(route.params.id)
    restEvent.value = { ...restEvent.value, ...data }
    successCount.value = data.successFiles ?? 0
    failedCount.value = data.failedFiles ?? 0
        if (data.templateName === 'dedup') {
      await loadDedupGroups()
    } else if (data.state === 'COMPLETED' || data.state === 'FAILED' || data.state === 'CANCELLED') {
      await loadDbPage(1)
    }
  } catch (e) {
    console.error('[PipelineDetail] 加载详情失败:', e)
  } finally {
    loading.value = false
  }
}

watch(progressEvent, (evt) => {
  if (evt) restEvent.value = { ...restEvent.value, ...evt }
})

watch(() => route.params.id, (newId) => {
  if (newId) {
    loading.value = true
    restEvent.value = null
    wsAllItems.value = []
    dbItems.value = []
    disconnect()
    load()
    connect(newId)
  }
})

onMounted(() => {
  load()
  connect(route.params.id)
})
</script>

<style scoped>
.pipeline-detail {
  padding: 16px 32px;
  height: 100%;
  overflow-y: auto;
}


.top-nav {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
  position: relative;
}
.top-nav-title {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
}
.top-nav-spacer { flex: 1; }


.overview-card {
  background: var(--color-surface);
  border: var(--border-width-default) solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 20px;
  margin-bottom: 16px;
}
.overview-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 14px;
}
.overview-left { flex: 1; min-width: 0; }
.overview-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex-shrink: 0;
  margin-left: 16px;
}
.overview-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.overview-template {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text);
}
.overview-state-tag { flex-shrink: 0; }
.overview-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-tertiary);
}
.meta-id {
  font-family: var(--font-family-mono);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta-sep { color: var(--color-border); }
.duration-label {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-bottom: 2px;
}
.duration-value {
  font-size: 24px;
  font-weight: 700;
  font-family: var(--font-family-mono);
  color: var(--color-text-secondary);
  transition: color 0.3s;
}
.duration-running { color: var(--color-primary); }

.main-progress { margin-bottom: 16px; }


.stats-row {
  display: flex;
  gap: 40px;
}
.stat { display: flex; align-items: center; gap: 8px; }
.stat-icon { font-size: 18px; width: 28px; text-align: center; flex-shrink: 0; }
.stat-value {
  font-size: 26px;
  font-weight: 700;
  font-family: var(--font-family-mono);
}
.stat-value.success { color: var(--color-success); }
.stat-value.error { color: var(--color-destructive); }
.stat-label { font-size: 13px; color: var(--color-text-tertiary); }


.action-bar {
  margin-bottom: 16px;
}


.ready-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 16px;
  background: var(--color-warning-light, #fef3c7);
  border: 1px solid var(--color-warning, #f59e0b);
  border-radius: var(--radius-md, 6px);
  color: var(--color-warning-text, #92400e);
  font-size: 13px;
}


.results-card {
  background: var(--color-surface);
  border: var(--border-width-default) solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 16px 20px;
  margin-bottom: 16px;
}
.results-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.results-header-left { display: flex; align-items: center; gap: 10px; }
.results-title { margin: 0; font-size: 14px; font-weight: 600; }
.live-badge {
  font-size: 11px;
  color: var(--color-success);
  font-weight: 600;
  letter-spacing: 1px;
  animation: live-pulse 2s ease-in-out infinite;
}
@keyframes live-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
.results-search { width: 200px; }
.results-tabs { margin-bottom: 8px; }

.items-table-wrapper {
  max-height: 420px;
  overflow-y: auto;
}
.items-table-header {
  display: flex;
  align-items: center;
  padding: 7px 10px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-tertiary);
  border-bottom: var(--border-width-default) solid var(--color-border);
  position: sticky;
  top: 0;
  background: var(--color-surface);
  z-index: 1;
}
.items-table-body { }
.items-table-row {
  display: flex;
  align-items: center;
  padding: 8px 10px;
  border-bottom: var(--border-width-default) solid var(--color-border);
  font-family: var(--font-family-mono);
  font-size: 13px;
  transition: background 0.15s;
}
.items-table-row:hover { background: var(--color-surface-hover); }
.items-table-row:last-child { border-bottom: none; }
.items-table-row.row-failed { background: rgba(var(--color-destructive-rgb), 0.03); }
.col-path {
  flex: 2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text);
  cursor: default;
  min-width: 0;
}
.col-status {
  flex: 2;
  margin-left: 16px;
  min-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.col-action {
  flex-shrink: 0;
  margin-left: 16px;
  margin-right: 6px;
  width: 28px;
  text-align: center;
}
.error-tag {
  max-width: 100%;
  overflow: hidden;
}
.path-tooltip {
  font-family: var(--font-family-mono);
  font-size: 11px;
  word-break: break-all;
  max-width: 500px;
  display: inline-block;
}
.items-empty { padding: 24px 0; }
.results-pagination { margin-top: 10px; justify-content: flex-end; }


.results-tabs :deep(.n-tabs-tab--active) {
  color: var(--color-primary) !important;
  font-weight: 600;
}

.dedup-groups-body {

  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.dedup-group-card {
  background: var(--color-surface-hover);
  border: var(--border-width-default) solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}
.dedup-group-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--color-surface);
  border-bottom: var(--border-width-default) solid var(--color-border);
}
.dedup-group-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text);
}
.dedup-group-count {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.dedup-group-file {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: var(--border-width-default) solid var(--color-border);
  font-family: var(--font-family-mono);
  font-size: 12px;
  transition: background 0.15s;
}
.dedup-group-file:last-child { border-bottom: none; }
.dedup-group-file:hover { background: var(--color-surface); }
.dedup-file-primary { background: rgba(var(--color-primary-rgb, 32 128 240), 0.04); }
.dedup-file-icon {
  font-size: 16px;
  flex-shrink: 0;
}
.dedup-file-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text);
  cursor: default;
  min-width: 0;
  max-width: 280px;
}
.dedup-file-tag {
  flex-shrink: 0;
}
.dedup-file-dir {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-tertiary);
  font-size: 12px;
  min-width: 0;
}
.dedup-file-delete-btn {
  flex-shrink: 0;
  margin-left: auto;
}


.dedup-delete-modal-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.dedup-delete-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.dedup-delete-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text);
}
.dedup-delete-alert {
  margin-top: 4px;
}
.dedup-delete-preview-list {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 12px;
  max-height: 180px;
  overflow-y: auto;
}
.dedup-delete-preview-name {
  font-weight: 500;
}
.dedup-delete-preview-dir {
  margin-left: 8px;
  color: var(--color-text-tertiary);
}
.dedup-delete-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}


.error-card { margin-bottom: 16px; }
.error-module {
  font-family: var(--font-family-mono);
  color: var(--color-destructive);
}

.not-found { margin-top: 60px; }
</style>
