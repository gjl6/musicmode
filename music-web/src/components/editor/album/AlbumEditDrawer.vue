<template>
  <n-drawer
    v-model:show="drawerVisible"
    :width="showEnrich ? 1200 : 480"
    placement="right"
    :mask-closable="!isDirty"
    display-directive="show"
  >
    <n-drawer-content :native-scrollbar="false">
      <template #header>
        <div class="drawer-header">
          <span class="header-title">{{ form.name || t('albumManager.editAlbumTitle') }}</span>
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

      <!-- Body: flex 布局 — 左增强面板 + 右编辑表单 -->
      <div class="drawer-body-layout">
        <!-- 左侧：在线增强面板（条件渲染） -->
        <div v-if="showEnrich" class="enrich-panel-inline">
          <AlbumEnrichCompare
            ref="enrichRef"
            :album-id="form.id"
            compact
            @fill-field="onFillField"
            @fill-all="onFillAll"
            @refresh-needed="enrichRef?.load()"
          />
        </div>

        <!-- 右侧：编辑表单（始终可见） -->
        <div class="drawer-edit-area">
          <!-- 封面 — 顶部大图 -->
          <div class="cover-area" v-memo="[form.coverUrl]">
            <div
              class="cover-box"
              :class="{ 'has-cover': !!form.coverUrl }"
              @click="triggerCoverUpload"
            >
              <img v-if="form.coverUrl" :src="getCoverUrl(form.coverUrl)" class="cover-img" decoding="async" />
              <div v-else class="cover-placeholder">
                <n-icon :size="32"><ImageOutline /></n-icon>
                <span>{{ $t('albumManager.editCover') }}</span>
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

          <n-form label-placement="top" :show-feedback="false">
            <!-- 名称 -->
            <n-form-item :label="$t('albumManager.editName')">
              <n-input v-model:value="form.name" />
            </n-form-item>

            <!-- 类型 -->
            <n-form-item :label="$t('albumManager.editType')">
              <n-select
                v-model:value="form.albumType"
                :options="typeOptions"
                :placeholder="$t('albumManager.editType')"
              />
            </n-form-item>

            <!-- 年份 -->
            <n-form-item :label="$t('albumManager.editYear')">
              <n-input-number v-model:value="form.albumYear" :min="1900" :max="2099" style="width:100%" />
            </n-form-item>

            <!-- 发行公司 -->
            <n-form-item :label="$t('albumManager.editCompany')">
              <n-input v-model:value="form.company" />
            </n-form-item>

            <!-- 语言 -->
            <n-form-item :label="$t('albumManager.editLanguage')">
              <n-input v-model:value="form.language" />
            </n-form-item>

            <!-- 简介 -->
            <n-form-item :label="$t('albumManager.editIntroduction')">
              <n-input
                v-model:value="form.introduction"
                type="textarea"
                :autosize="{ minRows: 3, maxRows: 8 }"
              />
            </n-form-item>
          </n-form>

          <div class="drawer-footer">
            <n-button @click="handleClose">{{ $t('albumManager.cancel') }}</n-button>
            <n-button type="primary" :loading="saving" @click="save">{{ $t('albumManager.editSave') }}</n-button>
          </div>
        </div>
      </div>
    </n-drawer-content>
  </n-drawer>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { CloudDownloadOutline, CloseOutline, ImageOutline, CameraOutline } from '@vicons/ionicons5'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { getCoverUrl } from '@/utils/mediaUrl.js'
import client from '@/api/client.js'
import AlbumEnrichCompare from '@/components/editor/album/AlbumEnrichCompare.vue'

const { t } = useI18n()
const message = useMessage()

const props = defineProps({
  show: { type: Boolean, default: false },
  album: { type: Object, default: null },
})

const emit = defineEmits(['update:show', 'saved'])

const drawerVisible = ref(false)
const showEnrich = ref(false)
const enrichRef = ref(null)
const isDirty = ref(false)

const typeOptions = computed(() => [
  { label: t('albumManager.typeAlbum'), value: 'ALBUM' },
  { label: t('albumManager.typeSingle'), value: 'SINGLE' },
  { label: t('albumManager.typeEP'), value: 'EP' },
  { label: t('albumManager.typeCompilation'), value: 'COMPILATION' },
  { label: t('albumManager.typeLive'), value: 'LIVE' },
  { label: t('albumManager.typeSoundtrack'), value: 'SOUNDTRACK' },
])

const form = ref({})
const saving = ref(false)
const coverInputRef = ref(null)
const uploadingCover = ref(false)

watch(() => props.show, v => { drawerVisible.value = v })
watch(drawerVisible, v => { if (!v) { emit('update:show', false); showEnrich.value = false } })

watch(() => props.album, (a) => {
  if (a) {
    form.value = {
      id: a.id,
      name: a.name || a.albumName || '',
      albumType: a.albumType || null,
      albumYear: a.albumYear || null,
      company: a.company || '',
      language: a.language || '',
      introduction: a.introduction || '',
      coverUrl: a.coverArt || null,
    }
    isDirty.value = false
    // 切换专辑时重新加载增强面板
    if (showEnrich.value) {
      enrichRef.value?.load()
    }
  }
}, { immediate: true })

function toggleEnrich() {
  showEnrich.value = !showEnrich.value
}

function handleClose() {
  drawerVisible.value = false
}

// ── 封面上传 ──
function triggerCoverUpload() {
  coverInputRef.value?.click()
}

async function onCoverFileChange(e) {
  const file = e.target?.files?.[0]
  if (!file) return
  // 重置 input 以便重复选择同一文件
  e.target.value = ''

  if (!form.value.id) return
  uploadingCover.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const res = await client.post(`/albums/${form.value.id}/cover`, fd)
    if (res?.coverUrl) {
      form.value.coverUrl = res.coverUrl
      isDirty.value = true
    }
  } catch (err) {
    console.error('[AlbumEditDrawer] 封面上传失败:', err)
    message.warning('封面上传失败: ' + (err?.response?.data?.error || err.message))
  } finally {
    uploadingCover.value = false
  }
}

// ── EnrichCompare 回填 ──
function onFillField(fieldKey, value) {
  if (value === null || value === undefined || value === '') return
  isDirty.value = true
  if (fieldKey in form.value) {
    form.value[fieldKey] = value
  }
}

function onFillAll(info, mode) {
  isDirty.value = true
  const fillOnly = mode === 'fill'

  if (info.introduction != null && info.introduction !== '') {
    if (!fillOnly || !form.value.introduction) form.value.introduction = info.introduction
  }
  if (info.albumType != null && info.albumType !== '') {
    if (!fillOnly || !form.value.albumType) form.value.albumType = info.albumType
  }
  if (info.albumYear != null) {
    if (!fillOnly || form.value.albumYear == null) form.value.albumYear = info.albumYear
  }
  if (info.company != null && info.company !== '') {
    if (!fillOnly || !form.value.company) form.value.company = info.company
  }
  if (info.language != null && info.language !== '') {
    if (!fillOnly || !form.value.language) form.value.language = info.language
  }
  if (info.coverUrl != null && info.coverUrl !== '') {
    if (!fillOnly || !form.value.coverUrl) form.value.coverUrl = info.coverUrl
  }
}

async function save() {
  saving.value = true
  try {
    const id = props.album.id
    const body = {
      name: form.value.name,
      genre: form.value.albumType || null,
      year: form.value.albumYear || null,
      company: form.value.company || null,
      language: form.value.language || null,
      introduction: form.value.introduction || null,
      albumCover: form.value.coverUrl || null,
    }
    await client.put(`/albums/${id}`, body)
    message.success(t('albumManager.editSaved'))
    isDirty.value = false
    emit('saved')
    drawerVisible.value = false
  } catch (e) {
    message.error(e.response?.data?.error || e.message)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.drawer-body-layout {
  display: flex;
  gap: 20px;
  height: 100%;
}

.enrich-panel-inline {
  width: 720px;
  flex-shrink: 0;
  overflow-y: auto;
  padding-right: 12px;
  border-right: 1px solid var(--ct-border);
}

.drawer-edit-area {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 封面 */
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
  border-radius: 12px;
  overflow: hidden;
  background: var(--ct-bg-secondary);
  border: 2px solid var(--ct-border);
  box-shadow: 0 2px 8px rgba(0 0 0 / 0.1);
  cursor: pointer;
  flex-shrink: 0;
}

.cover-box.has-cover {
  border: none;
  box-shadow: 0 2px 8px rgba(0 0 0 / 0.1);
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

.drawer-footer {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px solid var(--ct-border);
}
</style>
