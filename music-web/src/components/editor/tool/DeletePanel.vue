<template>
  <ToolPanelLayout
    :selected-files="selectedFiles"
    :selected-folders="selectedFolders"
    :target-path="targetPath"
  >
    <div class="del-main">
      <!-- 1. 顶部标题栏 -->
      <div class="del-topbar">
        <span class="del-title">{{ t('deleteModule.title') }}</span>
        <n-tag type="error" size="small" :bordered="false">
          {{ t('deleteModule.warning') }}
        </n-tag>
      </div>

      <!-- 2. 模式选择 -->
      <div class="del-section">
        <div class="del-section-header">
          <span class="del-section-title">{{ t('deleteModule.mode') }}</span>
        </div>
        <n-select
          v-model:value="deleteMode"
          :options="modeOptions"
          :consistent-menu-width="false"
          style="max-width: 480px"
        />
        <n-text depth="3" class="del-mode-desc">
          {{ modeDescription }}
        </n-text>
      </div>

      <!-- 3. 警告区 -->
      <div class="del-section">
        <n-alert type="error" :bordered="false" class="del-warning-alert">
          <template #header>
            <strong>{{ warningTitle }}</strong>
          </template>
          <p>{{ warningContent }}</p>
        </n-alert>
      </div>

      <!-- 4. 模式 1&2: 目标路径显示 -->
      <div v-if="deleteMode !== 'selected'" class="del-section">
        <div class="del-section-header">
          <span class="del-section-title">{{ t('deleteModule.targetPath') }}</span>
        </div>
        <n-text code class="del-path">{{ props.targetPath || '/' }}</n-text>
      </div>

      <!-- 5. 模式 3: 选中项统计 -->
      <div v-if="deleteMode === 'selected'" class="del-section">
        <div class="del-section-header">
          <span class="del-section-title">{{ t('deleteModule.selectedCount', { n: totalSelected }) }}</span>
        </div>
      </div>

      <!-- 6. 操作按钮区 -->
      <div class="del-section">
        <n-popconfirm
          :positive-text="t('common.confirm')"
          :negative-text="t('common.cancel')"
          @positive-click="handleSubmit"
        >
          <template #trigger>
            <n-button
              type="error"
              block
              :loading="processing"
              :disabled="!canSubmit"
            >
              <template #icon>
                <TrashOutline />
              </template>
              {{ processing ? t('deleteModule.processing') : t('deleteModule.submit') }}
            </n-button>
          </template>
          {{ confirmText }}
        </n-popconfirm>
      </div>

      <!-- 7. 结果区 -->
      <n-alert v-if="error" type="error" class="del-result-alert">{{ error }}</n-alert>
    </div>
  </ToolPanelLayout>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { NTag, NSelect, NButton, NAlert, NText, NPopconfirm, useMessage } from 'naive-ui'
import { TrashOutline } from '@vicons/ionicons5'
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

const deleteMode = ref('empty-folders')
const processing = ref(false)
const error = ref(null)

const modeOptions = [
  {
    label: t('deleteModule.modeEmptyFolders'),
    value: 'empty-folders',
  },
  {
    label: t('deleteModule.modeNonMusicFolders'),
    value: 'non-music-folders',
  },
  {
    label: t('deleteModule.modeSelected'),
    value: 'selected',
  },
]

const modeDescription = computed(() => {
  switch (deleteMode.value) {
    case 'empty-folders': return t('deleteModule.modeEmptyFoldersDesc')
    case 'non-music-folders': return t('deleteModule.modeNonMusicFoldersDesc')
    case 'selected': return t('deleteModule.modeSelectedDesc')
    default: return ''
  }
})

const warningTitle = computed(() => {
  switch (deleteMode.value) {
    case 'empty-folders': return t('deleteModule.emptyFolderWarning')
    case 'non-music-folders': return t('deleteModule.nonMusicWarning')
    case 'selected': return t('deleteModule.warningSelected')
    default: return t('deleteModule.warning')
  }
})

const warningContent = computed(() => {
  if (deleteMode.value === 'selected') {
    return t('deleteModule.warningSelected')
  }
  return ''
})

const totalSelected = computed(() =>
  props.selectedFiles.length + props.selectedFolders.length,
)

const confirmText = computed(() => {
  if (deleteMode.value === 'selected') {
    return t('deleteModule.confirmSelected', { n: totalSelected.value })
  }
  if (deleteMode.value === 'empty-folders') {
    return t('deleteModule.confirmEmpty')
  }
  return t('deleteModule.confirmNonMusic')
})

const canSubmit = computed(() => {
  if (deleteMode.value === 'selected') {
    return totalSelected.value > 0
  }
  // 模式 1/2: 只需要有目标路径
  return !!props.targetPath
})

async function handleSubmit() {
  processing.value = true
  error.value = null
  try {
    const allTargets = [...props.selectedFiles, ...props.selectedFolders]
    const options = {
      mode: deleteMode.value,
      files: allTargets,
      path: props.targetPath,
    }
    const res = await runTool('deleteFiles', options)
    if (res?.pipelineId) {
      message?.success('已提交: ' + res.pipelineId)
      emit('done')
    } else {
      message?.error(res?.error || t('common.error'))
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
.del-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow-y: auto;
}

/* 1. 顶部标题栏 */
.del-topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--ct-border);
}
.del-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ct-text);
}

/* section 通用 */
.del-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.del-section-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.del-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ct-text);
}
.del-mode-desc {
  font-size: 12px;
}

/* 路径显示 */
.del-path {
  font-size: 13px;
  padding: 6px 12px;
  word-break: break-all;
}

/* 警告 */
.del-warning-alert {
  --n-padding: 12px 16px;
}
.del-warning-alert p {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--ct-text-2);
}

/* 结果 */
.del-result-alert {
  margin-top: 4px;
}
</style>
