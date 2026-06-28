<template>
  <div class="amp-root">
    <!-- 已选专辑 -->
    <div class="amp-section">
      <div class="amp-section-title">{{ $t('albumManager.toolMergeSelected', { n: selectedList.length }) }}</div>
      <div class="amp-album-list">
        <div v-for="a in selectedList" :key="a.id" class="amp-album-item">
          <span class="amp-album-name">{{ a.name || a.albumName }}</span>
          <span class="amp-album-meta">{{ a.songCount }}{{ $t('albumManager.songCount') }}</span>
        </div>
      </div>
    </div>

    <!-- 保留目标 -->
    <div class="amp-section">
      <div class="amp-section-title">{{ $t('albumManager.toolMergeKeep') }}</div>
      <n-select
        v-model:value="targetId"
        :options="targetOptions"
        :placeholder="$t('albumManager.toolMergeKeep')"
        style="width:100%"
      />
    </div>

    <!-- 影响预览 -->
    <div class="amp-section" v-if="sourceList.length">
      <div class="amp-section-title">{{ $t('albumManager.toolMergePreview') }}</div>
      <div class="amp-preview">
        <div v-for="s in sourceList" :key="s.id" class="amp-preview-item">
          <n-icon :size="14"><ArrowForwardOutline /></n-icon>
          <span>{{ s.name || s.albumName }}</span>
          <span class="amp-preview-meta">→ {{ targetAlbum?.name || targetAlbum?.albumName || '—' }}</span>
        </div>
      </div>
      <div class="amp-warning">
        <n-icon :size="16"><WarningOutline /></n-icon>
        <span>{{ $t('albumManager.toolMergeWarning') }}</span>
      </div>
    </div>

    <!-- 提交 -->
    <div class="amp-footer">
      <n-button size="small" @click="$emit('done')">{{ $t('albumManager.cancel') }}</n-button>
      <n-button type="primary" size="small" :loading="submitting" :disabled="!canSubmit" @click="doSubmit">
        {{ $t('albumManager.toolMergeConfirm') }}
      </n-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ArrowForwardOutline, WarningOutline } from '@vicons/ionicons5'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { submitMerge } from '@/api/editor/album-manage.js'

const props = defineProps({
  albums: { type: Array, default: () => [] },
  selectedIds: { type: Array, default: () => [] },
})
const emit = defineEmits(['done'])

const { t } = useI18n()
const message = useMessage()

const submitting = ref(false)
const targetId = ref(null)

const selectedList = computed(() =>
  props.albums.filter(a => props.selectedIds.includes(a.id))
)

const targetOptions = computed(() =>
  selectedList.value.map(a => ({
    label: `${a.name || a.albumName} (${a.songCount}${t('albumManager.songCount')})`,
    value: a.id,
  }))
)

const sourceList = computed(() =>
  selectedList.value.filter(a => a.id !== targetId.value)
)

const targetAlbum = computed(() =>
  selectedList.value.find(a => a.id === targetId.value)
)

const canSubmit = computed(() =>
  targetId.value && sourceList.value.length >= 1
)

// 初始化：默认选第一个
if (props.selectedIds.length >= 2) {
  targetId.value = props.selectedIds[0]
}

async function doSubmit() {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const sourceIds = sourceList.value.map(a => a.id)
    const groups = [{
      label: `${targetAlbum.value?.name || targetAlbum.value?.albumName || targetId.value} ← ${sourceIds.length}个`,
      sourceIds,
      targetId: targetId.value,
    }]
    const res = await submitMerge(groups)
    if (res?.pipelineId) {
      message.success(t('albumManager.toolMergeSuccess', { id: res.pipelineId }))
      emit('done')
    }
  } catch (e) {
    console.error('[AlbumMerge] 合并失败:', e)
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

/* 已选列表 */
.amp-album-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 160px;
  overflow-y: auto;
}

.amp-album-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  border-radius: 6px;
  background: var(--ct-bg-secondary);
  border: 1px solid var(--ct-border);
}

.amp-album-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--ct-text);
}

.amp-album-meta {
  font-size: 11px;
  color: var(--ct-text-3);
}

/* 影响预览 */
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

/* 警告 */
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
