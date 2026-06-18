

import { computed } from 'vue'
import { useFileStore } from '@/store/editor/file.js'

export function useToolTarget() {
  const fileStore = useFileStore()

  const toolTarget = computed(() => {
    const selected = fileStore.pageFiles.filter(f => fileStore.selectedIds.has(f.id))
    const files = selected.map(f => f.path)
    const base = fileStore.currentPath || ''
    const folderPaths = [...fileStore.selectedFolderNames].map(name =>
      base && base !== '/' ? `${base}/${name}` : `${base}${name}`,
    )
    return { path: base, files, folders: folderPaths }
  })

  return { toolTarget }
}
