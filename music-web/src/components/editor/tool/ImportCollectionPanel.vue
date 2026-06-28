<template>
  <ToolPanelLayout
    :selected-files="selectedFiles"
    :selected-folders="selectedFolders"
    :target-path="targetPath"
  >
    <div class="ic-main">
      <!-- 1. 顶部标题栏 -->
      <div class="ic-topbar">
        <span class="ic-title">{{ t('importCollection.title') }}</span>
        <n-tag type="info" size="small" :bordered="false">
          {{ t('importCollection.sourceFiles', { n: totalCount }) }}
        </n-tag>
      </div>

      <!-- 2. 功能说明 -->
      <div class="ic-section">
        <n-alert type="info" :bordered="false">
          <template #header>
            {{ t('importCollection.whatItDoes') }}
          </template>
          <p class="ic-desc">{{ t('importCollection.desc') }}</p>
        </n-alert>
      </div>

      <!-- 3. 流程说明 -->
      <div class="ic-section">
        <div class="ic-flow">
          <div class="ic-flow-step">
            <span class="ic-flow-icon">📂</span>
            <span class="ic-flow-label">扫描文件</span>
          </div>
          <span class="ic-flow-arrow">→</span>
          <div class="ic-flow-step">
            <span class="ic-flow-icon">🏷️</span>
            <span class="ic-flow-label">解析标签</span>
          </div>
          <span class="ic-flow-arrow">→</span>
          <div class="ic-flow-step">
            <span class="ic-flow-icon">💾</span>
            <span class="ic-flow-label">写入数据库</span>
          </div>
        </div>
      </div>

      <!-- 4. 无文件提示 -->
      <n-alert v-if="!hasTargets" type="warning" :bordered="false">
        {{ t('importCollection.noFilesHint') }}
      </n-alert>

      <!-- 5. 提交区 -->
      <div class="ic-section">
        <n-button
          type="primary"
          block
          :loading="processing"
          :disabled="!hasTargets"
          @click="handleSubmit"
        >
          {{ processing ? t('toolPanel.processing') : t('toolPanel.submit') }}
        </n-button>

        <n-alert v-if="error" type="error" class="ic-result">{{ error }}</n-alert>
      </div>
    </div>
  </ToolPanelLayout>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NTag, NButton, NAlert, useMessage } from 'naive-ui'
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

const hasTargets = computed(
  () => props.selectedFiles.length > 0 || props.selectedFolders.length > 0,
)

const totalCount = computed(
  () => props.selectedFiles.length + props.selectedFolders.length,
)

async function handleSubmit() {
  processing.value = true
  error.value = null
  try {
    const allTargets = [...props.selectedFiles, ...props.selectedFolders]
    const options = {
      path: props.targetPath,
      files: allTargets,
    }
    const res = await runTool('importCollection', options)
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
.ic-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow-y: auto;
}

.ic-topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--ct-border);
}

.ic-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ct-text);
}

.ic-desc {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: var(--ct-text-2);
  line-height: 1.6;
}

.ic-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 流程示意 */
.ic-flow {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 16px;
  border: 1px dashed var(--ct-border);
  border-radius: 8px;
  background: var(--ct-bg-secondary);
}

.ic-flow-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  min-width: 64px;
}

.ic-flow-icon {
  font-size: 24px;
}

.ic-flow-label {
  font-size: 12px;
  color: var(--ct-text-2);
  font-weight: 500;
}

.ic-flow-arrow {
  font-size: 18px;
  color: var(--ct-text-3);
}

.ic-result {
  margin-top: 4px;
}
</style>
