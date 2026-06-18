<template>
  <ToolPanelLayout
    :selected-files="selectedFiles"
    :selected-folders="selectedFolders"
    :target-path="targetPath"
  >
    <div class="er-main">

      <div class="er-topbar">
        <span class="er-title">{{ t('encodingRepair.title') }}</span>
        <n-tag type="warning" size="small" :bordered="false">
          {{ t('encodingRepair.smartDetection') }}
        </n-tag>
      </div>


      <div class="er-section">
        <div class="er-section-header">
          <span class="er-section-title">{{ t('encodingRepair.featureDesc') }}</span>
          <span class="er-section-sub">{{ t('encodingRepair.autoRepair') }}</span>
        </div>
        <n-alert type="warning" :bordered="false" class="er-feature-alert">
          <template #header>
            <strong>{{ t('encodingRepair.featureTitle') }}</strong>
          </template>
          <p>{{ t('encodingRepair.featureContent') }}</p>
        </n-alert>
      </div>


      <div class="er-section">
        <div class="er-section-header">
          <span class="er-section-title">{{ t('encodingRepair.detectionScope') }}</span>
          <n-tag type="warning" size="small" :bordered="false">
            {{ t('encodingRepair.fieldsCount', { n: selectedFields.length }) }}
          </n-tag>
        </div>
        <div class="er-fields-row">
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
            :placeholder="t('encodingRepair.addField')"
            :options="availableFields"
            size="small"
            style="width: 140px"
            :consistent-menu-width="false"
            @update:value="addField"
          />
          <n-text v-else depth="3" class="er-all-selected">{{ t('encodingRepair.allFieldsSelected') }}</n-text>
        </div>
        <n-text depth="3" class="er-hint">{{ t('encodingRepair.fieldsHint') }}</n-text>
      </div>


      <div class="er-section">
        <div class="er-section-header">
          <span class="er-section-title">{{ t('encodingRepair.commonProblems') }}</span>
          <span class="er-section-sub">{{ t('encodingRepair.referenceExample') }}</span>
        </div>
        <div class="er-examples">
          <div class="er-example-item" v-for="ex in examples" :key="ex.garbled">
            <span class="er-dot"></span>
            <code class="er-garbled">{{ ex.garbled }}</code>
            <span class="er-arrow">→</span>
            <code class="er-fixed">{{ ex.fixed }}</code>
          </div>
        </div>
      </div>


      <div class="er-section">
        <n-button
          type="primary"
          block
          :loading="processing"
          :disabled="selectedFields.length === 0 || (!hasTargets && !props.targetPath)"
          @click="handleSubmit"
        >
          {{ processing ? t('tool.processing') : t('tool.startProcessing') }}
        </n-button>

        <n-alert v-if="result" :type="result.success ? 'success' : 'error'" class="er-result-alert">
          <template #header>
            <span v-if="result.success">{{ t('tool.success') }}</span>
            <span v-else>{{ t('tool.failure') }}</span>
          </template>
          <p>{{ t('tool.duration', { ms: result.durationMs ?? 0 }) }}</p>
        </n-alert>

        <n-alert v-if="error" type="error" class="er-result-alert">{{ error }}</n-alert>
      </div>
    </div>
  </ToolPanelLayout>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NTag, NSelect, NButton, NAlert, NText, useMessage } from 'naive-ui'
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
const result = ref(null)
const error = ref(null)

const ALL_FIELDS = [
  { value: 'song.title', labelKey: 'fields.songTitle' },
  { value: 'song.year', labelKey: 'fields.songYear' },
  { value: 'song.language', labelKey: 'fields.songLanguage' },
  { value: 'song.composer', labelKey: 'fields.songComposer' },
  { value: 'song.lyricist', labelKey: 'fields.songLyricist' },
  { value: 'album.albumName', labelKey: 'fields.albumName' },
  { value: 'album.albumYear', labelKey: 'fields.albumYear' },
  { value: 'album.introduction', labelKey: 'fields.albumIntroduction' },
  { value: 'album.company', labelKey: 'fields.albumCompany' },
  { value: 'album.language', labelKey: 'fields.albumLanguage' },
  { value: 'artist.artistName', labelKey: 'fields.artistName' },
  { value: 'artist.country', labelKey: 'fields.artistCountry' },
  { value: 'style.styleName', labelKey: 'fields.styleName' },
  { value: 'lyric.content', labelKey: 'fields.lyricContent' },
]

const selectedFields = ref([
  'song.title',
  'artist.artistName',
  'album.albumName',
])

const selectValue = ref(null)

const availableFields = computed(() =>
  ALL_FIELDS
    .filter(f => !selectedFields.value.includes(f.value))
    .map(f => ({ label: t('encodingRepair.' + f.labelKey), value: f.value })),
)

const hasTargets = computed(
  () => props.selectedFiles.length > 0 || props.selectedFolders.length > 0,
)

const examples = [
  { garbled: 'ä¸æ', fixed: '中文' },
  { garbled: '???', fixed: '汉字' },
  { garbled: 'öÐïÄ', fixed: '中文' },
]

function fieldLabel(field) {
  const entry = ALL_FIELDS.find(f => f.value === field)
  return entry ? t('encodingRepair.' + entry.labelKey) : field
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

async function handleSubmit() {
  processing.value = true
  error.value = null
  result.value = null
  try {
    const allTargets = [...props.selectedFiles, ...props.selectedFolders]
    const options = {
      path: props.targetPath,
      files: allTargets,
      'encoding-repair': {
        fields: selectedFields.value,
      },
    }
    const res = await runTool('encodingRepair', options)
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

.er-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow-y: auto;
}


.er-topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--ct-border);
}
.er-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ct-text);
}


.er-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.er-section-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.er-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ct-text);
}
.er-section-sub {
  font-size: 11px;
  color: var(--ct-text-3);
}


.er-feature-alert {
  --n-padding: 12px 16px;
}
.er-feature-alert p {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--ct-text-2);
}


.er-fields-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.er-hint {
  font-size: 12px;
}
.er-all-selected {
  font-size: 12px;
}


.er-examples {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 14px;
  background: var(--ct-hover, rgba(128, 128, 128, 0.06));
  border-radius: 6px;
}
.er-example-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
}
.er-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-destructive);
  flex-shrink: 0;
}
.er-garbled {
  font-family: monospace;
  color: var(--color-destructive);
  background: rgb(var(--color-destructive-rgb) / 0.08);
  padding: 2px 6px;
  border-radius: 3px;
}
.er-arrow {
  color: var(--color-text-tertiary);
  font-weight: 600;
}
.er-fixed {
  font-family: monospace;
  color: var(--color-success);
  background: rgb(var(--color-success-rgb) / 0.08);
  padding: 2px 6px;
  border-radius: 3px;
}


.er-result-alert {
  margin-top: 4px;
}
</style>
