<template>
  <ToolPanelLayout
    :selected-files="selectedFiles"
    :selected-folders="selectedFolders"
    :target-path="targetPath"
  >
    <div class="lc-main">

      <div class="lc-topbar">
        <span class="lc-title">{{ t('langConvert.title') }}</span>
        <n-tag type="warning" size="small" :bordered="false">
          {{ t('langConvert.autoDetect') }}
        </n-tag>
      </div>


      <div class="lc-section">
        <div class="lc-section-header">
          <span class="lc-section-title">{{ t('langConvert.direction') }}</span>
        </div>
        <div class="lc-direction-row">
          <n-button
            :type="direction === 'toSimplified' ? 'primary' : 'default'"
            size="small"
            @click="direction = 'toSimplified'"
          >
            {{ t('langConvert.toSimplified') }}
          </n-button>
          <n-button
            :type="direction === 'toTraditional' ? 'primary' : 'default'"
            size="small"
            @click="direction = 'toTraditional'"
          >
            {{ t('langConvert.toTraditional') }}
          </n-button>
        </div>
      </div>


      <div class="lc-section">
        <div class="lc-section-header">
          <span class="lc-section-title">{{ t('langConvert.targetFields') }}</span>
          <n-tag type="warning" size="small" :bordered="false">
            {{ t('langConvert.fieldsCount', { n: selectedFields.length }) }}
          </n-tag>
        </div>
        <div class="lc-fields-row">
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
            :placeholder="t('langConvert.addField')"
            :options="availableFields"
            size="small"
            style="width: 140px"
            :consistent-menu-width="false"
            @update:value="addField"
          />
          <n-text v-else depth="3" class="lc-all-selected">{{ t('langConvert.allFieldsSelected') }}</n-text>
        </div>
      </div>


      <div class="lc-section">
        <div class="lc-section-header">
          <span class="lc-section-title">{{ t('langConvert.featureDesc') }}</span>
          <span class="lc-section-sub">{{ t('langConvert.autoConvert') }}</span>
        </div>
        <n-alert type="warning" :bordered="false" class="lc-feature-alert">
          <template #header>
            <strong>{{ t('langConvert.featureTitle') }}</strong>
          </template>
          <p>{{ t('langConvert.featureContent') }}</p>
        </n-alert>
      </div>


      <div class="lc-section">
        <div class="lc-section-header">
          <span class="lc-section-title">{{ t('langConvert.examples') }}</span>
        </div>
        <div class="lc-examples">
          <div class="lc-example-item" v-for="ex in currentExamples" :key="ex.from">
            <code class="lc-from">{{ ex.from }}</code>
            <span class="lc-arrow">→</span>
            <code class="lc-to">{{ ex.to }}</code>
          </div>
        </div>
      </div>


      <div class="lc-section">
        <n-button
          type="primary"
          block
          :loading="processing"
          :disabled="!hasTargets && !props.targetPath"
          @click="handleSubmit"
        >
          {{ processing ? t('tool.processing') : t('tool.startProcessing') }}
        </n-button>

        <n-alert v-if="result" :type="result.success ? 'success' : 'error'" class="lc-result-alert">
          <template #header>
            <span v-if="result.success">{{ t('tool.success') }}</span>
            <span v-else>{{ t('tool.failure') }}</span>
          </template>
          <p>{{ t('tool.duration', { ms: result.durationMs ?? 0 }) }}</p>
        </n-alert>

        <n-alert v-if="error" type="error" class="lc-result-alert">{{ error }}</n-alert>
      </div>
    </div>
  </ToolPanelLayout>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NTag, NButton, NAlert, NSelect, NText, useMessage } from 'naive-ui'
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
const direction = ref('toSimplified')


const ALL_FIELDS = [
  { value: 'song.title', labelKey: 'songTitle' },
  { value: 'song.composer', labelKey: 'songComposer' },
  { value: 'song.lyricist', labelKey: 'songLyricist' },
  { value: 'artist.artistName', labelKey: 'artistName' },
  { value: 'artist.country', labelKey: 'artistCountry' },
  { value: 'artist.introduction', labelKey: 'artistIntroduction' },
  { value: 'album.albumName', labelKey: 'albumName' },
  { value: 'album.introduction', labelKey: 'albumIntroduction' },
  { value: 'album.company', labelKey: 'albumCompany' },
  { value: 'style.styleName', labelKey: 'styleName' },
  { value: 'style.description', labelKey: 'styleDescription' },
  { value: 'lyric.content', labelKey: 'lyricContent' },
]

const selectedFields = ref(ALL_FIELDS.map(f => f.value))
const selectValue = ref(null)

const availableFields = computed(() =>
  ALL_FIELDS
    .filter(f => !selectedFields.value.includes(f.value))
    .map(f => ({ label: t('langConvert.fields.' + f.labelKey), value: f.value })),
)

function fieldLabel(field) {
  const entry = ALL_FIELDS.find(f => f.value === field)
  return entry ? t('langConvert.fields.' + entry.labelKey) : field
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

const examplesToSimplified = [
  { from: '臺灣', to: '台湾' },
  { from: '音樂', to: '音乐' },
  { from: '張學友', to: '张学友' },
  { from: '專輯簡介', to: '专辑简介' },
]

const examplesToTraditional = [
  { from: '台湾', to: '臺灣' },
  { from: '音乐', to: '音樂' },
  { from: '张学友', to: '張學友' },
  { from: '专辑简介', to: '專輯簡介' },
]

const currentExamples = computed(() =>
  direction.value === 'toSimplified' ? examplesToSimplified : examplesToTraditional,
)

async function handleSubmit() {
  processing.value = true
  error.value = null
  result.value = null
  try {
    const allTargets = [...props.selectedFiles, ...props.selectedFolders]
    const options = {
      path: props.targetPath,
      files: allTargets,
      'chinese-convert': {
        direction: direction.value,
        fields: selectedFields.value,
      },
    }
    const res = await runTool('langConvert', options)
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
.lang-convert-panel {
  display: flex;
  gap: 16px;
  overflow: hidden;
}


.lc-files-col {
  width: 175px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--ct-border);
  padding-right: 12px;
  max-height: 500px;
}
.lc-files-header {
  font-size: 11px;
  font-weight: 600;
  color: var(--ct-text);
  margin-bottom: 8px;
  flex-shrink: 0;
}
.lc-file-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.lc-file-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-size: 11px;
  padding: 1px 0;
}
.lc-file-idx {
  color: var(--ct-text-3);
  min-width: 18px;
  text-align: right;
  font-size: 10px;
  flex-shrink: 0;
}
.lc-file-name {
  font-family: 'SF Mono', monospace;
  color: var(--ct-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}


.lc-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow-y: auto;
}


.lc-topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--ct-border);
}
.lc-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ct-text);
}


.lc-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.lc-section-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.lc-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ct-text);
}
.lc-section-sub {
  font-size: 11px;
  color: var(--ct-text-3);
}


.lc-direction-row {
  display: flex;
  gap: 8px;
}


.lc-feature-alert {
  --n-padding: 12px 16px;
}
.lc-feature-alert p {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--ct-text-2);
}


.lc-examples {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 14px;
  background: var(--ct-hover, rgba(128, 128, 128, 0.06));
  border-radius: 6px;
}
.lc-example-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
}
.lc-from {
  font-family: monospace;
  color: var(--color-destructive);
  background: rgb(var(--color-destructive-rgb) / 0.08);
  padding: 2px 6px;
  border-radius: 3px;
}
.lc-arrow {
  color: var(--color-text-tertiary);
  font-weight: 600;
}
.lc-to {
  font-family: monospace;
  color: var(--color-success);
  background: rgb(var(--color-success-rgb) / 0.08);
  padding: 2px 6px;
  border-radius: 3px;
}


.lc-result-alert {
  margin-top: 4px;
}
</style>
