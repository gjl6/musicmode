import { useEditStore } from '@/store/editor/edit.js'
import { useAppStore } from '@/store/editor/app.js'
import { useFileStore } from '@/store/editor/file.js'
import { fetchMetadata } from '@/api/editor/music.js'
import { songField, artistNames } from '@/utils/musicMeta.js'
import { useArtistConfig } from '@/composables/editor/useArtistConfig.js'

export function useEditAction() {
  const editStore = useEditStore()
  const appStore = useAppStore()
  const fileStore = useFileStore()
  const { joinSeparator } = useArtistConfig()

  function openEditor(file) {
    if (!fileStore.selectedIds.has(file.id)) {
      fileStore.setSelectedIds([file.id])
    }

        appStore.openDrawer()
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        const meta = file.meta
        editStore.startEdit(file, {
          title: songField(meta, 'title') || file.title || '',
          artist: artistNames(meta, joinSeparator.value || undefined) || file.artist || '',
          album: meta?.albums?.[0]?.albumName || file.album || '',
          year: songField(meta, 'year') || file.year || 0,
          genre: meta?.styles?.[0]?.styleName || file.genre || '',
        })
                fetchMetadata(file.path)
          .then(res => {
            editStore.refreshMeta(file, res)
          })
          .catch(() => {})
      })
    })
  }

  return { openEditor }
}
