<template>
  <n-modal
    :show="visible"
    preset="card"
    :title="toolLabel"
    style="max-width: 560px"
    :mask-closable="!processing"
    @update:show="$emit('update:visible', $event)"
  >
    <!-- 工具描述 -->
    <n-alert type="info" :bordered="false" class="tool-desc">
      {{ toolDesc }}
    </n-alert>

    <!-- 未实现工具 -->
    <n-result
      v-if="!isImplemented"
      status="info"
      :title="$t('tool.comingSoon')"
      :description="$t('tool.comingSoonDesc')"
    />

    <!-- 已实现工具 -->
    <template v-if="isImplemented">
      <n-form-item :label="$t('tool.targetPath')" class="path-field">
        <n-input
          v-model:value="path"
          :disabled="processing"
          placeholder="music/some-album"
        />
      </n-form-item>
      <n-alert v-if="selectedCount > 0" type="info" :bordered="false" class="selected-hint">
        {{ $t('tool.selectedHint', { n: selectedCount }) }}
        <div class="selected-files">
          <span v-for="f in selectedFilePaths.slice(0, 3)" :key="f" class="selected-file-tag">{{ f }}</span>
          <span v-if="selectedFilePaths.length > 3">{{ $t('tool.andMoreFiles', { n: selectedFilePaths.length - 3 }) }}</span>
        </div>
      </n-alert>

      <n-collapse v-if="!result && !error">
        <n-collapse-item :title="$t('tool.advancedOptions')" name="options">
          <n-text depth="3">{{ $t('tool.noOptions') }}</n-text>
        </n-collapse-item>
      </n-collapse>

      <!-- 结果展示 -->
      <n-alert v-if="result" :type="result.success ? 'success' : 'error'" class="result-alert">
        <template #header>
          <span v-if="result.success">{{ $t('tool.success') }}</span>
          <span v-else>{{ $t('tool.failure') }}</span>
        </template>
        <p>{{ $t('tool.duration', { ms: result.durationMs ?? 0 }) }}</p>

        <p v-if="toolKey === 'importCollection' && result.data">
          {{ $t('tool.importResult', { n: result.data.imported ?? 0 }) }}
        </p>
        <p v-if="isWriteTool && result.data">
          {{ $t('tool.writeResult', { ok: result.data.successCount ?? 0, fail: result.data.failCount ?? 0 }) }}
        </p>
      </n-alert>

      <!-- 错误展示 -->
      <n-alert v-if="error" type="error" class="result-alert">
        {{ error }}
      </n-alert>
    </template>

    <template #footer>
      <n-space justify="end">
        <n-button @click="visible = false" :disabled="processing">{{ $t('common.cancel') }}</n-button>
        <n-button
          v-if="isImplemented && !result"
          type="primary"
          :loading="processing"
          :disabled="!path"
          @click="handleSubmit"
        >
          {{ processing ? $t('tool.processing') : $t('tool.startProcessing') }}
        </n-button>
        <n-button v-if="result" type="primary" @click="visible = false">
          {{ $t('common.close') }}
        </n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { runTool, isToolImplemented } from '@/api/editor/tools.js'
import { useFileStore } from '@/store/editor/file.js'

const SPLIT_DEFAULTS = [
  { name: '序号 + 标题', pattern: '^(\\d+)\\.\\s*(.+)$', groups: { 1: 'tracknumber', 2: 'title' }, enabled: true },
  { name: '序号 标题', pattern: '^(\\d+)\\s+(.+)$', groups: { 1: 'tracknumber', 2: 'title' }, enabled: true },
  { name: '标题 - 艺术家 - 专辑', pattern: '^(.+?)\\s*-\\s*(.+?)\\s*-\\s*(.+?)$', groups: { 1: 'title', 2: 'artist', 3: 'album' }, enabled: true },
  { name: '标题 - 艺术家', pattern: '^(.+?)\\s*-\\s*(.+?)$', groups: { 1: 'title', 2: 'artist' }, enabled: true },
  { name: '艺术家 - 标题', pattern: '^(.+?)\\s*-\\s*(.+?)$', groups: { 1: 'artist', 2: 'title' }, enabled: false },
  { name: '序号. 标题 - 艺术家', pattern: '^(\\d+)\\.\\s*(.+?)\\s*-\\s*(.+?)$', groups: { 1: 'tracknumber', 2: 'title', 3: 'artist' }, enabled: false },
]
const REPLACE_DEFAULTS = [
  { name: '去除{}包裹数字', type: 'regex', find: '\\{\\d+\\}', replace: '', enabled: true },
]

const props = defineProps({
  toolKey: { type: String, required: true },
  toolLabel: { type: String, required: true },
  toolDesc: { type: String, default: '' },
  visible: { type: Boolean, default: false },
})

const emit = defineEmits(['update:visible', 'done'])

const { t } = useI18n()
const fileStore = useFileStore()

const processing = ref(false)
const result = ref(null)
const error = ref(null)
const path = ref('')

const isImplemented = computed(() => isToolImplemented(props.toolKey))
const isWriteTool = computed(() =>
  ['encodingRepair', 'langConvert', 'batchWrite'].includes(props.toolKey)
)
const selectedCount = computed(() => fileStore.selectedIds?.size ?? 0)
const selectedFilePaths = computed(() => fileStore.selectedFiles.map(f => f.path))

// 弹窗打开时自动填充当前路径
watch(() => props.visible, (v) => {
  if (v) {
    path.value = fileStore.currentPath || ''
    result.value = null
    error.value = null
  }
})

async function handleSubmit() {
  processing.value = true
  error.value = null
  result.value = null
  try {
    let options = {}
    if (props.toolKey === 'split') {
      try {
        const raw = localStorage.getItem('music-split-rules')
        const rules = raw ? JSON.parse(raw) : SPLIT_DEFAULTS
        if (rules && rules.length > 0) {
          options = { 'split-metadata': { rules } }
        }
      } catch (_) {
        options = { 'split-metadata': { rules: SPLIT_DEFAULTS } }
      }
    } else if (props.toolKey === 'replaceText') {
      try {
        const raw = localStorage.getItem('music-replace-rules')
        const rules = raw ? JSON.parse(raw) : REPLACE_DEFAULTS
        if (rules && rules.length > 0) {
          options = { 'replace-text': { rules } }
        }
      } catch (_) {
        options = { 'replace-text': { rules: REPLACE_DEFAULTS } }
      }
    }
    if (selectedFilePaths.value.length > 0) {
      options.files = selectedFilePaths.value
    }
    options.path = path.value
    const res = await runTool(props.toolKey, options)
    result.value = res
    if (res.success) {
      window.$message?.success(t('tool.success'))
      emit('done')
    } else {
      window.$message?.error(res.error || t('tool.failure'))
    }
  } catch (err) {
    const msg = err?.response?.data?.error || err.message || t('common.error')
    error.value = msg
    window.$message?.error(msg)
  } finally {
    processing.value = false
  }
}
</script>

<style scoped>
.tool-desc {
  margin-bottom: 16px;
}

.path-field {
  margin-top: 4px;
}

.selected-hint {
  display: block;
  margin-top: 4px;
}

.selected-files {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
  font-size: 11px;
  color: var(--ct-text-2);
}

.selected-file-tag {
  font-family: 'SF Mono', monospace;
  font-size: 10px;
  background: var(--ct-bg-secondary);
  padding: 1px 6px;
  border-radius: 3px;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-alert {
  margin-top: 16px;
}

.result-alert p {
  margin: 4px 0;
}

</style>
