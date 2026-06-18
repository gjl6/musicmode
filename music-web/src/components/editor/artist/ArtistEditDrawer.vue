<template>
  <n-drawer
    v-model:show="drawerVisible"
    :width="drawerWidth"
    placement="right"
    :mask-closable="!isDirty"
    display-directive="show"
  >
    <n-drawer-content :native-scrollbar="false">
      <template #header>
        <div class="drawer-header">
          <span class="header-title">{{ form.name || '编辑艺术家' }}</span>
          <div class="header-actions">
            <n-button
              size="tiny"
              text
              :type="showEnrich ? 'primary' : 'default'"
              title="在线增强 — 从标签源获取数据"
              @click="toggleEnrich"
            >
              <template #icon>
                <n-icon :size="18"><CloudDownloadOutline /></n-icon>
              </template>
            </n-button>
            <n-button size="tiny" text @click="handleClose">
              <template #icon>
                <n-icon :size="18"><CloseOutline /></n-icon>
              </template>
            </n-button>
          </div>
        </div>
      </template>


      <div class="drawer-body-layout">

        <div v-if="showEnrich" class="enrich-panel-inline">
          <ArtistEnrichCompare
            ref="enrichRef"
            :artist-id="form.id"
            compact
            @fill-field="onFillField"
            @fill-all="onFillAll"
            @refresh-needed="enrichRef?.load()"
          />
        </div>


        <div class="drawer-edit-area">

          <div class="cover-area" v-memo="[form.coverUrl]">
            <div
              class="cover-box"
              :class="{ 'has-cover': !!form.coverUrl }"
              @click="triggerCoverUpload"
            >
              <img v-if="form.coverUrl" :src="getCoverUrl(form.coverUrl)" class="cover-img" decoding="async" />
              <div v-else class="cover-placeholder">
                <n-icon :size="32"><ImageOutline /></n-icon>
                <span>艺术家封面</span>
              </div>
              <div class="cover-overlay">
                <n-icon :size="20"><CameraOutline /></n-icon>
                <span>{{ form.coverUrl ? '更换封面' : '添加封面' }}</span>
              </div>
            </div>
            <input
              ref="coverInputRef"
              type="file"
              accept="image/*"
              style="display:none"
              @change="onCoverFileChange"
            />
          </div>


          <div v-if="originalEnrichSource" class="enrich-source-tag">
            <n-tag size="tiny" :bordered="false" type="info">
              数据来源: {{ originalEnrichSource }}
            </n-tag>
          </div>

          <n-form
            ref="formRef"
            :model="form"
            label-placement="top"
            size="small"
          >
            <n-form-item label="艺术家名称">
              <n-input v-model:value="form.name" @update:value="markDirty" />
            </n-form-item>

            <n-form-item label="性别">
              <n-select
                v-model:value="form.gender"
                :options="genderOptions"
                placeholder="未知"
                clearable
                @update:value="markDirty"
              />
            </n-form-item>

            <n-form-item label="国家/地区">
              <n-input
                v-model:value="form.country"
                placeholder="如: 英国"
                @update:value="markDirty"
              />
            </n-form-item>

            <n-form-item label="简介">
              <n-input
                v-model:value="form.introduction"
                type="textarea"
                :autosize="{ minRows: 3, maxRows: 6 }"
                placeholder="艺术家简介..."
                @update:value="markDirty"
              />
            </n-form-item>
          </n-form>
        </div>
      </div>

      <template #footer>
        <div class="drawer-footer">
          <span class="draft-hint">
            <template v-if="isDirty">
              <span class="dirty-mark">●</span> 有未保存的修改
            </template>
            <template v-else>无修改</template>
          </span>
          <div class="footer-actions">
            <n-button size="small" @click="handleClose">取消</n-button>
            <n-button
              type="primary"
              size="small"
              :loading="saving"
              :disabled="!isDirty"
              @click="doSave"
            >
              保存
              <template v-if="isDirty" #icon>
                <span class="dirty-count-badge">{{ dirtyCount }}</span>
              </template>
            </n-button>
          </div>
        </div>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { useMessage, useDialog } from 'naive-ui'
import {
  CloseOutline, CloudDownloadOutline, ImageOutline, CameraOutline,
} from '@vicons/ionicons5'
import { updateArtist, uploadArtistCover } from '@/api/playback/artist.js'
import { getCoverUrl } from '@/utils/mediaUrl.js'
import ArtistEnrichCompare from './ArtistEnrichCompare.vue'

const GENDER_LABELS = { 0: '其他', 1: '男', 2: '女', 3: '组合' }

const props = defineProps({
  artist: { type: Object, required: true },
})

const emit = defineEmits(['saved', 'close'])

const message = useMessage()
const dialog = useDialog()

const drawerVisible = ref(false)
const showEnrich = ref(false)
const enrichRef = ref(null)

const drawerWidth = computed(() => (showEnrich.value ? 1150 : 480))

defineExpose({ open, close })

function open(enrichTab) {
  resetForm()
  if (enrichTab) showEnrich.value = true
  drawerVisible.value = true
}

function close() {
  if (isDirty.value) {
    dialog.warning({
      title: '未保存的修改',
      content: '有未保存的修改，确定要关闭吗？',
      positiveText: '确定',
      negativeText: '取消',
      onPositiveClick: () => {
        drawerVisible.value = false
        showEnrich.value = false
        emit('close')
      },
    })
  } else {
    drawerVisible.value = false
    showEnrich.value = false
    emit('close')
  }
}

function handleClose() {
  close()
}

function toggleEnrich() {
  showEnrich.value = !showEnrich.value
}

const formRef = ref(null)
const form = ref({
  id: null, name: '', gender: null, country: '', introduction: '', coverUrl: '',
})
const initialForm = ref({})
const originalEnrichSource = ref('')

function resetForm() {
  const a = props.artist
  const data = {
    id: a.id,
    name: a.name || '',
    gender: a.gender ?? null,
    country: a.country || '',
    introduction: a.introduction || '',
    coverUrl: a.coverArt || '',
  }
  form.value = { ...data }
  initialForm.value = { ...data }
  originalEnrichSource.value = a.enrichSource || ''
  showEnrich.value = false
}

watch(() => props.artist?.id, (newId, oldId) => {
  if (newId && newId !== oldId) resetForm()
})

const genderOptions = [
  { label: '未知', value: null },
  { label: '组合', value: 3 },
  { label: '男', value: 1 },
  { label: '女', value: 2 },
  { label: '其他', value: 0 },
]

const isDirty = computed(() => {
  const f = form.value
  const init = initialForm.value
  return (
    f.name !== init.name ||
    f.gender !== init.gender ||
    f.country !== init.country ||
    f.introduction !== init.introduction ||
    f.coverUrl !== init.coverUrl
  )
})

const dirtyCount = computed(() => {
  const f = form.value
  const init = initialForm.value
  let c = 0
  if (f.name !== init.name) c++
  if (f.gender !== init.gender) c++
  if (f.country !== init.country) c++
  if (f.introduction !== init.introduction) c++
  if (f.coverUrl !== init.coverUrl) c++
  return c
})

function markDirty() {  }

const coverInputRef = ref(null)
const uploadingCover = ref(false)

function triggerCoverUpload() {
  coverInputRef.value?.click()
}

async function onCoverFileChange(e) {
  const file = e.target?.files?.[0]
  if (!file) return
    e.target.value = ''

  if (!form.value.id) return
  uploadingCover.value = true
  try {
    const res = await uploadArtistCover(form.value.id, file)
    if (res?.coverUrl) {
      form.value.coverUrl = res.coverUrl
    }
  } catch (err) {
    console.error('[ArtistEditDrawer] 封面上传失败:', err)
    message.warning('封面上传失败: ' + (err?.response?.data?.error || err.message))
  } finally {
    uploadingCover.value = false
  }
}


function onFillField(fieldKey, value) {
  if (value == null || value === '') return
  if (fieldKey === 'gender') {
    form.value.gender = value
  } else if (fieldKey in form.value) {
    form.value[fieldKey] = value
  }
}

function onFillAll(fields, mode) {
  const fillOnly = (mode === 'fill')

  if (fields.introduction != null && fields.introduction !== '') {
    if (!fillOnly || !form.value.introduction) {
      form.value.introduction = fields.introduction
    }
  }
  if (fields.gender != null) {
    if (!fillOnly || form.value.gender == null) {
      form.value.gender = fields.gender
    }
  }
  if (fields.country != null && fields.country !== '') {
    if (!fillOnly || !form.value.country) {
      form.value.country = fields.country
    }
  }
  if (fields.coverUrl != null && fields.coverUrl !== '') {
    if (!fillOnly || !form.value.coverUrl) {
      form.value.coverUrl = fields.coverUrl
    }
  }
}

const saving = ref(false)

async function doSave() {
  if (!form.value.id) return
  saving.value = true
  try {
    const body = {
      artistName: form.value.name || undefined,
      gender: form.value.gender,
      country: form.value.country || undefined,
      introduction: form.value.introduction || undefined,
    }
    if (form.value.coverUrl && form.value.coverUrl !== (props.artist?.coverArt || '')) {
      body.artistCover = form.value.coverUrl
    }
    await updateArtist(form.value.id, body)
    message.success('保存成功')
        initialForm.value = { ...form.value }
    emit('saved')
  } catch (e) {
    console.error('[ArtistEditDrawer] 保存失败:', e)
    message.warning('保存失败: ' + (e?.response?.data?.error || e.message))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>

:deep(.n-drawer-content) {
  --n-header-padding: 14px 20px;
  --n-body-padding: 0 20px 16px;
  --n-footer-padding: 10px 20px;
}

:deep(.n-drawer-header) {
  border-bottom: 1px solid var(--ct-border);
}

:deep(.n-drawer-footer) {
  border-top: 1px solid var(--ct-border);
  background: var(--ct-bg-secondary);
}

:deep(.n-drawer-body) {
  overflow: hidden;
}


.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.header-title {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}


.drawer-body-layout {
  display: flex;
  height: 100%;
  gap: 20px;
}

.enrich-panel-inline {
  flex-shrink: 0;
  width: 580px;
  overflow-y: auto;
  padding-right: 4px;
  border-right: 1px solid var(--ct-border);
}

.drawer-edit-area {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding-bottom: 80px;
}


.cover-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 0 16px;
  gap: 10px;
}

.cover-box {
  position: relative;
  width: 160px;
  height: 160px;
  border-radius: var(--radius-xl, 12px);
  overflow: hidden;
  background: var(--gradient-card, var(--ct-bg-secondary));
  border: 2px solid var(--ct-border);
  box-shadow: var(--shadow-md, 0 2px 8px rgba(0 0 0 / 0.1));
  cursor: pointer;
  flex-shrink: 0;
}

.cover-box.has-cover {
  border: none;
  box-shadow: var(--shadow-md, 0 2px 8px rgba(0 0 0 / 0.1));
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--ct-text-2);
  font-size: 11px;
  letter-spacing: 0.03em;
}

.cover-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: rgba(0 0 0 / 0.55);
  color: #fff;
  font-size: 11px;
  letter-spacing: 0.03em;
  opacity: 0;
  transition: opacity 0.2s;
}

.cover-box:hover .cover-overlay {
  opacity: 1;
}


.enrich-source-tag {
  margin-top: 4px;
}


.drawer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.draft-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--ct-text-2);
}

.dirty-mark {
  color: var(--n-color-warning);
  font-size: 10px;
}

.footer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dirty-count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
  border-radius: 8px;
  background: rgba(255 255 255 / 0.2);
}
</style>
