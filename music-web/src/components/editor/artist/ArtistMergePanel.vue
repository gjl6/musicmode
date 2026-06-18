<template>
  <div class="amp-root">

    <div class="amp-section">
      <div class="amp-section-title">{{ $t('artistManager.toolMergeSelected', { n: selectedList.length }) }}</div>
      <div class="amp-artist-list">
        <div v-for="a in selectedList" :key="a.id" class="amp-artist-item">
          <span class="amp-artist-name">{{ a.name }}</span>
          <span class="amp-artist-meta">{{ a.albumCount }}专辑, {{ a.songCount }}首</span>
        </div>
      </div>
    </div>


    <div class="amp-section">
      <div class="amp-section-title">{{ $t('artistManager.toolMergeKeep') }}</div>
      <n-select
        v-model:value="targetId"
        :options="targetOptions"
        :placeholder="$t('artistManager.toolMergeKeep')"
        style="width:100%"
      />
    </div>


    <div class="amp-section" v-if="sourceList.length">
      <div class="amp-section-title">{{ $t('artistManager.toolMergePreview') }}</div>
      <div class="amp-preview">
        <div v-for="s in sourceList" :key="s.id" class="amp-preview-item">
          <n-icon :size="14"><ArrowForwardOutline /></n-icon>
          <span>{{ s.name }}</span>
          <span class="amp-preview-meta">→ {{ targetArtist?.name || '—' }}</span>
        </div>
      </div>
      <div class="amp-warning">
        <n-icon :size="16"><WarningOutline /></n-icon>
        <span>{{ $t('artistManager.toolMergeWarning') }}</span>
      </div>
    </div>


    <div class="amp-footer">
      <n-button size="small" @click="$emit('done')">{{ $t('artistManager.cancel') }}</n-button>
      <n-button type="primary" size="small" :loading="submitting" :disabled="!canSubmit" @click="doSubmit">
        {{ $t('artistManager.toolMergeConfirm') }}
      </n-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ArrowForwardOutline, WarningOutline } from '@vicons/ionicons5'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { submitMerge } from '@/api/editor/artist-manage.js'

const props = defineProps({
  artists: { type: Array, default: () => [] },
  selectedIds: { type: Array, default: () => [] },
})
const emit = defineEmits(['done'])

const { t } = useI18n()
const message = useMessage()

const submitting = ref(false)
const targetId = ref(null)

const selectedList = computed(() =>
  props.artists.filter(a => props.selectedIds.includes(a.id))
)

const targetOptions = computed(() =>
  selectedList.value.map(a => ({
    label: `${a.name} (${a.albumCount}专辑, ${a.songCount}首)`,
    value: a.id,
  }))
)

const sourceList = computed(() =>
  selectedList.value.filter(a => a.id !== targetId.value)
)

const targetArtist = computed(() =>
  selectedList.value.find(a => a.id === targetId.value)
)

const canSubmit = computed(() =>
  targetId.value && sourceList.value.length >= 1
)

if (props.selectedIds.length >= 2) {
  targetId.value = props.selectedIds[0]
}

async function doSubmit() {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const sourceIds = sourceList.value.map(a => a.id)
    const groups = [{
      label: `${targetArtist.value?.name || targetId.value} ← ${sourceIds.length}个`,
      sourceIds,
      targetId: targetId.value,
    }]
    const res = await submitMerge(groups)
    if (res?.pipelineId) {
      message.success(t('artistManager.toolMergeSuccess', { id: res.pipelineId }))
      emit('done')
    }
  } catch (e) {
    console.error('[ArtistMerge] 合并失败:', e)
    message.warning('提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.amp-root {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 4px 0;
}

.amp-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.amp-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ct-text-2);
}


.amp-artist-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 160px;
  overflow-y: auto;
}

.amp-artist-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  border-radius: 6px;
  background: var(--ct-bg-secondary);
  border: 1px solid var(--ct-border);
}

.amp-artist-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--ct-text);
}

.amp-artist-meta {
  font-size: 11px;
  color: var(--ct-text-3);
}


.amp-preview {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.amp-preview-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 6px;
  background: rgb(var(--ct-accent-rgb) / 0.05);
  font-size: 12px;
  color: var(--ct-text-2);
}

.amp-preview-item :deep(.n-icon) {
  color: var(--ct-accent);
}

.amp-preview-meta {
  color: var(--ct-accent);
  font-weight: 500;
}


.amp-warning {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgb(var(--ct-warning-rgb, 245 158 11) / 0.1);
  border: 1px solid rgb(var(--ct-warning-rgb, 245 158 11) / 0.3);
  font-size: 12px;
  color: var(--ct-text-2);
  line-height: 1.6;
}

.amp-warning :deep(.n-icon) {
  color: rgb(var(--ct-warning-rgb, 245 158 11));
  flex-shrink: 0;
  margin-top: 1px;
}

.amp-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--ct-border);
}
</style>
