<template>
  <n-modal
    :show="show"
    :title="title"
    preset="card"
    size="small"
    style="max-width: 420px"
    @update:show="(v) => emit('update:show', v)"
  >
    <!-- 搜索/过滤 -->
    <div class="modal-search" v-if="playlists.length > 5">
      <n-input
        v-model:value="search"
        placeholder="搜索歌单..."
        size="small"
        clearable
      >
        <template #prefix><n-icon :size="16"><SearchOutline /></n-icon></template>
      </n-input>
    </div>

    <!-- 歌单列表 -->
    <div class="modal-list">
      <n-checkbox-group v-model:value="selected" v-if="filtered.length">
        <div
          v-for="pl in filtered"
          :key="pl.id"
          class="playlist-item"
          :class="{ checked: selected.includes(pl.id) }"
        >
          <n-checkbox :value="pl.id">
            <span class="pl-name">{{ pl.name }}</span>
            <span class="pl-count">{{ pl.songCount || 0 }} 首</span>
          </n-checkbox>
        </div>
      </n-checkbox-group>
      <n-empty
        v-else-if="!loading"
        description="暂无歌单"
        size="small"
        style="margin: 12px 0"
      />
    </div>

    <!-- 新建歌单 -->
    <div class="modal-create">
      <n-input-group>
        <n-input
          v-model:value="newName"
          placeholder="输入名称后回车创建..."
          size="small"
          @keyup.enter="doCreate"
        />
        <n-button type="primary" size="small" :disabled="!newName.trim()" @click="doCreate">
          <template #icon><n-icon :size="16"><AddOutline /></n-icon></template>
        </n-button>
      </n-input-group>
    </div>

    <!-- 操作栏 -->
    <template #footer>
      <div class="modal-footer">
        <span class="footer-hint" v-if="selected.length">
          已选 {{ selected.length }} 个歌单
        </span>
        <n-space>
          <n-button size="small" @click="emit('update:show', false)">取消</n-button>
          <n-button
            type="primary"
            size="small"
            :disabled="!selected.length"
            :loading="submitting"
            @click="doAdd"
          >添加到歌单</n-button>
        </n-space>
      </div>
    </template>
  </n-modal>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import {
  SearchOutline, AddOutline,
} from '@vicons/ionicons5'
import { NModal, NInput, NButton, NIcon, NCheckbox, NCheckboxGroup, NSpace, NEmpty, NInputGroup } from 'naive-ui'
import { useMessage } from 'naive-ui'
import { useLibraryStore } from '@/store/playback/library.js'
import {
  subsonicGetPlaylists,
  subsonicCreatePlaylist,
} from '@/api/playback/subsonic.js'
import { addSongsToPlaylist } from '@/api/playback/playlist.js'

const props = defineProps({
  show: { type: Boolean, default: false },
  /** 待添加的歌曲 ID 数组 */
  songIds: { type: Array, default: () => [] },
  /** 单曲标题（单曲模式时显示） */
  songTitle: { type: String, default: '' },
})

const emit = defineEmits(['update:show', 'added'])

const message = useMessage()
const library = useLibraryStore()

const search = ref('')
const selected = ref([])
const newName = ref('')
const submitting = ref(false)
const loading = ref(false)
const playlists = ref([])

const title = computed(() => {
  if (props.songTitle) return `添加到歌单 — ${props.songTitle}`
  return `添加到歌单 — ${props.songIds.length} 首歌曲`
})

const filtered = computed(() => {
  if (!search.value.trim()) return playlists.value
  const q = search.value.toLowerCase()
  return playlists.value.filter(pl => pl.name.toLowerCase().includes(q))
})

watch(() => props.show, async (v) => {
  if (v) {
    search.value = ''
    selected.value = []
    newName.value = ''
    await loadMyPlaylists()
  }
})

async function loadMyPlaylists() {
  loading.value = true
  try {
    const data = await subsonicGetPlaylists()
    playlists.value = (data?.['subsonic-response']?.playlists?.playlist) || []
  } catch {
    playlists.value = []
  } finally {
    loading.value = false
  }
}

async function doCreate() {
  const name = newName.value.trim()
  if (!name) return
  try {
    const resp = await subsonicCreatePlaylist(name, [])
    // 创建成功后加入列表并选中
    await loadMyPlaylists()
    const created = playlists.value.find(pl => pl.name === name)
    if (created) selected.value = [...selected.value, created.id]
    newName.value = ''
    message.success(`歌单「${name}」已创建`)
  } catch {
    message.warning('创建歌单失败')
  }
}

async function doAdd() {
  if (!selected.value.length || !props.songIds.length) return
  submitting.value = true
  let success = 0
  let failed = 0
  for (const plId of selected.value) {
    try {
      await addSongsToPlaylist(plId, props.songIds)
      success++
    } catch {
      failed++
    }
  }
  submitting.value = false
  if (success > 0) {
    message.success(`已添加到 ${success} 个歌单`)
    emit('added', selected.value)
  }
  if (failed > 0) message.warning(`${failed} 个歌单添加失败`)
  emit('update:show', false)
}
</script>

<style scoped>
.modal-search { margin-bottom: 8px; }
.modal-list { max-height: 280px; overflow-y: auto; margin-bottom: 8px; }
.modal-list::-webkit-scrollbar { width: 4px; }
.modal-create { margin-bottom: 4px; }

.playlist-item {
  padding: 4px 0;
  border-radius: 4px;
  transition: background 0.12s;
}
.playlist-item:hover { background: var(--ct-bg-hover); }

.pl-name { font-size: 13px; color: var(--ct-text); }
.pl-count { font-size: 11px; color: var(--ct-text-3); margin-left: 6px; }

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.footer-hint { font-size: 12px; color: var(--ct-text-3); }
</style>
