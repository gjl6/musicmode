<template>
  <div class="split-panel" :class="{ 'tool-mode': mode === 'tool' }">
    <!-- 工具模式：文件列表栏 -->
    <div v-if="mode === 'tool'" class="sp-files-col">
      <div class="sp-files-header">
        <template v-if="selectedFiles.length || selectedFolders.length">
          处理 {{ selectedFiles.length + selectedFolders.length }} 项
        </template>
        <template v-else>目录：{{ targetPath || '—' }}</template>
      </div>
      <div v-if="selectedFiles.length || selectedFolders.length" class="sp-file-list">
        <div v-for="(f, i) in selectedFolders" :key="'d' + f" class="sp-file-row sp-folder-row">
          <span class="sp-file-idx">📁</span>
          <span class="sp-filename">{{ f }}</span>
        </div>
        <div v-for="(f, i) in selectedFiles" :key="f" class="sp-file-row">
          <span class="sp-file-idx">{{ selectedFolders.length + i + 1 }}.</span>
          <span class="sp-filename">{{ f.split('/').pop() }}</span>
        </div>
      </div>
    </div>

    <!-- 左侧：规则配置 -->
    <div class="sp-left">
      <div class="sp-section-header">
        <span class="sp-section-title">{{ t('split.rules') }}</span>
        <div class="sp-section-actions">
          <n-button size="tiny" text @click="split.resetToDefault()">{{ t('split.resetDefault') }}</n-button>
        </div>
      </div>

      <div class="sp-rule-list">
        <div
          v-for="(rule, i) in split.rules.value"
          :key="rule.id"
          class="sp-rule-item"
          :class="{ active: i === split.activeRuleIndex.value }"
          @click="split.activeRuleIndex.value = i"
        >
          <n-input
            v-model:value="rule.name"
            size="tiny"
            placeholder="规则名称"
            class="sp-rule-name"
            @click.stop
          />
          <n-switch
            :value="rule.enabled"
            size="small"
            @click.stop
            @update:value="split.toggleRule(i)"
          />
          <n-button
            size="tiny"
            text
            type="error"
            @click.stop="split.removeRule(i)"
            :disabled="split.rules.value.length <= 1"
          >×</n-button>
        </div>
        <n-button
          dashed
          size="small"
          class="sp-add-rule-btn"
          @click="split.addRule()"
        >{{ t('split.addRule') }}</n-button>
      </div>
    </div>

    <!-- 右侧：预览 -->
    <div class="sp-right">
      <!-- 当前规则编辑 -->
      <div v-if="activeRule" class="sp-rule-editor">
        <div class="sp-field">
          <label>{{ t('split.template') }}</label>
          <n-input
            :value="activeRule.template || ''"
            size="small"
            :placeholder="tmplPlaceholder"
            @update:value="v => split.setTemplate(split.activeRuleIndex.value, v)"
          />
          <span class="sp-field-hint">{{ tmplHint }}</span>
        </div>
        <n-collapse class="sp-advanced">
          <n-collapse-item title="高级" name="advanced">
            <div class="sp-field">
              <label>{{ t('split.regexPattern') }}</label>
              <n-input
                v-model:value="activeRule.pattern"
                size="small"
                placeholder="自动生成"
              />
            </div>
            <div class="sp-field">
              <label>{{ t('split.groupMapping') }}</label>
              <div class="sp-group-mapping">
                <div
                  v-for="entry in groupEntries"
                  :key="entry.key"
                  class="sp-group-row"
                >
                  <span class="sp-group-idx">${{ entry.key }}</span>
                  <n-select
                    :value="entry.value"
                    size="tiny"
                    :options="groupFieldOptions"
                    style="width:110px"
                    @update:value="v => updateGroup(entry.key, v)"
                  />
                </div>
              </div>
            </div>
          </n-collapse-item>
        </n-collapse>
        <div class="sp-field">
          <label>{{ t('split.notes') }}</label>
          <n-input
            v-model:value="activeRule.notes"
            size="small"
            placeholder="备注..."
          />
        </div>
      </div>

      <!-- 填充模式 -->
      <div class="sp-fill-mode">
        <span class="sp-fill-mode-label">{{ t('split.fillMode') }}</span>
        <n-radio-group :value="split.fillMode.value" size="small" @update:value="v => split.fillMode.value = v">
          <n-radio value="gapFill">{{ t('split.fillModeGapFill') }}</n-radio>
          <n-radio value="overwrite">{{ t('split.fillModeOverwrite') }}</n-radio>
        </n-radio-group>
      </div>

      <!-- 预览测试 -->
      <div class="sp-preview-section">
        <div class="sp-section-header">
          <span class="sp-section-title">{{ t('split.preview') }}</span>
          <n-button v-if="mode === 'edit'" size="tiny" text @click="split.useCurrentFileName()">用当前文件名</n-button>
        </div>
        <n-input
          v-model:value="split.sampleText.value"
          size="small"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 2 }"
          :placeholder="t('split.sampleInput')"
          @keyup.enter="split.executePreview()"
        />
        <n-button
          size="small"
          type="primary"
          class="sp-preview-btn"
          @click="split.executePreview()"
        >{{ t('split.executePreview') }}</n-button>

        <!-- 结果 -->
        <div v-if="split.previewError.value" class="sp-error">{{ split.previewError.value }}</div>
        <div v-else-if="split.previewResult.value" class="sp-result">
          <div v-if="!split.previewResult.value.matched" class="sp-no-match">{{ t('split.noMatch') }}</div>
          <table v-else class="sp-result-table">
            <thead>
              <tr><th>{{ t('split.targetField') }}</th><th>值</th></tr>
            </thead>
            <tbody>
              <tr v-for="(val, key) in split.previewResult.value.fields" :key="key">
                <td>{{ key }}</td>
                <td class="sp-result-val">{{ val }}</td>
              </tr>
            </tbody>
          </table>
          <n-button
            v-if="split.previewResult.value.matched && mode === 'edit'"
            size="small"
            type="primary"
            class="sp-apply-btn"
            @click="split.applyFields()"
          >{{ t('split.apply') }}</n-button>
        </div>

        <!-- 工具模式：提交按钮 + 结果 -->
        <template v-if="mode === 'tool'">
          <n-button
            size="small"
            type="primary"
            :loading="processing"
            :disabled="!targetPath && selectedFiles.length === 0 && selectedFolders.length === 0"
            @click="handleSubmit"
          >{{ processing ? t('tool.processing') : t('tool.startProcessing') }}</n-button>

          <n-alert v-if="error" type="error" class="sp-result-alert">{{ error }}</n-alert>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NSwitch, NInput, NButton, NSelect, NCollapse, NCollapseItem, NAlert, NRadioGroup, NRadio, useMessage } from 'naive-ui'
import { useSplit } from '@/composables/editor/useSplit.js'
import { runTool } from '@/api/editor/tools.js'

const props = defineProps({
  mode: { type: String, default: 'edit' },
  targetPath: { type: String, default: '' },
  selectedFiles: { type: Array, default: () => [] },
  selectedFolders: { type: Array, default: () => [] },
})

const emit = defineEmits(['done'])

const { t, locale } = useI18n()
const message = useMessage()
const split = useSplit()

// ── 工具模式：提交后端 ──

const processing = ref(false)
const error = ref(null)

async function handleSubmit() {
  processing.value = true
  error.value = null
  try {
    const enabledRules = split.rules.value.filter(r => r.enabled)
    const allTargets = [...props.selectedFiles, ...props.selectedFolders]
    const options = {
      'split-metadata': { rules: enabledRules, fillMode: split.fillMode.value },
      path: props.targetPath,
      files: allTargets,
    }
    const res = await runTool('split', options)
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

const tmplPlaceholder = computed(() =>
  locale.value.startsWith('zh')
    ? '{序号}. {标题} - {艺术家}'
    : '{track}. {title} - {artist}',
)

const tmplHint = computed(() =>
  locale.value.startsWith('zh')
    ? '用 {字段名} 标记要提取的部分，如: {标题} - {艺术家} - {专辑}'
    : 'Use {field} to mark parts, e.g. {title} - {artist} - {album}',
)

const activeRule = computed(() => split.rules.value[split.activeRuleIndex.value])

const groupFieldOptions = [
  { label: '—', value: '' },
  { label: 'title', value: 'title' },
  { label: 'artist', value: 'artist' },
  { label: 'album', value: 'album' },
  { label: 'tracknumber', value: 'tracknumber' },
  { label: 'discnumber', value: 'discnumber' },
  { label: 'year', value: 'year' },
  { label: 'genre', value: 'genre' },
  { label: 'language', value: 'language' },
  { label: 'composer', value: 'composer' },
  { label: 'lyricist', value: 'lyricist' },
  { label: 'country', value: 'country' },
  { label: 'albumyear', value: 'albumyear' },
]

const groupEntries = computed(() => {
  if (!activeRule.value?.groups) return []
  const g = activeRule.value.groups
  const maxIdx = Math.max(0, ...Object.keys(g).map(Number))
  const entries = []
  for (let i = 1; i <= Math.max(maxIdx, 3); i++) {
    entries.push({ key: i, value: g[i] || '' })
  }
  return entries
})

function updateGroup(idx, value) {
  if (!activeRule.value) return
  const newGroups = { ...activeRule.value.groups }
  if (value) {
    newGroups[idx] = value
  } else {
    delete newGroups[idx]
  }
  activeRule.value.groups = newGroups
}
</script>

<style scoped>
.split-panel {
  width: 600px;
  flex-shrink: 0;
  height: 100%;
  display: flex;
  gap: 16px;
  overflow: hidden;
}

.split-panel.tool-mode {
  width: 100%;
  gap: 12px;
}

/* tool mode: 文件列表栏 */
.sp-files-col {
  width: 175px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--ct-border);
  padding-right: 12px;
  max-height: 500px;
}

.sp-files-header {
  font-size: 11px;
  font-weight: 600;
  color: var(--ct-text);
  margin-bottom: 8px;
  flex-shrink: 0;
}

.sp-file-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.sp-file-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-size: 11px;
  padding: 1px 0;
}

.sp-file-idx {
  color: var(--ct-text-3);
  min-width: 18px;
  text-align: right;
  font-size: 10px;
  flex-shrink: 0;
}

.sp-filename {
  font-family: 'SF Mono', monospace;
  color: var(--ct-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sp-left {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--ct-border);
  padding-right: 12px;
}

.sp-right {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* section header */
.sp-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.sp-section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--ct-text);
}

.sp-section-actions {
  display: flex;
  gap: 4px;
}

/* rule list */
.sp-rule-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sp-rule-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px;
  border-radius: 4px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s;
}

.sp-rule-item:hover {
  background: var(--ct-bg-secondary);
}

.sp-rule-item.active {
  border-color: var(--n-color-primary);
  background: rgba(var(--ct-accent-rgb, 124, 58, 237) / 0.08);
}

.sp-rule-name {
  flex: 1;
  min-width: 0;
}

/* rule editor */
.sp-rule-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sp-field {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.sp-field label {
  font-size: 11px;
  color: var(--ct-text-2);
}

.sp-group-mapping {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sp-group-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sp-group-idx {
  font-size: 11px;
  font-family: monospace;
  color: var(--n-color-primary);
  width: 18px;
  text-align: right;
}

/* preview */
.sp-preview-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--ct-border);
}

.sp-preview-btn {
  align-self: flex-start;
}

.sp-error {
  font-size: 11px;
  color: var(--n-color-error);
  padding: 4px 0;
}

.sp-no-match {
  font-size: 11px;
  color: var(--ct-text-2);
}

.sp-result {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sp-result-table {
  border-collapse: collapse;
  font-size: 11px;
}

.sp-result-table th,
.sp-result-table td {
  padding: 4px 8px;
  text-align: left;
  border-bottom: 1px solid var(--ct-border);
}

.sp-result-table th {
  color: var(--ct-text-2);
  font-weight: 500;
  font-size: 10px;
}

.sp-result-val {
  font-family: 'SF Mono', monospace;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sp-apply-btn {
  align-self: flex-start;
}

.sp-add-rule-btn {
  margin-top: 6px;
}

.sp-field-hint {
  font-size: 10px;
  color: var(--ct-text-3);
  line-height: 1.4;
}

.sp-advanced {
  margin: 4px 0;
}

.sp-fill-mode {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-top: 1px solid var(--ct-border);
}

.sp-fill-mode-label {
  font-size: 11px;
  color: var(--ct-text-2);
  white-space: nowrap;
}

/* tool mode */
.sp-result-alert {
  margin-top: 8px;
}

.sp-result-alert p {
  margin: 4px 0;
}
</style>
