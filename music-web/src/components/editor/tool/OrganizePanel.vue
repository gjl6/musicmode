<template>
  <ToolPanelLayout
    :selected-files="selectedFiles"
    :selected-folders="selectedFolders"
    :target-path="targetPath"
  >
    <div class="og-main">
      <!-- 1. 顶部标题 -->
      <div class="og-topbar">
        <span class="og-title">{{ t('organize.title') }}</span>
        <n-tag type="info" size="small" :bordered="false">
          {{ t('organize.sourceFiles', { n: totalCount }) }}
        </n-tag>
      </div>

      <!-- 2. 操作模式 -->
      <div class="og-section">
        <div class="og-section-header">
          <span class="og-section-title">{{ t('organize.mode') }}</span>
        </div>
        <n-radio-group :value="mode" @update:value="v => mode = v">
          <n-radio value="move">{{ t('organize.modeMove') }}</n-radio>
          <n-radio value="copy">{{ t('organize.modeCopy') }}</n-radio>
        </n-radio-group>
      </div>

      <!-- 3. 目标根目录 -->
      <div class="og-section">
        <div class="og-section-header">
          <span class="og-section-title">{{ t('organize.targetRoot') }}</span>
        </div>
        <n-input
          v-model:value="targetRoot"
          size="small"
          :placeholder="t('organize.targetRootPlaceholder')"
          clearable
        />
        <n-text depth="3" class="og-hint">{{ t('organize.targetRootHint') }}</n-text>
      </div>

      <!-- 4. 目录层级 -->
      <div class="og-section">
        <div class="og-section-header">
          <span class="og-section-title">{{ t('organize.hierarchy') }}</span>
          <n-tag type="info" size="small" :bordered="false">
            {{ levels.length }}
          </n-tag>
        </div>

        <div class="og-level-list">
          <div v-for="(level, i) in levels" :key="i" class="og-level-row">
            <span class="og-level-idx">{{ i + 1 }}</span>
            <n-select
              :value="level.field"
              :options="fieldOptions"
              size="small"
              :placeholder="t('organize.selectField')"
              style="flex: 1"
              @update:value="v => updateLevelField(i, v)"
            />
            <n-button
              size="tiny"
              text
              type="error"
              @click="removeLevel(i)"
            >×</n-button>
          </div>
        </div>

        <n-button
          dashed
          size="small"
          block
          @click="addLevel"
        >{{ t('organize.addLevel') }}</n-button>

        <!-- 路径预览 -->
        <div v-if="levels.length > 0" class="og-preview">
          <span class="og-preview-label">{{ t('organize.preview') }}:</span>
          <code class="og-preview-path">{{ previewPath }}</code>
        </div>
        <n-text v-else depth="3" class="og-hint">{{ t('organize.noLevels') }}</n-text>
      </div>

      <!-- 5. 提示信息 -->
      <n-alert type="info" class="og-info">
        {{ t('organize.conflictWarning') }}
      </n-alert>

      <!-- 6. 提交 -->
      <div class="og-section">
        <n-button
          type="primary"
          block
          :loading="processing"
          :disabled="levels.length === 0 || (!hasTargets && !targetPath)"
          @click="handleSubmit"
        >
          {{ processing ? t('toolPanel.processing') : t('toolPanel.submit') }}
        </n-button>

        <n-alert v-if="error" type="error" class="og-result">{{ error }}</n-alert>
      </div>
    </div>
  </ToolPanelLayout>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { NTag, NSelect, NButton, NAlert, NText, NInput, NRadioGroup, NRadio, useMessage } from 'naive-ui'
import { runTool } from '@/api/editor/tools.js'
import ToolPanelLayout from '@/components/editor/tool/ToolPanelLayout.vue'

const props = defineProps({
  mode: { type: String, default: 'tool' },
  targetPath: { type: String, default: '' },
  selectedFiles: { type: Array, default: () => [] },
  selectedFolders: { type: Array, default: () => [] },
})

const emit = defineEmits(['done'])

const { t } = useI18n()
const message = useMessage()

const processing = ref(false)
const error = ref(null)

// 操作模式
const mode = ref('move')

// 目标根目录
const targetRoot = ref('')

// 目录层级
const levels = ref([])

// 可用字段选项
const fieldOptions = [
  { label: () => t('organize.fields.artist'), value: 'artist' },
  { label: () => t('organize.fields.album'), value: 'album' },
  { label: () => t('organize.fields.title'), value: 'title' },
  { label: () => t('organize.fields.year'), value: 'year' },
  { label: () => t('organize.fields.format'), value: 'format' },
  { label: () => t('organize.fields.style'), value: 'style' },
  { label: () => t('organize.fields.language'), value: 'language' },
  { label: () => t('organize.fields.trackNumber'), value: 'trackNumber' },
  { label: () => t('organize.fields.discNumber'), value: 'discNumber' },
  { label: () => t('organize.fields.composer'), value: 'composer' },
  { label: () => t('organize.fields.lyricist'), value: 'lyricist' },
  { label: () => t('organize.fields.albumYear'), value: 'albumYear' },
  { label: () => t('organize.fields.company'), value: 'company' },
]

const hasTargets = computed(
  () => props.selectedFiles.length > 0 || props.selectedFolders.length > 0,
)

const totalCount = computed(
  () => props.selectedFiles.length + props.selectedFolders.length,
)

// 路径预览
const previewPath = computed(() => {
  // 目标根目录优先，否则用当前浏览目录
  const rootLabel = targetRoot.value
    || (props.targetPath && props.targetPath !== '/' ? props.targetPath : '')
    || t('organize.sourceDir')
  const segments = levels.value.map(l => {
    const opt = fieldOptions.find(o => o.value === l.field)
    return opt?.label?.() || l.field
  })
  return rootLabel + '/' + segments.join('/') + '/' + t('organize.filename')
})

function addLevel() {
  levels.value = [...levels.value, { field: 'artist' }]
}

function removeLevel(index) {
  levels.value = levels.value.filter((_, i) => i !== index)
}

function updateLevelField(index, newField) {
  const updated = [...levels.value]
  updated[index] = { field: newField }
  levels.value = updated
}

// ── localStorage 持久化 ──

const STORAGE_KEY_LEVELS = 'music-organize-levels'
const STORAGE_KEY_MODE = 'music-organize-mode'
const STORAGE_KEY_ROOT = 'music-organize-targetRoot'

function loadFromStorage() {
  try {
    const savedLevels = localStorage.getItem(STORAGE_KEY_LEVELS)
    if (savedLevels) {
      const arr = JSON.parse(savedLevels)
      if (Array.isArray(arr) && arr.length > 0) levels.value = arr
    }
    const savedMode = localStorage.getItem(STORAGE_KEY_MODE)
    if (savedMode === 'move' || savedMode === 'copy') mode.value = savedMode
    const savedRoot = localStorage.getItem(STORAGE_KEY_ROOT)
    if (savedRoot) targetRoot.value = savedRoot
  } catch (_) { /* ignore */ }
}

loadFromStorage()

watch(levels, v => localStorage.setItem(STORAGE_KEY_LEVELS, JSON.stringify(v)), { deep: true })
watch(mode, v => localStorage.setItem(STORAGE_KEY_MODE, v))
watch(targetRoot, v => localStorage.setItem(STORAGE_KEY_ROOT, v))

// ── 提交 ──

async function handleSubmit() {
  processing.value = true
  error.value = null
  try {
    const allTargets = [...props.selectedFiles, ...props.selectedFolders]
    const options = {
      path: props.targetPath,
      files: allTargets,
      organize: {
        mode: mode.value,
        targetRoot: targetRoot.value || null,
        levels: levels.value,
        browsePath: props.targetPath || null,
      },
    }
    const res = await runTool('organize', options)
    if (res?.pipelineId) {
      message?.success('已提交: ' + res.pipelineId)
      emit('done')
    } else {
      message?.error(res?.error || t('tool.failure'))
    }
  } catch (err) {
    const msg = err?.response?.data?.error || err.message || t('common.error')
    error.value = msg
    message?.error(msg)
  } finally {
    processing.value = false
  }
}
</script>

<style scoped>
.og-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow-y: auto;
}

.og-topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--ct-border);
}

.og-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ct-text);
}

/* section 通用 */
.og-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.og-section-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.og-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ct-text);
}

.og-hint {
  font-size: 12px;
}

/* 层级列表 */
.og-level-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.og-level-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.og-level-idx {
  font-size: 12px;
  color: var(--ct-text-3);
  min-width: 16px;
  text-align: right;
}

/* 路径预览 */
.og-preview {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--ct-border);
  border-radius: 6px;
  background: var(--ct-bg-secondary);
}

.og-preview-label {
  font-size: 12px;
  color: var(--ct-text-3);
  flex-shrink: 0;
}

.og-preview-path {
  font-family: 'SF Mono', monospace;
  font-size: 12px;
  color: var(--n-color-primary);
  word-break: break-all;
}

.og-info {
  font-size: 12px;
}

.og-result {
  margin-top: 4px;
}
</style>
