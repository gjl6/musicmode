<template>
  <ToolPanelLayout
    :selected-files="selectedFiles"
    :selected-folders="selectedFolders"
    :target-path="targetPath"
  >
    <div class="et-main">
      <!-- 1. 顶部操作栏 -->
      <div class="et-topbar">
        <span class="et-title">{{ t('enrichTool.title') }}</span>
        <n-tag type="warning" size="small" :bordered="false">
          {{ t('enrichTool.batchEnrich') }}
        </n-tag>
      </div>

      <!-- 2. 标签源区 -->
      <div class="et-section">
        <div class="et-section-header">
          <span class="et-section-title">{{ t('enrichTool.tagSource') }}</span>
        </div>
        <n-select
          v-model:value="provider"
          :options="providerOptions"
          multiple
          :placeholder="t('enrichTool.tagSource')"
          style="max-width: 400px"
        />
      </div>

      <!-- 3. 匹配模式区 -->
      <div class="et-section">
        <div class="et-section-header">
          <span class="et-section-title">{{ t('enrichTool.matchMode') }}</span>
        </div>
        <div class="et-mode-list">
          <div
            v-for="m in matchModes"
            :key="m.value"
            class="et-mode-item"
            :class="{ 'et-mode-active': matchMode === m.value }"
            @click="matchMode = m.value"
          >
            <div class="et-mode-label">{{ t(m.labelKey) }}</div>
            <div class="et-mode-desc">{{ t(m.descKey) }}</div>
          </div>
        </div>
      </div>

      <!-- 4. 合并策略区 -->
      <div class="et-section">
        <div class="et-section-header">
          <span class="et-section-title">{{ t('enrichTool.mergeScope') }}</span>
        </div>
        <div class="et-radio-row">
          <n-button
            :type="mergeScope === 'FILL_ONLY' ? 'primary' : 'default'"
            size="small"
            @click="mergeScope = 'FILL_ONLY'"
          >
            {{ t('enrichTool.mergeScopeFillOnly') }}
          </n-button>
          <n-button
            :type="mergeScope === 'REPLACE_ALL' ? 'primary' : 'default'"
            size="small"
            @click="mergeScope = 'REPLACE_ALL'"
          >
            {{ t('enrichTool.mergeScopeReplaceAll') }}
          </n-button>
        </div>
      </div>

      <!-- 5. 目标字段区 -->
      <div class="et-section">
        <div class="et-section-header">
          <span class="et-section-title">{{ t('enrichTool.targetFields') }}</span>
          <n-tag type="warning" size="small" :bordered="false">
            {{ t('enrichTool.fieldsCount', { n: selectedFields.length }) }}
          </n-tag>
        </div>
        <div class="et-fields-row">
          <n-tag
            v-for="f in selectedFields"
            :key="f"
            closable
            @close="removeField(f)"
          >
            {{ fieldLabel(f) }}
          </n-tag>
          <n-select
            v-if="availableFields.length > 0"
            v-model:value="selectValue"
            :placeholder="t('enrichTool.addField')"
            :options="availableFields"
            size="small"
            style="width: 140px"
            :consistent-menu-width="false"
            @update:value="addField"
          />
          <n-text v-else depth="3" class="et-all-selected">{{ t('enrichTool.allFieldsSelected') }}</n-text>
        </div>
      </div>

      <!-- 6. 处理结果 -->
      <div class="et-section">
        <n-button
          type="primary"
          block
          :loading="processing"
          :disabled="!hasTargets && !props.targetPath"
          @click="handleSubmit"
        >
          {{ processing ? t('tool.processing') : t('tool.startProcessing') }}
        </n-button>

        <n-alert v-if="error" type="error" class="et-result-alert">{{ error }}</n-alert>
      </div>
    </div>
  </ToolPanelLayout>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { NTag, NButton, NAlert, NSelect, NText, useMessage } from 'naive-ui'
import { runTool } from '@/api/editor/tools.js'
import ToolPanelLayout from '@/components/editor/tool/ToolPanelLayout.vue'
import { listProviders } from '@/api/editor/enrich.js'

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
const matchMode = ref('LOOSE')
const matchModes = [
  { value: 'LOOSE', labelKey: 'enrichTool.matchModeLoose', descKey: 'enrichTool.matchModeLooseDesc' },
  { value: 'STANDARD', labelKey: 'enrichTool.matchModeStandard', descKey: 'enrichTool.matchModeStandardDesc' },
  { value: 'STRICT', labelKey: 'enrichTool.matchModeStrict', descKey: 'enrichTool.matchModeStrictDesc' },
]

const mergeScope = ref('REPLACE_ALL')
const provider = ref([])

// ── Provider 列表 ──

const providerOptions = ref([])

async function loadProviderOptions() {
  try {
    const data = await listProviders()
    providerOptions.value = (Array.isArray(data) ? data : []).map(p => ({
      label: p.label || p.name,
      value: p.name,
    }))
  } catch {
    providerOptions.value = []
  }
}

onMounted(() => { loadProviderOptions() })

// ── 字段选择 ──

const ALL_FIELDS = [
  { value: 'song.title', labelKey: 'songTitle' },
  { value: 'song.duration', labelKey: 'songDuration' },
  { value: 'song.year', labelKey: 'songYear' },
  { value: 'song.language', labelKey: 'songLanguage' },
  { value: 'song.coverPath', labelKey: 'songCover' },
  { value: 'song.trackNumber', labelKey: 'songTrackNumber' },
  { value: 'song.discNumber', labelKey: 'songDiscNumber' },
  { value: 'song.composer', labelKey: 'songComposer' },
  { value: 'song.lyricist', labelKey: 'songLyricist' },
  { value: 'album.albumName', labelKey: 'albumName' },
  { value: 'album.language', labelKey: 'albumLanguage' },
  { value: 'album.company', labelKey: 'albumCompany' },
  { value: 'album.introduction', labelKey: 'albumIntroduction' },
  { value: 'album.albumYear', labelKey: 'albumYear' },
  { value: 'album.albumCover', labelKey: 'albumCover' },
  { value: 'style.styleName', labelKey: 'styleName' },
  { value: 'lyric.content', labelKey: 'lyricContent' },
]

const selectedFields = ref(ALL_FIELDS.map(f => f.value))
const selectValue = ref(null)

const availableFields = computed(() =>
  ALL_FIELDS
    .filter(f => !selectedFields.value.includes(f.value))
    .map(f => ({ label: t('enrichTool.fields.' + f.labelKey), value: f.value })),
)

function fieldLabel(field) {
  const entry = ALL_FIELDS.find(f => f.value === field)
  return entry ? t('enrichTool.fields.' + entry.labelKey) : field
}

function removeField(field) {
  selectedFields.value = selectedFields.value.filter(f => f !== field)
}

function addField(field) {
  if (field && !selectedFields.value.includes(field)) {
    selectedFields.value = [...selectedFields.value, field]
  }
  selectValue.value = null
}

const hasTargets = computed(
  () => props.selectedFiles.length > 0 || props.selectedFolders.length > 0,
)

// ── 提交 ──

async function handleSubmit() {
  processing.value = true
  error.value = null
  try {
    const allTargets = [...props.selectedFiles, ...props.selectedFolders]
    const options = {
      path: props.targetPath,
      files: allTargets,
      'enrich': {
        provider: provider.value.join(','),
        matchMode: matchMode.value,
        mergeScope: mergeScope.value,
        fields: selectedFields.value,
      },
    }
    const res = await runTool('qqEnrich', options)
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
.enrich-tool-panel {
  display: flex;
  gap: 16px;
  overflow: hidden;
}

/* 左侧：文件/目录列表 */
.et-files-col {
  width: 175px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--ct-border);
  padding-right: 12px;
  max-height: 500px;
}
.et-files-header {
  font-size: 11px;
  font-weight: 600;
  color: var(--ct-text);
  margin-bottom: 8px;
  flex-shrink: 0;
}
.et-file-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.et-file-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-size: 11px;
  padding: 1px 0;
}
.et-file-idx {
  color: var(--ct-text-3);
  min-width: 18px;
  text-align: right;
  font-size: 10px;
  flex-shrink: 0;
}
.et-file-name {
  font-family: 'SF Mono', monospace;
  color: var(--ct-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 右侧主内容 */
.et-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow-y: auto;
}

/* 1. 顶部操作栏 */
.et-topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--ct-border);
}
.et-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ct-text);
}

/* section 通用 */
.et-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.et-section-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.et-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ct-text);
}

/* radio 按钮行 */
.et-radio-row {
  display: flex;
  gap: 8px;
}

/* 匹配模式卡片 */
.et-mode-list {
  display: flex;
  gap: 8px;
}
.et-mode-item {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid var(--ct-border);
  border-radius: 6px;
  cursor: pointer;
  transition: border-color .2s, background .2s;
}
.et-mode-item:hover {
  border-color: var(--n-color-primary);
}
.et-mode-active {
  border-color: var(--n-color-primary);
  background: rgba(var(--n-color-primary-rgb, 24 160 88), 0.06);
}
.et-mode-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--ct-text);
}
.et-mode-desc {
  font-size: 11px;
  color: var(--ct-text-3);
  margin-top: 2px;
}

/* 字段选择 */
.et-fields-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.et-all-selected {
  font-size: 12px;
}

/* 结果 */
.et-result-alert {
  margin-top: 4px;
}
</style>
