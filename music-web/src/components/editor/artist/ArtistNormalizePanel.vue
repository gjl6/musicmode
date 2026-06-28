<template>
  <div class="anp-root">
    <!-- 处理范围 -->
    <div class="anp-section">
      <div class="anp-section-title">{{ $t('artistManager.toolNormalizeScope') }}</div>
      <n-radio-group v-model:value="scope" size="small">
        <n-radio-button value="all">
          {{ $t('artistManager.toolNormalizeAllResults', { n: total }) }}
        </n-radio-button>
        <n-radio-button value="selected" :disabled="!selectedIds.length">
          {{ $t('artistManager.toolNormalizeSelected', { n: selectedIds.length }) }}
        </n-radio-button>
      </n-radio-group>
    </div>

    <!-- 说明 -->
    <div class="anp-hint">
      <n-icon :size="16"><InformationCircleOutline /></n-icon>
      <span>规范化艺术家名称格式：移除多余空格、统一大小写、清理特殊字符（如 feat. / & 等分隔符标准化）。将在后台通过管道处理，不跳转页面。</span>
    </div>

    <!-- 提交 -->
    <div class="anp-footer">
      <n-button type="primary" size="small" :loading="submitting" @click="doSubmit">
        {{ $t('artistManager.submitPipeline') }}
      </n-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { InformationCircleOutline } from '@vicons/ionicons5'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { submitArtistPipeline } from '@/api/editor/artist-manage.js'

const props = defineProps({
  total: { type: Number, default: 0 },
  selectedIds: { type: Array, default: () => [] },
  selection: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['done'])

const { t } = useI18n()
const message = useMessage()

const scope = ref('all')
const submitting = ref(false)

async function doSubmit() {
  submitting.value = true
  try {
    const sel = { ...props.selection }
    if (scope.value === 'selected') {
      sel.mode = 'ids'
      sel.ids = [...props.selectedIds]
    }
    const res = await submitArtistPipeline('artist-normalize', sel)
    if (res?.pipelineId) {
      message.success(t('artistManager.pipelineSubmitted', { id: res.pipelineId }))
      emit('done')
    }
  } catch (e) {
    console.error('[ArtistNormalize] 提交失败:', e)
    message.warning('提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.anp-root {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 4px 0;
}

.anp-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.anp-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ct-text-2);
}

.anp-hint {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgb(var(--ct-accent-rgb) / 0.06);
  border: 1px solid rgb(var(--ct-accent-rgb) / 0.15);
  font-size: 12px;
  color: var(--ct-text-2);
  line-height: 1.6;
}

.anp-hint :deep(.n-icon) {
  color: rgb(var(--ct-accent-rgb));
  flex-shrink: 0;
  margin-top: 1px;
}

.anp-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 8px;
  border-top: 1px solid var(--ct-border);
}
</style>
