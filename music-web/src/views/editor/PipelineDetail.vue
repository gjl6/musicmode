<template>
  <div class="pipeline-detail">
    <!-- ═══ 顶部导航栏 ═══ -->
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
        <!-- ═══ 概览信息卡片 ═══ -->
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

          <!-- 进度条 -->
          <n-progress
            type="line"
            :percentage="progressPct"
            :height="10"
            :color="progressColor"
            :indicator-text-color="progressColor"
            rail-color="var(--color-surface-hover)"
            class="main-progress"
          />

          <!-- 统计数字 -->
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

        <!-- ═══ 操作按钮组 ═══ -->
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

        <!-- READY 排队等待提示 -->
        <div v-if="event.state === 'READY'" class="ready-notice">
          <n-icon :size="18"><TimeOutline /></n-icon>
          <span>{{ $t('pipelineDetail.readyHint') }}</span>
        </div>

        <!-- ═══ 去重结果（仅 dedup 模板）═══ -->
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

          <!-- 自动删除弹窗 -->
          <n-modal
            v-model:show="dedupDeleteModalVisible"
            preset="card"
            :title="$t('pipelineDetail.dedupDelete')"
            style="max-width: 560px; width: 560px"
            :mask-closable="true"
          >
            <div class="dedup-delete-modal-body">
              <!-- 规则选择 -->
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

              <!-- 条件（按规则动态显示） -->
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

              <!-- 预览统计 -->
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
              <!-- 策略 Tab -->
              <n-tabs v-model:value="dedupActiveStrategy" type="segment" size="small" class="results-tabs">
                <n-tab-pane
                  v-for="tab in dedupStrategyTabs"
                  :key="tab.key"
                  :name="tab.key"
                  :tab="tab.label + ' ' + tab.count"
                />
              </n-tabs>

              <!-- 分组列表 -->
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

              <!-- 分页 -->
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

        <!-- ═══ 普通处理结果列表（非 dedup 模板）═══ -->
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

          <!-- 分类标签 -->
          <n-tabs v-model:value="activeTab" type="segment" size="small" @update:value="onTabChange" class="results-tabs">
            <n-tab-pane name="SUCCESS" :tab="tabLabel('SUCCESS')" />
            <n-tab-pane name="FAILED" :tab="tabLabel('FAILED')" />
          </n-tabs>

          <!-- 列表内容 -->
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

          <!-- 分页（运行中 + 完成后都显示） -->
          <n-pagination
            v-if="paginationTotal > pageSize"
            v-model:page="currentPage"
            :page-size="pageSize"
            :item-count="paginationTotal"
            size="small"
            class="results-pagination"
            @update:page="onPageChange"
          />
        </div>

        <!-- ═══ 错误信息卡片 ═══ -->
        <n-alert v-if="event.errorModule" type="error" class="error-card" :bordered="false">
          <template #header>
            <span class="error-module">[{{ event.errorModule }}]</span>
          </template>
          {{ event.errorMessage }}
        </n-alert>
      </template>

      <n-empty v-if="!loading && !event" :description="$t('pipelineDetail.notFound')" size="small" class="not-found" />
    </n-spin>

    <!-- 编辑抽屉 -->
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

// ── 状态 ──
const loading = ref(true)
const actionLoading = ref(false)
const restEvent = ref(null)

// restEvent 已合并 WS 进度数据，始终包含 templateName 等 REST 独有字段
const event = computed(() => restEvent.value)
const isDedup = computed(() => restEvent.value?.templateName === 'dedup')

// ── 状态颜色映射 ──
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

// ── 运行时长 ──
const createdAt = computed(() => event.value?.createdAt || '')
const now = ref(Date.now())
const frozenDuration = ref(null)  // 管道结束时冻结的最终时长
let durationTimer = null

onMounted(() => {
  durationTimer = setInterval(() => { now.value = Date.now() }, 1000)
})
onBeforeUnmount(() => {
  if (durationTimer) { clearInterval(durationTimer); durationTimer = null }
})

// 冻结时长：管道结束时停止计时，页面刷新时从 createdAt 估算
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

// 页面刷新后，已完成的管道直接冻结
watch(event, (evt) => {
  if (evt && isFinished.value && frozenDuration.value == null) {
    freezeDuration()
  }
})

const durationDisplay = computed(() => {
  // 优先使用后端推送的有效时长（已扣除暂停时间）
  const backendMs = event.value?.durationMs
  const state = event.value?.state
  // READY / PAUSED 状态：时长冻结，用后端值，不回退到实时 now
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
  // 回退：客户端根据 createdAt 估算（运行中 / 刚刷新时后端尚未推送 durationMs）
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

// ── 时间格式化 ──
function formatTime(ts) {
  if (!ts) return '-'
  const d = new Date(ts.replace(' ', 'T') + (ts.includes('+') ? '' : '+08:00'))
  if (isNaN(d.getTime())) return ts
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// ── 文件路径处理 ──
function extractFileName(fullPath) {
  if (!fullPath) return ''
  const idx = Math.max(fullPath.lastIndexOf('/'), fullPath.lastIndexOf('\\'))
  return idx >= 0 ? fullPath.slice(idx + 1) : fullPath
}

function truncateError(msg) {
  if (!msg) return ''
  return msg.length > 40 ? msg.slice(0, 40) + '…' : msg
}

// ═══════════════════════════════════════════════════════════════
// 合并列表：WS（最新200条快照）+ DB 分页（管道结束后）
// ═══════════════════════════════════════════════════════════════

// WS 实时推送的 items（保留原始格式，不做过滤）
const wsAllItems = ref([])

// 后端每次发送完整快照，直接替换
watch(itemEvents, (items) => {
  if (!isFinished.value) {
    wsAllItems.value = items
  }
})

// 管道完成时：加载 DB 第 1 页，就绪后翻回首页切换到 DB 模式
watch(isFinished, (finished) => {
  if (finished && !dbReady.value) {
    loadDbPage(1).then(() => {
      dbReady.value = true
      currentPage.value = 1
    })
  }
})

// 是否使用纯 DB 分页模式（管道结束 + DB 首屏就绪后才切换）
const useDbMode = computed(() => {
  return isFinished.value && dbReady.value
})

// Tab / 搜索
const activeTab = ref('SUCCESS')
const searchQuery = ref('')
const itemsLoading = ref(false)

// DB 分页数据
const dbItems = ref([])
const dbTotal = ref(0)
const dbCurrentPage = ref(1)
const dbReady = ref(false)        // 管道结束后 DB 首屏加载完成才切换到纯 DB 模式
const currentPage = ref(1)        // 统一分页 ref（运行中 + 完成后共用）
const pageSize = 50

// Tab 计数
const successCount = ref(0)
const failedCount = ref(0)

// 从进度事件更新计数
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

// 模糊搜索匹配（文件名或路径中包含关键词即可）
function matchesSearch(item) {
  const kw = searchQuery.value.trim().toLowerCase()
  if (!kw) return true
  const key = (item.itemKey || '').toLowerCase()
  const fileName = extractFileName(item.itemKey).toLowerCase()
  return key.includes(kw) || fileName.includes(kw)
}

// DB 模式下的数据
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
  currentPage.value = 1
  searchQuery.value = ''
  if (useDbMode.value) {
    loadDbPage(1)
  }
}

// 最终展示列表：运行中前 4 页走 WS，第 5 页起走 DB；完成后纯 DB
const displayItems = computed(() => {
  const page = currentPage.value

  if (useDbMode.value) {
    // 完成后：纯 DB 分页
    return (dbItems.value || []).filter(matchesSearch)
  }

  // 运行中：前 4 页 WS 实时数据，第 5 页起 DB
  const tab = activeTab.value
  if (page <= 4) {
    const wsOfTab = wsAllItems.value.filter(it => it.status === tab)
    const start = (page - 1) * pageSize
    return wsOfTab.slice(start, start + pageSize).filter(matchesSearch)
  }
  // page >= 5：走 DB
  return (dbItems.value || []).filter(matchesSearch)
})

// 分页总数：完成后取 DB 精确值，运行中取 progressEvent 聚合计数
const paginationTotal = computed(() => {
  if (useDbMode.value) return dbTotal.value
  return activeTab.value === 'SUCCESS' ? successCount.value : failedCount.value
})

// 翻页回调
function onPageChange(page) {
  currentPage.value = page
  if (useDbMode.value) {
    loadDbPage(page)
  } else if (page > 4) {
    loadRunningDbPage(page)
  }
  // page 1-4 运行中：无需 API 调用，displayItems 自动从 wsAllItems slice
}

// 运行中加载 DB 页（不覆盖 tab 计数，计数来自 progressEvent）
async function loadRunningDbPage(page) {
  itemsLoading.value = true
  try {
    const res = await fetchPipelineItems(route.params.id, activeTab.value, page - 1, pageSize)
    dbItems.value = res.items || []
  } catch (e) {
    console.error('[PipelineDetail] 获取结果失败:', e)
  } finally {
    itemsLoading.value = false
  }
}

// ═══════════════════════════════════════════════════════════════
// 操作按钮
// ═══════════════════════════════════════════════════════════════

async function handlePause() {
  actionLoading.value = true
  try {
    await pausePipeline(route.params.id)
    // 立即刷新状态，不等 WebSocket
    const data = await fetchPipeline(route.params.id)
    restEvent.value = { ...restEvent.value, ...data }
  } catch (e) { console.error(e) }
  actionLoading.value = false
}
async function handleResume() {
  actionLoading.value = true
  try {
    await resumePipeline(route.params.id)
    // 立即刷新状态，不等 WebSocket
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

// ── 去重结果 ──
const dedupLoading = ref(false)
const dedupRawGroups = ref([])         // 后端返回的原始分组 [{pipelineId, strategy, groupIndex, filePaths, fileNames}, ...]
const dedupActiveStrategy = ref('')    // 当前选中的策略 tab
const dedupCurrentPage = ref(1)
const dedupPageSize = 20

const STRATEGY_LABEL_MAP = {
  hash: 'tool.dedupStrategyHash',
  filename: 'tool.dedupStrategyFileName',
  metadata: 'tool.dedupStrategyMetadata',
  fingerprint: 'tool.dedupStrategyFingerprint',
}

// 按策略分组的 tabs
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

// 当前策略的分组（已按 groupIndex 排序，展开 filePaths/fileNames JSON）
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

// 当前页的分组
const dedupPageGroups = computed(() => {
  const start = (dedupCurrentPage.value - 1) * dedupPageSize
  return dedupCurrentStrategyGroups.value.slice(start, start + dedupPageSize)
})

const dedupTotalPages = computed(() =>
  Math.ceil(dedupCurrentStrategyGroups.value.length / dedupPageSize)
)

// 自动选择第一个策略 tab
watch(dedupStrategyTabs, (tabs) => {
  if (tabs.length > 0 && !tabs.find(t => t.key === dedupActiveStrategy.value)) {
    dedupActiveStrategy.value = tabs[0].key
  }
  dedupCurrentPage.value = 1
})

// 切换策略时重置页码
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

// ── 去重删除 ──
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

// 客户端预览：基于当前选中策略的分组和规则计算待删除文件
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

// 单文件删除：弹窗确认后执行
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

// 规则批量删除（预览通过弹窗内 alert 实时展示，无需额外函数）

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


// 从路径中提取所在目录名
function extractDirName(fullPath) {
  if (!fullPath) return ''
  const idx = Math.max(fullPath.lastIndexOf('/'), fullPath.lastIndexOf('\\'))
  if (idx < 0) return ''
  // 返回上一级目录名
  const parent = fullPath.slice(0, idx)
  const idx2 = Math.max(parent.lastIndexOf('/'), parent.lastIndexOf('\\'))
  return idx2 >= 0 ? parent.slice(idx2 + 1) : parent
}

// ── 编辑文件 ──
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

// ═══════════════════════════════════════════════════════════════
// 初始加载
// ═══════════════════════════════════════════════════════════════

async function load() {
  try {
    const data = await fetchPipeline(route.params.id)
    restEvent.value = { ...restEvent.value, ...data }
    successCount.value = data.successFiles ?? 0
    failedCount.value = data.failedFiles ?? 0
    // dedup 模板：加载去重分组
    if (data.templateName === 'dedup') {
      await loadDedupGroups()
    } else if (data.state === 'COMPLETED' || data.state === 'FAILED' || data.state === 'CANCELLED') {
      await loadDbPage(1)
      dbReady.value = true
    }
  } catch (e) {
    console.error('[PipelineDetail] 加载详情失败:', e)
  } finally {
    loading.value = false
  }
}

// WS progressEvent 合并更新（保留 REST 独有的字段如 templateName、inputPaths）
watch(progressEvent, (evt) => {
  if (evt) restEvent.value = { ...restEvent.value, ...evt }
})

// pipeline ID 变化时重载
watch(() => route.params.id, (newId) => {
  if (newId) {
    loading.value = true
    restEvent.value = null
    wsAllItems.value = []
    dbItems.value = []
    dbReady.value = false
    currentPage.value = 1
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

/* ── 顶部导航 ── */
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

/* ── 概览卡片 ── */
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

/* 统计 */
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

/* ── 操作按钮 ── */
.action-bar {
  margin-bottom: 16px;
}

/* ── READY 排队等待提示 ── */
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

/* ── 结果卡片（合并列表）── */
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

/* ── 去重结果 ── */
/* 策略 Tab 选中态颜色 */
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

/* ── 去重删除面板 ── */
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

/* ── 错误卡片 ── */
.error-card { margin-bottom: 16px; }
.error-module {
  font-family: var(--font-family-mono);
  color: var(--color-destructive);
}

.not-found { margin-top: 60px; }
</style>
