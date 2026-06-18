<template>
  <ToolPanelLayout
    :selected-files="selectedFiles"
    :selected-folders="selectedFolders"
    :target-path="targetPath"
  >
    <div class="fc-main">

      <div class="fc-topbar">
        <span class="fc-title">{{ t('formatConvert.title') }}</span>
        <n-tag type="info" size="small" :bordered="false">
          {{ t('formatConvert.sourceFiles', { n: totalCount }) }}
        </n-tag>
      </div>


      <div class="fc-section">
        <div class="fc-section-header">
          <span class="fc-section-title">{{ t('formatConvert.targetFormat') }}</span>
        </div>
        <n-radio-group v-model:value="targetFormat" name="targetFormat">
          <n-space>
            <n-radio v-for="fmt in formatOptions" :key="fmt.value" :value="fmt.value">
              <span class="fc-format-label">{{ fmt.label }}</span>
              <span class="fc-format-desc">— {{ fmt.desc }}</span>
            </n-radio>
          </n-space>
        </n-radio-group>
      </div>


      <div class="fc-section">
        <div class="fc-section-header">
          <span class="fc-section-title">{{ t('formatConvert.bitrate') }}</span>
        </div>
        <template v-if="isLossy">
          <n-select
            v-model:value="bitrate"
            :options="bitrateOptions"
            style="max-width: 220px"
          />
        </template>
        <template v-else>
          <n-alert type="info" :bordered="false" class="fc-lossless-hint">
            {{ t('formatConvert.losslessHint') }}
          </n-alert>
        </template>
      </div>


      <n-collapse>
        <n-collapse-item :title="t('formatConvert.advanced')" name="advanced">
          <div class="fc-section">
            <div class="fc-section-header">
              <span class="fc-section-title">{{ t('formatConvert.sampleRate') }}</span>
            </div>
            <n-select
              v-model:value="sampleRate"
              :options="sampleRateOptions"
              style="max-width: 220px"
            />
          </div>
          <div class="fc-section">
            <div class="fc-section-header">
              <span class="fc-section-title">{{ t('formatConvert.channels') }}</span>
            </div>
            <n-select
              v-model:value="channels"
              :options="channelsOptions"
              style="max-width: 220px"
            />
          </div>
        </n-collapse-item>
      </n-collapse>


      <div class="fc-section">
        <n-checkbox v-model:checked="deleteOriginal">
          {{ t('formatConvert.deleteOriginal') }}
        </n-checkbox>
        <n-text v-if="deleteOriginal" type="warning" depth="2" class="fc-warning">
          ⚠ {{ t('formatConvert.deleteOriginalWarning') }}
        </n-text>
      </div>


      <div class="fc-section">
        <n-button
          type="primary"
          block
          :loading="processing"
          :disabled="!hasTargets"
          @click="handleSubmit"
        >
          {{ processing ? t('tool.processing') : t('tool.startProcessing') }}
        </n-button>

        <n-alert v-if="result" :type="result.success ? 'success' : 'error'" class="fc-result-alert">
          <template #header>
            <span v-if="result.success">{{ t('tool.success') }}</span>
            <span v-else>{{ t('tool.failure') }}</span>
          </template>
          <p>{{ t('tool.duration', { ms: result.durationMs ?? 0 }) }}</p>
        </n-alert>

        <n-alert v-if="error" type="error" class="fc-result-alert">{{ error }}</n-alert>
      </div>
    </div>
  </ToolPanelLayout>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NTag, NButton, NAlert, NSelect, NText, NCheckbox,
  NRadioGroup, NRadio, NSpace, NCollapse, NCollapseItem, useMessage,
} from 'naive-ui'
import ToolPanelLayout from '@/components/editor/tool/ToolPanelLayout.vue'
import { runTool } from '@/api/editor/tools.js'

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
const result = ref(null)
const error = ref(null)


const targetFormat = ref('mp3')
const bitrate = ref(320)
const sampleRate = ref(0)
const channels = ref(0)
const deleteOriginal = ref(false)


const formatOptions = [
  { value: 'mp3', label: 'MP3', desc: t('formatConvert.formatDescs.mp3') },
  { value: 'flac', label: 'FLAC', desc: t('formatConvert.formatDescs.flac') },
  { value: 'wav', label: 'WAV', desc: t('formatConvert.formatDescs.wav') },
  { value: 'aac', label: 'AAC (M4A)', desc: t('formatConvert.formatDescs.aac') },
  { value: 'ogg', label: 'OGG', desc: t('formatConvert.formatDescs.ogg') },
  { value: 'opus', label: 'OPUS', desc: t('formatConvert.formatDescs.opus') },
  { value: 'alac', label: 'ALAC (M4A)', desc: t('formatConvert.formatDescs.alac') },
  { value: 'aiff', label: 'AIFF', desc: t('formatConvert.formatDescs.aiff') },
  { value: 'wma', label: 'WMA', desc: t('formatConvert.formatDescs.wma') },
]

const LOSSY_FORMATS = new Set(['mp3', 'aac', 'ogg', 'opus', 'wma'])

const isLossy = computed(() => LOSSY_FORMATS.has(targetFormat.value))

const bitrateOptions = computed(() => {
  const fmt = targetFormat.value
  if (fmt === 'mp3') {
    return [
      { label: 'V0 (~245 kbps VBR)', value: 0 },
      { label: '320 kbps CBR', value: 320 },
      { label: '256 kbps CBR', value: 256 },
      { label: '192 kbps CBR', value: 192 },
      { label: '128 kbps CBR', value: 128 },
    ]
  }
  if (fmt === 'aac') {
    return [
      { label: '320 kbps', value: 320 },
      { label: '256 kbps', value: 256 },
      { label: '192 kbps', value: 192 },
      { label: '128 kbps', value: 128 },
    ]
  }
  if (fmt === 'ogg') {
    return [
      { label: 'q10 (~500 kbps)', value: 500 },
      { label: 'q8 (~260 kbps)', value: 260 },
      { label: 'q6 (~192 kbps)', value: 192 },
      { label: 'q5 (~160 kbps)', value: 160 },
      { label: 'q3 (~96 kbps)', value: 96 },
    ]
  }
  if (fmt === 'opus') {
    return [
      { label: '256 kbps', value: 256 },
      { label: '192 kbps', value: 192 },
      { label: '160 kbps', value: 160 },
      { label: '128 kbps', value: 128 },
      { label: '96 kbps', value: 96 },
      { label: '64 kbps', value: 64 },
    ]
  }
  if (fmt === 'wma') {
    return [
      { label: '320 kbps', value: 320 },
      { label: '256 kbps', value: 256 },
      { label: '192 kbps', value: 192 },
      { label: '128 kbps', value: 128 },
    ]
  }
  return [{ label: '—', value: 320 }]
})

watch(targetFormat, (newFmt) => {
  if (newFmt === 'mp3') bitrate.value = 320
  else if (newFmt === 'aac') bitrate.value = 256
  else if (newFmt === 'ogg') bitrate.value = 192
  else if (newFmt === 'opus') bitrate.value = 160
  else if (newFmt === 'wma') bitrate.value = 320
})

const sampleRateOptions = [
  { label: t('formatConvert.keepOriginal'), value: 0 },
  { label: '44100 Hz', value: 44100 },
  { label: '48000 Hz', value: 48000 },
  { label: '96000 Hz', value: 96000 },
]

const channelsOptions = [
  { label: t('formatConvert.keepOriginal'), value: 0 },
  { label: t('formatConvert.mono'), value: 1 },
  { label: t('formatConvert.stereo'), value: 2 },
]

const totalCount = computed(() => props.selectedFiles.length + props.selectedFolders.length)

const hasTargets = computed(() => totalCount.value > 0)


async function handleSubmit() {
  processing.value = true
  error.value = null
  result.value = null
  try {
    const allTargets = [...props.selectedFiles, ...props.selectedFolders]
    const moduleConfig = {
      targetFormat: targetFormat.value,
      bitrate: bitrate.value,
      sampleRate: sampleRate.value,
      channels: channels.value,
      deleteOriginal: deleteOriginal.value,
    }
    const options = {
      path: props.targetPath,
      files: allTargets,
      'format-convert': moduleConfig,
    }
    const res = await runTool('formatConvert', options)
    result.value = res
    if (res.success) {
      message?.success(t('tool.success'))
      emit('done')
    } else {
      message?.error(res.error || t('tool.failure'))
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
.fc-main {
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow-y: auto;
}

.fc-topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border);
}

.fc-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
}

.fc-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.fc-section-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.fc-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.fc-format-label {
  font-weight: 500;
}

.fc-format-desc {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.fc-lossless-hint {
  --n-padding: 8px 14px;
}

.fc-warning {
  font-size: 12px;
}

.fc-result-alert {
  margin-top: 4px;
}
</style>
