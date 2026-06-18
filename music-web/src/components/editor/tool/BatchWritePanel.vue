<template>
  <div class="batch-write-panel">

    <div class="bw-files-col">
      <div class="bw-files-header">
        <template v-if="selectedFiles.length || selectedFolders.length">
          {{ t('toolPanel.processingItems', { n: selectedFiles.length + selectedFolders.length }) }}
        </template>
        <template v-else>{{ t('toolPanel.directory', { path: targetPath || '—' }) }}</template>
      </div>
      <div v-if="selectedFiles.length || selectedFolders.length" class="bw-file-list">
        <div v-for="f in selectedFolders" :key="'d' + f" class="bw-file-row bw-folder-row">
          <span class="bw-file-idx">📁</span>
          <span class="bw-file-name">{{ f }}</span>
        </div>
        <div v-for="(f, i) in selectedFiles" :key="f" class="bw-file-row">
          <span class="bw-file-idx">{{ selectedFolders.length + i + 1 }}.</span>
          <span class="bw-file-name">{{ f.split('/').pop() }}</span>
        </div>
      </div>
    </div>


    <div class="bw-main">

      <div class="bw-topbar">
        <span class="bw-title">{{ t('batchWrite.title') }}</span>
        <n-tag type="warning" size="small" :bordered="false">
          {{ t('batchWrite.unifiedWrite') }}
        </n-tag>
      </div>


      <div class="bw-sections">
        <EditSection :title="t('batchWrite.songInfo')" :default-open="true">
          <div class="field-row">
            <label>{{ t('batchWrite.fields.title') }}</label>
            <n-input size="small" v-model:value="form.song.title" placeholder="—" />
          </div>
          <div class="field-row field-row-2col">
            <div class="field-col">
              <label>{{ t('batchWrite.fields.year') }}</label>
              <n-input size="small" v-model:value="form.song.year" placeholder="—" />
            </div>
            <div class="field-col">
              <label>{{ t('batchWrite.fields.language') }}</label>
              <n-input size="small" v-model:value="form.song.language" placeholder="—" />
            </div>
          </div>
          <div class="field-row field-row-2col">
            <div class="field-col">
              <label>{{ t('batchWrite.fields.composer') }}</label>
              <n-input size="small" v-model:value="form.song.composer" placeholder="—" />
            </div>
            <div class="field-col">
              <label>{{ t('batchWrite.fields.lyricist') }}</label>
              <n-input size="small" v-model:value="form.song.lyricist" placeholder="—" />
            </div>
          </div>
        </EditSection>

        <EditSection :title="t('batchWrite.albumInfo')" :default-open="true">
          <div class="field-row">
            <label>{{ t('batchWrite.fields.album') }}</label>
            <n-input size="small" v-model:value="form.album.albumName" placeholder="—" />
          </div>
          <div class="field-row">
            <label>{{ t('batchWrite.fields.albumYear') }}</label>
            <n-input size="small" v-model:value="form.album.albumYear" placeholder="—" />
          </div>
          <div class="field-row">
            <label>{{ t('batchWrite.fields.company') }}</label>
            <n-input size="small" v-model:value="form.album.company" placeholder="—" />
          </div>
        </EditSection>

        <EditSection :title="t('batchWrite.artistInfo')" :default-open="true">

          <div class="artist-tabs">
            <span
              v-for="(a, i) in form.artists"
              :key="i"
              class="artist-tab"
              :class="{ active: i === form.activeArtistIndex }"
              @click="switchArtist(i)"
            >
              <span class="artist-tab-name">{{ a.artistName || `Artist ${i + 1}` }}</span>
              <span
                v-if="form.artists.length > 1"
                class="artist-tab-close"
                @click.stop="removeArtist(i)"
              >×</span>
            </span>
            <span class="artist-tab artist-tab-add" @click="addArtist()">+</span>
          </div>

          <div class="field-row">
            <label>{{ t('batchWrite.fields.artist') }}</label>
            <n-input size="small" v-model:value="form.artist.artistName" placeholder="—" />
          </div>
          <div class="field-row field-row-2col">
            <div class="field-col">
              <label>{{ t('batchWrite.fields.gender') }}</label>
              <n-select
                size="small"
                v-model:value="form.artist.gender"
                :options="genderOptions"
              />
            </div>
            <div class="field-col">
              <label>{{ t('batchWrite.fields.country') }}</label>
              <n-input size="small" v-model:value="form.artist.country" placeholder="—" />
            </div>
          </div>
          <div class="field-row">
            <label>{{ t('batchWrite.fields.introduction') }}</label>
            <n-input
              type="textarea"
              size="small"
              :autosize="{ minRows: 1, maxRows: 2 }"
              v-model:value="form.artist.introduction"
              placeholder="—"
            />
          </div>
        </EditSection>

        <EditSection :title="t('batchWrite.styleInfo')" :default-open="true">
          <div class="field-row">
            <label>{{ t('batchWrite.fields.genre') }}</label>
            <n-input size="small" v-model:value="form.style.styleName" placeholder="—" />
          </div>
        </EditSection>
      </div>


      <div class="bw-section">
        <n-button
          type="primary"
          block
          :loading="processing"
          :disabled="!hasTargets && !props.targetPath"
          @click="handleSubmit"
        >
          {{ processing ? t('tool.processing') : t('tool.startProcessing') }}
        </n-button>

        <n-alert v-if="result" :type="result.success ? 'success' : 'error'" class="bw-result-alert">
          <template #header>
            <span v-if="result.success">{{ t('tool.success') }}</span>
            <span v-else>{{ t('tool.failure') }}</span>
          </template>
          <p>{{ t('tool.duration', { ms: result.durationMs ?? 0 }) }}</p>
        </n-alert>

        <n-alert v-if="error" type="error" class="bw-result-alert">{{ error }}</n-alert>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NTag, NButton, NAlert, NInput, NSelect, useMessage } from 'naive-ui'
import { runTool } from '@/api/editor/tools.js'
import { toEditMeta, toMusicMetadata } from '@/utils/musicMeta.js'
import EditSection from '@/components/editor/metadata/EditSection.vue'

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

function initForm() {
  const empty = toEditMeta(null)
  empty.artists = [{ artistName: '', gender: 0, country: '', introduction: '', artistCover: '' }]
  empty.activeArtistIndex = 0
  return empty
}

const form = reactive(initForm())

const genderOptions = [
  { label: '未知', value: 0 },
  { label: '男', value: 1 },
  { label: '女', value: 2 },
  { label: '组合', value: 3 },
]

function switchArtist(index) {
  const artists = form.artists
  if (!artists || index < 0 || index >= artists.length) return
  artists[form.activeArtistIndex] = { ...form.artist }
  form.activeArtistIndex = index
  Object.assign(form.artist, artists[index])
}

function addArtist() {
  const newArtist = { artistName: '', gender: 0, country: '', introduction: '', artistCover: '' }
  form.artists.push(newArtist)
  switchArtist(form.artists.length - 1)
}

function removeArtist(index) {
  if (!form.artists || form.artists.length <= 1) return
  form.artists.splice(index, 1)
  const newIndex = Math.min(index, form.artists.length - 1)
  if (form.activeArtistIndex >= form.artists.length) {
    form.activeArtistIndex = newIndex
    Object.assign(form.artist, form.artists[newIndex])
  } else if (form.activeArtistIndex === index) {
    switchArtist(newIndex)
  } else if (form.activeArtistIndex > index) {
    form.activeArtistIndex--
  }
}

const hasTargets = computed(
  () => props.selectedFiles.length > 0 || props.selectedFolders.length > 0,
)

async function handleSubmit() {
  processing.value = true
  error.value = null
  result.value = null
  try {
        if (form.artists && form.artists.length > 0) {
      form.artists[form.activeArtistIndex] = { ...form.artist }
    }
        const meta = toMusicMetadata(form)
    const allTargets = [...props.selectedFiles, ...props.selectedFolders]
    const options = {
      path: props.targetPath,
      files: allTargets,
      'batch-write': { metadata: meta },
    }
    const res = await runTool('batchWrite', options)
    result.value = res
    if (res.success) {
      message?.success(t('tool.success'))
      Object.assign(form, initForm())
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

.artist-tabs {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 8px;
}
.artist-tab {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  border: 1px solid var(--ct-border);
  background: var(--ct-bg-secondary);
  color: var(--ct-text-2);
  transition: all 0.15s;
}
.artist-tab:hover {
  background: var(--color-sidebar-hover);
  color: var(--color-text);
}
.artist-tab.active {
  background: rgb(var(--color-accent-rgb) / 0.12);
  border-color: rgb(var(--color-accent-rgb) / 0.4);
  color: var(--color-accent);
}
.artist-tab-name {
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.artist-tab-close {
  font-size: 13px;
  line-height: 1;
  opacity: 0.5;
}
.artist-tab-close:hover {
  opacity: 1;
  color: var(--n-color-error);
}
.artist-tab-add {
  padding: 3px 8px;
  font-size: 14px;
  line-height: 1;
}
.artist-tab-add:hover {
  border-color: rgb(var(--color-accent-rgb) / 0.5);
  color: var(--color-accent);
}

.batch-write-panel {
  display: flex;
  gap: 16px;
  overflow: hidden;
}


.bw-files-col {
  width: 175px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--ct-border);
  padding-right: 12px;
  max-height: 500px;
}
.bw-files-header {
  font-size: 11px;
  font-weight: 600;
  color: var(--ct-text);
  margin-bottom: 8px;
  flex-shrink: 0;
}
.bw-file-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.bw-file-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-size: 11px;
  padding: 1px 0;
}
.bw-file-idx {
  color: var(--ct-text-3);
  min-width: 18px;
  text-align: right;
  font-size: 10px;
  flex-shrink: 0;
}
.bw-file-name {
  font-family: 'SF Mono', monospace;
  color: var(--ct-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}


.bw-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
}


.bw-topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--ct-border);
}
.bw-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ct-text);
}


.bw-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}


.bw-result-alert {
  margin-top: 4px;
}
</style>

<style>


.bw-sections {
  display: flex;
  flex-direction: column;
}

.bw-sections .edit-section .section-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 0 6px;
  cursor: pointer;
  user-select: none;
  transition: color 0.15s;
}

.bw-sections .edit-section .section-header:hover {
  color: var(--n-color-primary);
}

.bw-sections .edit-section .section-chevron {
  flex-shrink: 0;
  transition: transform 0.2s;
  opacity: 0.5;
}

.bw-sections .edit-section .section-chevron.rotated {
  transform: rotate(90deg);
  opacity: 0.8;
}

.bw-sections .edit-section .section-title {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.03em;
  text-transform: uppercase;
}

.bw-sections .edit-section .section-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-bottom: 6px;
}


.bw-sections .field-row {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.bw-sections .field-row label {
  font-size: 11px;
  font-weight: 500;
  color: var(--ct-text-2);
  letter-spacing: 0.02em;
  padding-left: 1px;
}

.bw-sections .field-row-2col {
  flex-direction: row;
  gap: 10px;
}

.bw-sections .field-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}


.bw-sections .n-input {
  --n-color: var(--ct-bg-secondary);
  --n-color-focus: var(--ct-bg-secondary);
  --n-text-color: var(--ct-text);
  --n-border: 1px solid var(--ct-border);
  --n-border-focus: 1px solid var(--n-color-primary);
  --n-border-radius: 6px;
  --n-font-size: 12.5px;
  --n-padding: 6px 10px;
}
</style>
