<template>
  <div class="aep-root">

    <div class="aep-section">
      <div class="aep-section-title">{{ $t('artistManager.toolEnrichScope') }}</div>
      <n-radio-group v-model:value="scope" size="small">
        <n-radio-button value="all">
          {{ $t('artistManager.toolEnrichAllResults', { n: total }) }}
        </n-radio-button>
        <n-radio-button value="selected" :disabled="!selectedIds.length">
          {{ $t('artistManager.toolEnrichSelected', { n: selectedIds.length }) }}
        </n-radio-button>
      </n-radio-group>
    </div>


    <div class="aep-section">
      <div class="aep-section-title">{{ $t('artistManager.toolEnrichProviders') }}</div>
      <n-select
        v-model:value="providers"
        :options="providerOptions"
        multiple
        :placeholder="$t('artistManager.toolEnrichProviders')"
        style="max-width:420px"
      />
    </div>


    <div class="aep-section">
      <div class="aep-section-title">写入模式</div>
      <n-radio-group v-model:value="writeMode" size="small">
        <n-radio-button value="fill">只填空</n-radio-button>
        <n-radio-button value="overwrite">全覆盖</n-radio-button>
      </n-radio-group>
      <span class="aep-hint">
        {{ writeMode === 'fill' ? '只填充空白字段，不覆盖已有数据' : '以数据源为准覆盖所有字段' }}
      </span>
    </div>


    <div class="aep-footer">
      <n-button type="primary" size="small" :loading="submitting" @click="doSubmit">
        {{ $t('artistManager.submitPipeline') }}
      </n-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { submitArtistPipeline, getArtistProviders } from '@/api/editor/artist-manage.js'

const props = defineProps({
  total: { type: Number, default: 0 },
  selectedIds: { type: Array, default: () => [] },
  selection: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['done'])

const { t } = useI18n()
const message = useMessage()

const scope = ref('all')
const providers = ref(['netease', 'qqmusic'])
const providerOptions = ref([])
const writeMode = ref('fill')
const submitting = ref(false)

onMounted(async () => {
  try {
    const res = await getArtistProviders()
    const list = res?.providers || []
    providerOptions.value = list.map(p => ({ label: p.label, value: p.name }))
        if (list.length && providers.value.length === 0) {
      providers.value = list.map(p => p.name)
    }
  } catch (e) {
    console.warn('[ArtistEnrich] 无法获取 provider 列表，使用默认', e)
    providerOptions.value = [
      { label: 'QQ音乐', value: 'qqmusic' },
      { label: '网易云音乐', value: 'netease' },
      { label: 'iTunes', value: 'itunes' },
      { label: 'MusicBrainz', value: 'musicbrainz' },
      { label: '百度百科', value: 'baidubaike' },
    ]
  }
})

async function doSubmit() {
  submitting.value = true
  try {
    const sel = { ...props.selection }
    if (scope.value === 'selected') {
      sel.mode = 'ids'
      sel.ids = [...props.selectedIds]
    }
    sel.providers = [...providers.value]
    sel.writeMode = writeMode.value
    const res = await submitArtistPipeline('artist-enrich', sel)
    if (res?.pipelineId) {
      message.success(t('artistManager.pipelineSubmitted', { id: res.pipelineId }))
      emit('done')
    }
  } catch (e) {
    console.error('[ArtistEnrich] 提交失败:', e)
    message.warning('提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.aep-root {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 4px 0;
}

.aep-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.aep-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ct-text-2);
}

.aep-hint {
  font-size: 12px;
  color: var(--ct-text-3);
  margin-top: -4px;
}

.aep-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 8px;
  border-top: 1px solid var(--ct-border);
}
</style>
