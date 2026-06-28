<template>
  <div class="playlist-edit-page">
    <div class="page-header">
      <n-button text @click="$router.back()">
        <n-icon :size="16"><ArrowBackOutline /></n-icon>
      </n-button>
      <h1>{{ $t('playlist.edit') }}</h1>
      <n-input
        v-model:value="searchText"
        :placeholder="$t('player.searchPlaceholder')"
        size="small"
        clearable
        round
        style="width:180px;margin-left:auto"
        @keyup.enter="doSearch"
      >
        <template #prefix>
          <n-icon :size="16"><SearchOutline /></n-icon>
        </template>
      </n-input>
    </div>

    <n-spin :show="!pl" size="medium">
      <n-card v-if="pl" style="max-width:480px">
        <n-form-item :label="$t('playlist.name')">
          <n-input v-model:value="name" />
        </n-form-item>
        <n-form-item :label="$t('playlist.comment')">
          <n-input v-model:value="comment" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" />
        </n-form-item>
        <n-form-item :label="$t('playlist.isPublic')">
          <n-switch v-model:value="isPublic" />
        </n-form-item>
        <n-space justify="end">
          <n-button @click="$router.back()">{{ $t('common.cancel') }}</n-button>
          <n-button type="primary" @click="doSave" :loading="saving">{{ $t('common.save') }}</n-button>
        </n-space>
      </n-card>
    </n-spin>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowBackOutline, SearchOutline } from '@vicons/ionicons5'
import { useLibraryStore } from '@/store/playback/library.js'
import { subsonicUpdatePlaylist } from '@/api/playback/subsonic.js'

const route = useRoute()
const router = useRouter()

const searchText = ref('')

function doSearch() {
  const q = searchText.value.trim()
  if (!q) return
  router.push(`/player/search?q=${encodeURIComponent(q)}`)
}

const library = useLibraryStore()

const pl = computed(() => library.playlistDetail)
const name = ref('')
const comment = ref('')
const isPublic = ref(false)
const saving = ref(false)

onMounted(async () => {
  const id = route.params.id
  if (id) {
    await library.loadPlaylist(id)
    if (pl.value) {
      name.value = pl.value.name || ''
      comment.value = pl.value.comment || ''
      isPublic.value = !!pl.value.public
    }
  }
})

async function doSave() {
  if (!name.value.trim()) return
  saving.value = true
  try {
    await subsonicUpdatePlaylist(pl.value.id, {
      name: name.value.trim(),
      comment: comment.value,
      isPublic: isPublic.value,
    })
    window.$message?.success('Saved')
    router.push(`/player/playlists/${pl.value.id}`)
  } catch (e) {
    window.$message?.error('Failed to save')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.playlist-edit-page {
  max-width: 600px;
  margin: 0 auto;
  padding: 24px 32px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.page-header h1 {
  font-size: 20px;
  font-weight: 700;
  margin: 0;
  color: var(--ct-text);
}
</style>
