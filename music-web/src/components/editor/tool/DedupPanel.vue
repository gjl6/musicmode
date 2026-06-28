<template>
  <ToolPanelLayout
    :selected-files="selectedFiles"
    :selected-folders="selectedFolders"
    :target-path="targetPath"
  >
    <div class="dp-main">
      <!-- 1. 顶部标题栏 -->
      <div class="dp-topbar">
        <span class="dp-title">{{ t('workbench.tools.dedup') }}</span>
        <n-tag type="warning" size="small" :bordered="false">
          {{ t('tool.dedupStrategies') }}
        </n-tag>
      </div>

      <!-- 2. 功能说明区 -->
      <div class="dp-section">
        <div class="dp-section-header">
          <span class="dp-section-title">{{ t('encodingRepair.featureDesc') }}</span>
        </div>
        <n-alert type="info" :bordered="false" class="dp-feature-alert">
          <template #header>
            <strong>{{ t('workbench.tools.dedup') }}</strong>
          </template>
          <p>{{ t('workbench.tools.dedupDesc') }}</p>
        </n-alert>
      </div>

      <!-- 3. 检测策略区 -->
      <div class="dp-section">
        <div class="dp-section-header">
          <span class="dp-section-title">{{ t('tool.dedupStrategies') }}</span>
          <n-tag type="warning" size="small" :bordered="false">
            {{ selectedStrategies.length }} / {{ allStrategies.length }}
          </n-tag>
        </div>
        <n-checkbox
          v-for="s in allStrategies"
          :key="s.key"
          :checked="selectedStrategies.includes(s.key)"
          @update:checked="(v) => toggleStrategy(s.key, v)"
          :disabled="processing"
        >
          <span class="dp-strategy-label">{{ s.label }}</span>
          <span class="dp-strategy-desc">{{ s.desc }}</span>
        </n-checkbox>

        <!-- metadata 子选项 -->
        <div class="dp-sub-options" v-if="selectedStrategies.includes('metadata')">
          <n-form-item :label="t('tool.dedupMetadataMode')" label-placement="left" size="small">
            <n-radio-group v-model:value="metadataMode" :disabled="processing" size="small">
              <n-radio value="loose">{{ t('tool.dedupModeLoose') }}</n-radio>
              <n-radio value="standard">{{ t('tool.dedupModeStandard') }}</n-radio>
              <n-radio value="strict">{{ t('tool.dedupModeStrict') }}</n-radio>
            </n-radio-group>
          </n-form-item>
        </div>

        <!-- fingerprint 子选项 -->
        <div class="dp-sub-options" v-if="selectedStrategies.includes('fingerprint')">
          <n-form-item :label="t('tool.dedupFingerprintThreshold')" label-placement="left" size="small">
            <n-slider v-model:value="fpThreshold" :min="0.60" :max="0.95" :step="0.05"
              :disabled="processing" style="max-width: 260px" />
            <span class="dp-threshold-value">{{ fpThreshold.toFixed(2) }}</span>
          </n-form-item>
        </div>
      </div>

      <!-- 4. 提交区 -->
      <div class="dp-section">
        <n-button
          type="primary"
          block
          :loading="processing"
          :disabled="selectedStrategies.length === 0 || (!hasTargets && !props.targetPath)"
          @click="handleSubmit"
        >
          {{ processing ? t('tool.processing') : t('tool.startProcessing') }}
        </n-button>

        <n-alert v-if="error" type="error" class="dp-result-alert">{{ error }}</n-alert>
      </div>
    </div>
  </ToolPanelLayout>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NTag, NCheckbox, NFormItem, NRadioGroup, NRadio, NSlider, NButton, NAlert, useMessage } from 'naive-ui'
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

const allStrategies = [
  { key: 'hash', label: t('tool.dedupStrategyHash'), desc: t('tool.dedupStrategyHashDesc') },
  { key: 'filename', label: t('tool.dedupStrategyFileName'), desc: t('tool.dedupStrategyFileNameDesc') },
  { key: 'metadata', label: t('tool.dedupStrategyMetadata'), desc: t('tool.dedupStrategyMetadataDesc') },
  { key: 'fingerprint', label: t('tool.dedupStrategyFingerprint'), desc: t('tool.dedupStrategyFingerprintDesc') },
]

const selectedStrategies = ref(['hash', 'filename', 'metadata'])
const metadataMode = ref('standard')
const fpThreshold = ref(0.80)

const hasTargets = computed(
  () => props.selectedFiles.length > 0 || props.selectedFolders.length > 0,
)

function toggleStrategy(key, checked) {
  if (checked) {
    if (!selectedStrategies.value.includes(key)) selectedStrategies.value.push(key)
  } else {
    selectedStrategies.value = selectedStrategies.value.filter(s => s !== key)
  }
}

async function handleSubmit() {
  processing.value = true
  error.value = null
  try {
    const allTargets = [...props.selectedFiles, ...props.selectedFolders]
    const options = {
      path: props.targetPath,
      files: allTargets,
      dedup: {
        strategies: selectedStrategies.value,
        metadataMode: metadataMode.value,
        fingerprint: { threshold: fpThreshold.value },
      },
    }
    const res = await runTool('dedup', options)
    if (res?.pipelineId) {
      message.success('已提交: ' + res.pipelineId)
      emit('done')
    } else {
      message.error(res?.error || t('tool.failure'))
    }
  } catch (err) {
    const msg = err?.response?.data?.error || err.message || t('common.error')
    error.value = msg
    message.error(msg)
  } finally {
    processing.value = false
  }
}
</script>

<style scoped>
.dp-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow-y: auto;
}

.dp-topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--ct-border);
}
.dp-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ct-text);
}

.dp-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.dp-section-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.dp-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ct-text);
}
.dp-section-sub {
  font-size: 11px;
  color: var(--ct-text-3);
}

.dp-feature-alert {
  --n-padding: 12px 16px;
}
.dp-feature-alert p {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--ct-text-2);
}

.dp-strategy-label {
  font-weight: 500;
  font-size: 13px;
}
.dp-strategy-desc {
  margin-left: 8px;
  font-size: 11px;
  color: var(--ct-text-3);
}

.dp-sub-options {
  margin-left: 28px;
  margin-top: 2px;
}

.dp-threshold-value {
  margin-left: 12px;
  font-size: 13px;
  font-weight: 500;
  color: var(--ct-primary);
}

.dp-result-alert {
  margin-top: 4px;
}
</style>
