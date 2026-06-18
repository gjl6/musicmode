

import { onMounted, onUnmounted } from 'vue'
import i18n from '@/i18n/index.js'
import { useAppStore } from '@/store/editor/app.js'
import { useFileStore } from '@/store/editor/file.js'
import { useEditStore } from '@/store/editor/edit.js'
import { useEditorPlayerStore } from '@/store/editor/player.js'
import { saveMetadata } from '@/api/editor/music.js'
import { toMusicMetadata } from '@/utils/musicMeta.js'

export function useKeyboard() {
  const app = useAppStore()
  const fileStore = useFileStore()
  const editStore = useEditStore()
  const player = useEditorPlayerStore()

  function handler(e) {
        const tag = e.target.tagName
    const isInput = tag === 'INPUT' || tag === 'TEXTAREA' || e.target.isContentEditable

        if (e.key === 'Escape') {
      if (app.drawerVisible) {
        app.closeDrawer()
        return
      }
      fileStore.clearSelection()
      return
    }

        if (e.ctrlKey && e.key === 's') {
      e.preventDefault()
      if (editStore.isDirty && editStore.editingFile) {
        const artists = editStore.currentMeta.artists
        if (artists && artists.length > 0) {
          artists[editStore.currentMeta.activeArtistIndex] = { ...editStore.currentMeta.artist }
        }
        const meta = toMusicMetadata(editStore.currentMeta)
        saveMetadata(editStore.editingFile.path, meta)
        editStore.clearDraft()
        editStore.resetEdit()
        window.$message?.success(i18n.global.t('common.saveSuccess'))
      }
      return
    }

        if (e.ctrlKey && !e.shiftKey && e.key === 'z') {
      e.preventDefault()
      if (!isInput) editStore.undo()
      return
    }

        if ((e.ctrlKey && e.key === 'y') || (e.ctrlKey && e.shiftKey && e.key === 'z')) {
      e.preventDefault()
      if (!isInput) editStore.redo()
      return
    }

        if (isInput) return

        if (e.key === ' ') {
      e.preventDefault()
      player.togglePlay()
      return
    }

        if (e.key === 'ArrowUp') {
      e.preventDefault()
      const files = fileStore.filteredFiles
      if (files.length === 0) return
      const current = fileStore.selectedFiles[0]
      const idx = current ? files.findIndex((f) => f.id === current.id) : -1
      const nextIdx = idx > 0 ? idx - 1 : files.length - 1
      fileStore.clearSelection()
      fileStore.toggleSelect(files[nextIdx].id)
      return
    }

        if (e.key === 'ArrowDown') {
      e.preventDefault()
      const files = fileStore.filteredFiles
      if (files.length === 0) return
      const current = fileStore.selectedFiles[0]
      const idx = current ? files.findIndex((f) => f.id === current.id) : -1
      const nextIdx = idx < files.length - 1 ? idx + 1 : 0
      fileStore.clearSelection()
      fileStore.toggleSelect(files[nextIdx].id)
      return
    }

        if (e.ctrlKey && e.key === 'a') {
      e.preventDefault()
      fileStore.selectAll()
      return
    }
  }

  onMounted(() => window.addEventListener('keydown', handler))
  onUnmounted(() => window.removeEventListener('keydown', handler))
}
