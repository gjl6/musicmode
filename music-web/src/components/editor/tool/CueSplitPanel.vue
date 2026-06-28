<template>
  <ToolPanelLayout
    :selected-files="selectedFiles"
    :selected-folders="selectedFolders"
    :target-path="targetPath"
  >
    <div class="cs-main">
      <!-- 标题 -->
      <div class="cs-topbar">
        <span class="cs-title">{{ t('cueSplit.title') }}</span>
        <n-tag type="warning" size="small" :bordered="false">
          {{ t('cueSplit.sourceFiles', { n: totalCount }) }}
        </n-tag>
      </div>

      <!-- 功能说明 -->
      <n-alert type="info" :bordered="false" class="cs-desc">
        {{ t('cueSplit.desc') }}
      </n-alert>

      <!-- 输出格式 -->
      <div class="cs-section">
        <div class="cs-section-header">
          <span class="cs-section-title">{{ t('cueSplit.outputFormat') }}</span>
        </div>
        <n-radio-group v-model:value="outputFormat" name="outputFormat">
          <n-space>
            <n-radio value="keep">
              <span class="cs-fmt-label">{{ t('cueSplit.keepOriginal') }}</span>
            </n-radio>
            <n-radio value="flac">
              <span class="cs-fmt-label">FLAC</span>
              <span class="cs-fmt-desc">— {{ t('cueSplit.flacDesc') }}</span>
            </n-radio>
            <n-radio value="mp3">
              <span class="cs-fmt-label">MP3</span>
              <span class="cs-fmt-desc">— {{ t('cueSplit.mp3Desc') }}</span>
            </n-radio>
          </n-space>
        </n-radio-group>
      </div>

      <!-- 选项 -->
      <div class="cs-section">
        <n-checkbox v-model:checked="writeTags">
          {{ t('cueSplit.writeTags') }}
        </n-checkbox>
      </div>
      <div class="cs-section">
        <n-checkbox v-model:checked="deleteSource">
          {{ t('cueSplit.deleteSource') }}
        </n-checkbox>
        <n-text v-if="deleteSource" depth="3" class="cs-warning">
          ⚠ {{ t('cueSplit.deleteSourceWarning') }}
        </n-text>
      </div>

      <!-- 提交 -->
      <div class="cs-action">
        <n-button
          type="primary"
          :loading="processing"
          :disabled="totalCount === 0"
          @click="handleSubmit"
        >
          {{ processing ? t('tool.processing') : t('tool.startProcessing') }}
        </n-button>
      </div>

      <n-alert v-if="error" type="error" class="cs-result">
        {{ error }}
      </n-alert>
    </div>
  </ToolPanelLayout>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NTag, NAlert, NRadioGroup, NRadio, NSpace, NCheckbox, NButton, NText, useMessage } from 'naive-ui'
import ToolPanelLayout from '@/components/editor/tool/ToolPanelLayout.vue'
import { runTool } from '@/api/editor/tools.js'

const props = defineProps({
  targetPath: { type: String, default: '' },
  selectedFiles: { type: Array, default: () => [] },
  selectedFolders: { type: Array, default: () => [] },
})

const emit = defineEmits(['done'])

const { t } = useI18n()
const message = useMessage()

const outputFormat = ref('keep')
const deleteSource = ref(false)
const writeTags = ref(true)
const processing = ref(false)
const error = ref(null)

const totalCount = computed(() =>
  props.selectedFiles.length + props.selectedFolders.length
)

async function handleSubmit() {
  processing.value = true
  error.value = null
  try {
    const allTargets = [...props.selectedFiles, ...props.selectedFolders]
    const options = {
      'cue-split': {
        outputFormat: outputFormat.value,
        deleteSource: deleteSource.value,
        writeTags: writeTags.value,
      },
      path: props.targetPath,
      files: allTargets,
    }
    const res = await runTool('cueSplit', options)
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
.cs-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.cs-topbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cs-title {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text);
}

.cs-desc {
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.cs-desc p {
  margin: 0;
}

.cs-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cs-section-header {
  margin-bottom: 2px;
}

.cs-section-title {
  font-size: var(--text-xs);
  font-weight: 500;
  color: var(--color-text-secondary);
}

.cs-fmt-label {
  font-weight: 500;
}

.cs-fmt-desc {
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.cs-warning {
  margin-top: 4px;
  font-size: var(--text-xs);
}

.cs-action {
  padding-top: 8px;
}

.cs-result {
  margin-top: 4px;
}

.cs-result p {
  margin: 4px 0;
}
</style>
