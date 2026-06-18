<template>
  <n-drawer
    v-model:show="visible"
    :width="drawerWidth"
    placement="right"
    :auto-focus="false"
    :mask-closable="!editStore.isDirty"
    display-directive="show"
  >
    <n-drawer-content
      :native-scrollbar="true"
    >
      <template #header>
        <div class="drawer-header">
          <div class="header-file">
            <span class="file-name">{{ editStore.editingFile?.fileName || editStore.editingFile?.name || '' }}</span>
            <span v-if="format" class="format-badge">{{ format }}</span>
          </div>
          <div class="header-actions">
            <n-button
              size="tiny"
              text
              :disabled="!editStore.canUndo"
              title="Ctrl+Z"
              @click="editStore.undo()"
            >
              <template #icon><n-icon :size="18"><ArrowUndoOutline /></n-icon></template>
            </n-button>
            <n-button
              size="tiny"
              text
              :disabled="!editStore.canRedo"
              title="Ctrl+Y"
              @click="editStore.redo()"
            >
              <template #icon><n-icon :size="18"><ArrowRedoOutline /></n-icon></template>
            </n-button>
            <n-button
              size="tiny"
              text
              title="从在线数据库获取增强数据"
              @click="appStore.toggleEnrichPanel()"
            >
              <template #icon><n-icon :size="18"><CloudDownloadOutline /></n-icon></template>
            </n-button>
            <n-button
              size="tiny"
              text
              title="从文件名拆分元数据"
              @click="appStore.toggleSplitPanel()"
            >
              <template #icon><n-icon :size="18"><CodeSlashOutline /></n-icon></template>
            </n-button>
            <n-button
              size="tiny"
              text
              title="智能清理无关信息"
              @click="appStore.toggleReplacePanel()"
            >
              <template #icon><n-icon :size="18"><BrushOutline /></n-icon></template>
            </n-button>
            <n-button
              size="tiny"
              text
              @click="handleClose"
            >
              <template #icon><n-icon :size="18"><CloseOutline /></n-icon></template>
            </n-button>
          </div>
        </div>
      </template>

      <template v-if="!editStore.editingFile" #default>
        <div class="empty-hint">{{ t('workbench.selectFile') }}</div>
      </template>

      <template v-else #default>
        <div class="drawer-body-layout">
          <EnrichPanel :visible="appStore.enrichPanelVisible" class="enrich-panel-inline" />
          <SplitPanel v-if="appStore.splitPanelVisible" />
          <ReplacePanel v-if="appStore.replacePanelVisible" />
          <div class="drawer-edit-area">

            <div class="cover-area" v-memo="[coverUrl]">
          <div
            class="cover-box"
            :class="{ 'has-cover': !!coverUrl }"
          >
            <img v-if="coverUrl" :src="coverUrl" class="cover-img" decoding="async" />
            <div v-else class="cover-placeholder">
              <n-icon :size="32"><MusicalNotesOutline /></n-icon>
              <span>{{ t('edit.cover') }}</span>
            </div>
            <div class="cover-overlay">
              <n-icon :size="20"><CameraOutline /></n-icon>
              <span>{{ t('edit.replaceCover') }}</span>
            </div>
          </div>
        </div>


        <div class="edit-sections">
          <EditSection
            :title="t('edit.song')"
            :dirty-count="editStore.dirtyCountByGroup.song"
            :default-open="true"
          >
            <div class="field-row">
              <label>{{ t('edit.fields.title') }}</label>
              <n-input
                size="small"
                :value="editStore.currentMeta.song.title"
                placeholder="—"
                @update:value="v => editStore.setField('song.title', v)"
              />
              <span v-if="dirtyState.has('song.title')" class="dirty-dot" />
            </div>
            <div class="field-row field-row-2col">
              <div class="field-col">
                <label>{{ t('edit.fields.year') }}</label>
                <n-input
                  size="small"
                  :value="editStore.currentMeta.song.year"
                  placeholder="—"
                  @update:value="v => editStore.setField('song.year', v)"
                />
                <span v-if="dirtyState.has('song.year')" class="dirty-dot" />
              </div>
              <div class="field-col">
                <label>{{ t('edit.fields.language') }}</label>
                <n-input
                  size="small"
                  :value="editStore.currentMeta.song.language"
                  placeholder="—"
                  @update:value="v => editStore.setField('song.language', v)"
                />
                <span v-if="dirtyState.has('song.language')" class="dirty-dot" />
              </div>
            </div>
            <div class="field-row field-row-2col">
              <div class="field-col">
                <label>{{ t('edit.fields.trackNumber') }}</label>
                <n-input-number
                  size="small"
                  :value="editStore.currentMeta.song.trackNumber"
                  :min="0"
                  :show-button="false"
                  placeholder="—"
                  @update:value="v => editStore.setField('song.trackNumber', v)"
                />
                <span v-if="dirtyState.has('song.trackNumber')" class="dirty-dot" />
              </div>
              <div class="field-col">
                <label>{{ t('edit.fields.discNumber') }}</label>
                <n-input-number
                  size="small"
                  :value="editStore.currentMeta.song.discNumber"
                  :min="0"
                  :show-button="false"
                  placeholder="—"
                  @update:value="v => editStore.setField('song.discNumber', v)"
                />
                <span v-if="dirtyState.has('song.discNumber')" class="dirty-dot" />
              </div>
            </div>
            <div class="field-row field-row-2col">
              <div class="field-col">
                <label>{{ t('edit.fields.composer') }}</label>
                <n-input
                  size="small"
                  :value="editStore.currentMeta.song.composer"
                  placeholder="—"
                  @update:value="v => editStore.setField('song.composer', v)"
                />
                <span v-if="dirtyState.has('song.composer')" class="dirty-dot" />
              </div>
              <div class="field-col">
                <label>{{ t('edit.fields.lyricist') }}</label>
                <n-input
                  size="small"
                  :value="editStore.currentMeta.song.lyricist"
                  placeholder="—"
                  @update:value="v => editStore.setField('song.lyricist', v)"
                />
                <span v-if="dirtyState.has('song.lyricist')" class="dirty-dot" />
              </div>
            </div>


            <n-collapse class="tech-collapse" v-memo="[tech]">
              <n-collapse-item :title="t('edit.techInfo')" name="tech">
                <div class="tech-grid">
                  <div class="tech-item">
                    <span class="tech-label">{{ t('edit.fields.duration') }}</span>
                    <span class="tech-value">{{ tech.duration }}</span>
                  </div>
                  <div class="tech-item">
                    <span class="tech-label">{{ t('edit.fields.bitrate') }}</span>
                    <span class="tech-value">{{ tech.bitrate }}</span>
                  </div>
                  <div class="tech-item">
                    <span class="tech-label">{{ t('edit.fields.sampleRate') }}</span>
                    <span class="tech-value">{{ tech.sampleRate }}</span>
                  </div>
                  <div class="tech-item">
                    <span class="tech-label">{{ t('edit.fields.channels') }}</span>
                    <span class="tech-value">{{ tech.channels }}</span>
                  </div>
                  <div class="tech-item">
                    <span class="tech-label">{{ t('edit.fields.bitsPerSample') }}</span>
                    <span class="tech-value">{{ tech.bitsPerSample }}</span>
                  </div>
                  <div class="tech-item">
                    <span class="tech-label">{{ t('edit.fields.fileSize') }}</span>
                    <span class="tech-value">{{ tech.fileSize }}</span>
                  </div>
                  <div class="tech-item">
                    <span class="tech-label">{{ t('edit.fields.fileFormat') }}</span>
                    <span class="tech-value">{{ tech.fileFormat }}</span>
                  </div>
                  <div class="tech-item">
                    <span class="tech-label">{{ t('edit.fields.filePath') }}</span>
                    <span class="tech-value mono">{{ tech.filePath }}</span>
                  </div>
                </div>
              </n-collapse-item>
            </n-collapse>
          </EditSection>

          <EditSection
            :title="t('edit.album')"
            :dirty-count="editStore.dirtyCountByGroup.album"
            :default-open="true"
          >
            <div class="field-row">
              <label>{{ t('edit.fields.albumName') }}</label>
              <n-input
                size="small"
                :value="editStore.currentMeta.album.albumName"
                placeholder="—"
                @update:value="v => editStore.setField('album.albumName', v)"
              />
              <span v-if="dirtyState.has('album.albumName')" class="dirty-dot" />
            </div>
            <div class="field-row field-row-2col">
              <div class="field-col">
                <label>{{ t('edit.fields.albumType') }}</label>
                <n-select
                  size="small"
                  :value="editStore.currentMeta.album.albumType"
                  :options="albumTypeOptions"
                  @update:value="v => editStore.setField('album.albumType', v)"
                />
              </div>
              <div class="field-col">
                <label>{{ t('edit.fields.albumYear') }}</label>
                <n-input-number
                  size="small"
                  :value="editStore.currentMeta.album.albumYear"
                  :min="0"
                  :show-button="false"
                  placeholder="—"
                  @update:value="v => editStore.setField('album.albumYear', v)"
                />
                <span v-if="dirtyState.has('album.albumYear')" class="dirty-dot" />
              </div>
            </div>
            <div class="field-row">
              <label>{{ t('edit.fields.company') }}</label>
              <n-input
                size="small"
                :value="editStore.currentMeta.album.company"
                placeholder="—"
                @update:value="v => editStore.setField('album.company', v)"
              />
              <span v-if="dirtyState.has('album.company')" class="dirty-dot" />
            </div>
            <div class="field-row">
              <label>{{ t('edit.fields.introduction') }}</label>
              <n-input
                size="small"
                type="textarea"
                :autosize="{ minRows: 2, maxRows: 4 }"
                :value="editStore.currentMeta.album.introduction"
                placeholder="—"
                @update:value="v => editStore.setField('album.introduction', v)"
              />
            </div>
          </EditSection>

          <EditSection
            :title="t('edit.artist')"
            :dirty-count="editStore.dirtyCountByGroup.artist"
          >

            <div class="artist-tabs">
              <span
                v-for="(a, i) in editStore.currentMeta.artists"
                :key="i"
                class="artist-tab"
                :class="{ active: i === editStore.currentMeta.activeArtistIndex }"
                @click="editStore.switchArtist(i)"
              >
                <span class="artist-tab-name">{{ a.artistName || `Artist ${i + 1}` }}</span>
                <span
                  v-if="editStore.currentMeta.artists.length > 1"
                  class="artist-tab-close"
                  @click.stop="editStore.removeArtist(i)"
                >×</span>
              </span>
              <span class="artist-tab artist-tab-add" @click="editStore.addArtist()">+</span>
            </div>

            <div class="field-row">
              <label>{{ t('edit.fields.artistName') }}</label>
              <n-input
                size="small"
                :value="editStore.currentMeta.artist.artistName"
                placeholder="—"
                @update:value="v => editStore.setField('artist.artistName', v)"
              />
              <span v-if="dirtyState.has('artist.artistName')" class="dirty-dot" />
            </div>
            <div class="field-row field-row-2col">
              <div class="field-col">
                <label>{{ t('edit.fields.gender') }}</label>
                <n-select
                  size="small"
                  :value="editStore.currentMeta.artist.gender"
                  :options="genderOptions"
                  @update:value="v => editStore.setField('artist.gender', v)"
                />
              </div>
              <div class="field-col">
                <label>{{ t('edit.fields.country') }}</label>
                <n-input
                  size="small"
                  :value="editStore.currentMeta.artist.country"
                  placeholder="—"
                  @update:value="v => editStore.setField('artist.country', v)"
                />
                <span v-if="dirtyState.has('artist.country')" class="dirty-dot" />
              </div>
            </div>
            <div class="field-row">
              <label>{{ t('edit.fields.introduction') }}</label>
              <n-input
                size="small"
                type="textarea"
                :autosize="{ minRows: 2, maxRows: 4 }"
                :value="editStore.currentMeta.artist.introduction"
                placeholder="—"
                @update:value="v => editStore.setField('artist.introduction', v)"
              />
            </div>
          </EditSection>

          <EditSection
            :title="t('edit.lyric')"
            :dirty-count="editStore.dirtyCountByGroup.lyric"
          >
            <div class="field-row">
              <label>{{ t('edit.fields.lyricType') }}</label>
              <n-select
                size="small"
                :value="editStore.currentMeta.lyric.type"
                :options="lyricTypeOptions"
                @update:value="v => editStore.setField('lyric.type', v)"
              />
            </div>
            <div class="field-row">
              <label>{{ t('edit.fields.lrcPath') }}</label>
              <n-input
                size="small"
                :value="editStore.currentMeta.lyric.lrcPath"
                disabled
                placeholder="—"
              />
            </div>
            <div class="field-row">
              <label>{{ t('edit.fields.lyricContent') }}</label>
              <n-input
                size="small"
                type="textarea"
                :autosize="{ minRows: 4, maxRows: 10 }"
                :value="editStore.currentMeta.lyric.content"
                placeholder="—"
                @update:value="v => editStore.setField('lyric.content', v)"
              />
              <span v-if="dirtyState.has('lyric.content')" class="dirty-dot" />
            </div>
          </EditSection>

          <EditSection
            :title="t('edit.style')"
            :dirty-count="editStore.dirtyCountByGroup.style"
          >
            <div class="field-row">
              <label>{{ t('edit.fields.styleName') }}</label>
              <n-input
                size="small"
                :value="editStore.currentMeta.style.styleName"
                placeholder="—"
                @update:value="v => editStore.setField('style.styleName', v)"
              />
              <span v-if="dirtyState.has('style.styleName')" class="dirty-dot" />
            </div>
            <div class="field-row">
              <label>{{ t('edit.fields.description') }}</label>
              <n-input
                size="small"
                :value="editStore.currentMeta.style.description"
                placeholder="—"
                @update:value="v => editStore.setField('style.description', v)"
              />
            </div>
          </EditSection>
        </div>
          </div>
        </div>
      </template>

      <template v-if="editStore.editingFile" #footer>
        <div class="drawer-footer">
          <span class="draft-hint">
            <template v-if="editStore.draftTime">
              <n-icon :size="12"><TimeOutline /></n-icon>
              {{ t('edit.draftSaved') }}
            </template>
            <template v-else-if="editStore.isDirty">
              {{ t('edit.unsaved') }}
            </template>
            <template v-else>
              {{ t('edit.noChanges') }}
            </template>
          </span>
          <div class="footer-actions">
            <n-button
              size="small"
              text
              :disabled="!editStore.isDirty"
              @click="editStore.resetAll()"
            >
              {{ t('edit.reset') }}
            </n-button>
            <n-button
              v-if="hasEdit"
              size="small"
              type="primary"
              :disabled="!editStore.isDirty"
              :loading="saving"
              @click="handleSave"
            >
              {{ t('edit.save') }}
              <template v-if="editStore.isDirty" #icon>
                <span class="dirty-count-badge">{{ editStore.dirtyFields.size }}</span>
              </template>
            </n-button>
          </div>
        </div>
      </template>
    </n-drawer-content>
  </n-drawer>

</template>

<script setup>
import { computed, ref, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { NIcon, useDialog } from 'naive-ui'
import {
  ArrowUndoOutline, ArrowRedoOutline,
  MusicalNotesOutline, CameraOutline, TimeOutline,
  CloseOutline, CloudDownloadOutline,
  CodeSlashOutline, BrushOutline,
} from '@vicons/ionicons5'
import { useEditStore } from '@/store/editor/edit.js'
import { useAppStore } from '@/store/editor/app.js'
import { useAuthStore } from '@/store/auth.js'
import { saveMetadata } from '@/api/editor/music.js'
import { toMusicMetadata } from '@/utils/musicMeta.js'
import { getCoverUrl } from '@/utils/mediaUrl.js'
import EditSection from '@/components/editor/metadata/EditSection.vue'
import EnrichPanel from '@/components/editor/metadata/EnrichPanel.vue'
import SplitPanel from '@/components/editor/metadata/SplitPanel.vue'
import ReplacePanel from '@/components/editor/metadata/ReplacePanel.vue'

const { t } = useI18n()
const dialog = useDialog()
const editStore = useEditStore()
const appStore = useAppStore()
const auth = useAuthStore()

const hasEdit = computed(() => auth.hasPermission('music:edit'))
const saving = ref(false)

const drawerWidth = computed(() => {
  if (appStore.enrichPanelVisible || appStore.splitPanelVisible) return 1100
  if (appStore.replacePanelVisible) return 960
  return 500
})


const visible = computed({
  get: () => appStore.drawerVisible,
  set: (v) => { if (!v) handleClose() },
})

const dirtyState = computed(() => editStore.dirtyFields)

watch(() => appStore.drawerVisible, (v) => {
  nextTick(() => {
    const mask = document.querySelector('.n-drawer-mask')
    if (mask) mask.style.pointerEvents = v ? '' : 'none'
  })
}, { immediate: true })


const coverUrl = computed(() => {
  const p = editStore.currentMeta.song?.coverPath
  return getCoverUrl(p)
})


const format = computed(() => {
  const f = editStore.currentMeta.song?._readonly?.fileFormat
  return f || ''
})


const drawerTitle = computed(() => {
  if (!editStore.editingFile) return t('edit.title')
  return editStore.editingFile.fileName || editStore.editingFile.name || ''
})


const tech = computed(() => {
  const r = editStore.currentMeta.song?._readonly || {}
  const dur = r.duration
  let duration = '—'
  if (dur && !isNaN(dur) && dur > 0) {
    const m = Math.floor(dur / 60)
    const s = Math.floor(dur % 60)
    duration = `${m}:${String(s).padStart(2, '0')}`
  }
  return {
    duration,
    bitrate: r.bitrate ? `${r.bitrate} kbps` : '—',
    sampleRate: r.sampleRate ? `${(r.sampleRate / 1000).toFixed(1)} kHz` : '—',
    channels: r.channels === 1 ? 'Mono' : r.channels === 2 ? 'Stereo' : r.channels ? `${r.channels}ch` : '—',
    bitsPerSample: r.bitsPerSample ? `${r.bitsPerSample} bit` : '—',
    fileSize: formatFileSize(r.fileSize),
    fileFormat: r.fileFormat || '—',
    filePath: r.filePath || '—',
  }
})

function formatFileSize(bytes) {
  if (!bytes || bytes === 0) return '—'
  let size = Number(bytes)
  if (size < 1024) return `${size} B`
  size /= 1024
  if (size < 1024) return `${size.toFixed(1)} KB`
  size /= 1024
  if (size < 1024) return `${size.toFixed(1)} MB`
  size /= 1024
  return `${size.toFixed(1)} GB`
}


const albumTypeOptions = [
  { label: 'ALBUM', value: 'ALBUM' },
  { label: 'EP', value: 'EP' },
  { label: 'SINGLE', value: 'SINGLE' },
  { label: 'COMPILATION', value: 'COMPILATION' },
  { label: 'SOUNDTRACK', value: 'SOUNDTRACK' },
]

const genderOptions = [
  { label: '未知', value: 0 },
  { label: '男', value: 1 },
  { label: '女', value: 2 },
]

const lyricTypeOptions = [
  { label: 'NONE', value: 'NONE' },
  { label: 'METADATA', value: 'METADATA' },
  { label: 'LRC', value: 'LRC' },
]


async function handleSave() {
  if (!editStore.isDirty || !editStore.editingFile) return
  saving.value = true
  try {
    const artists = editStore.currentMeta.artists
    if (artists && artists.length > 0) {
      artists[editStore.currentMeta.activeArtistIndex] = { ...editStore.currentMeta.artist }
    }
    const meta = toMusicMetadata(editStore.currentMeta)
    await saveMetadata(editStore.editingFile.path, meta)
    editStore.clearDraft()
    editStore.resetEdit()
    appStore.closeDrawer()
    window.$message?.success(t('common.success'))
  } catch (e) {
    window.$message?.error(t('common.error') + ': ' + (e?.response?.data?.error || e.message || ''))
  } finally {
    saving.value = false
  }
}

function handleClose() {
  if (editStore.isDirty) {
    dialog.warning({
      title: t('edit.unsaved'),
      content: t('edit.confirmClose'),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: () => {
        editStore.resetEdit()
        appStore.closeDrawer()
      },
    })
  } else {
    editStore.resetEdit()
    appStore.closeDrawer()
  }
}

</script>

<style scoped>


:deep(.n-drawer-content-wrapper) {

}

:deep(.n-drawer-content) {
  --n-color: var(--ct-bg);
  --n-text-color: var(--ct-text);
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

.header-file {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.file-name {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.format-badge {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  padding: 1px 5px;
  border-radius: 3px;
  background: var(--sb-bg-hover);
  color: var(--ct-text-2);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}


.empty-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  font-size: 13px;
  color: var(--ct-text-2);
}


.cover-area {
  display: flex;
  justify-content: center;
  padding: 12px 0 16px;
}

.cover-box {
  position: relative;
  width: 160px;
  height: 160px;
  border-radius: var(--radius-xl);
  overflow: hidden;
  background: var(--gradient-card, var(--ct-bg-secondary));
  border: var(--border-width-strong) solid var(--ct-border);
  box-shadow: var(--shadow-md);
}

.cover-box.has-cover {
  border: none;
  box-shadow: var(--shadow-md);
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
  cursor: pointer;
}

.cover-box:hover .cover-overlay {
  opacity: 1;
}


.edit-sections {
  display: flex;
  flex-direction: column;
}


:deep(.section-header) {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 0 8px;
  cursor: pointer;
  user-select: none;
  transition: color var(--transition-base);
  border-radius: var(--radius-md);
}

:deep(.section-header:hover) {
  color: var(--n-color-primary);
  box-shadow: var(--shadow-sm);
}

:deep(.section-chevron) {
  flex-shrink: 0;
  transition: transform 0.2s;
  opacity: 0.5;
}

:deep(.section-chevron.rotated) {
  transform: rotate(90deg);
  opacity: 0.8;
}

:deep(.section-title) {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.03em;
  text-transform: uppercase;
}

:deep(.section-badge) {
  font-size: 10px;
  font-weight: 700;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  line-height: 16px;
  text-align: center;
  border-radius: 8px;
  background: var(--n-color-primary);
  color: #fff;
  margin-left: auto;
  box-shadow: var(--shadow-sm);
}

:deep(.section-body) {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-bottom: 8px;
}


.artist-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.artist-tab {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: var(--radius-md);
  font-size: 11px;
  cursor: pointer;
  background: var(--gradient-button, var(--ct-bg-secondary));
  border: var(--border-width-strong) solid var(--ct-border);
  color: var(--ct-text-2);
  transition: all var(--transition-base);
  user-select: none;
  box-shadow: var(--effect-card-inner), var(--shadow-sm);
}

.artist-tab:hover {
  background: var(--sb-bg-hover);
  color: var(--ct-text);
}

.artist-tab.active {
  background: var(--gradient-card, rgb(var(--ct-accent-rgb) / 0.12));
  border-color: rgb(var(--ct-accent-rgb) / 0.5);
  color: rgb(var(--ct-accent-rgb));
  font-weight: 600;
  box-shadow: var(--effect-selection-glow);
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
  opacity: 0.4;
  padding: 0 1px;
}

.artist-tab-close:hover {
  opacity: 1;
  color: var(--n-color-error);
}

.artist-tab-add {
  padding: 3px 8px;
  font-size: 14px;
  font-weight: 600;
  border-style: dashed;
}

.artist-tab-add:hover {
  border-color: rgb(var(--ct-accent-rgb) / 0.5);
  color: rgb(var(--ct-accent-rgb));
}


.field-row {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.field-row label {
  font-size: 11px;
  font-weight: 500;
  color: var(--ct-text-2);
  letter-spacing: 0.02em;
  padding-left: 1px;
}

.field-row-2col {
  flex-direction: row;
  gap: 10px;
}

.field-col {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}


.dirty-dot {
  position: absolute;
  top: 18px;
  right: -6px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--n-color-primary);
  pointer-events: none;
}


.tech-collapse {
  margin-top: 8px;
}

.tech-collapse :deep(.n-collapse-item__header) {
  font-size: 11px !important;
  font-weight: 500;
  color: var(--ct-text-2);
  padding: 6px 0 !important;
}

.tech-collapse :deep(.n-collapse-item__content-inner) {
  padding: 0 0 8px 0 !important;
}

.tech-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 12px;
}

.tech-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  padding: 2px 0;
}

.tech-label {
  color: var(--ct-text-2);
  letter-spacing: 0.02em;
}

.tech-value {
  color: var(--ct-text);
  font-variant-numeric: tabular-nums;
}

.tech-value.mono {
  font-family: 'SF Mono', 'Cascadia Code', monospace;
  font-size: 10px;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  font-size: 11px;
  color: var(--ct-text-2);
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

<style>


.drawer-body-layout {
  display: flex;
  height: 100%;
  gap: 24px;
  contain: layout style paint;
}

.enrich-panel-inline {
  flex-shrink: 0;
  overflow: hidden;
}

.drawer-edit-area {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  contain: layout style;
}


.edit-sections .n-input {
  --n-color: var(--ct-bg-secondary);
  --n-color-focus: var(--ct-bg-secondary);
  --n-text-color: var(--ct-text);
  --n-border: 1px solid var(--ct-border);
  --n-border-focus: 1px solid var(--n-color-primary);
  --n-border-radius: 6px;
  --n-font-size: 12.5px;
  --n-padding: 6px 10px;
}

.edit-sections .n-input-number {
  --n-color: var(--ct-bg-secondary);
  --n-text-color: var(--ct-text);
  --n-border: 1px solid var(--ct-border);
  --n-border-focus: 1px solid var(--n-color-primary);
  --n-border-radius: 6px;
}

.edit-sections .n-base-selection {
  --n-color: var(--ct-bg-secondary);
  --n-text-color: var(--ct-text);
  --n-border: 1px solid var(--ct-border);
  --n-border-focus: 1px solid var(--n-color-primary);
  --n-border-radius: 6px;
  --n-font-size: 12.5px;
}


.edit-sections .n-input.n-input--textarea {
  --n-padding: 6px 10px;
}

.edit-sections .n-input.n-input--textarea textarea {
  font-size: 12px !important;
  line-height: 1.6;
  font-family: 'SF Mono', 'Cascadia Code', 'Fira Code', monospace;
}

</style>
