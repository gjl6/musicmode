/*
 * useToolTarget — 工具面板目标路径计算
 *
 * 替换 Workbench.vue 中 10+ 个完全相同的 xxxTarget computed 属性。
 * 使用方式：const { toolTarget } = useToolTarget()
 *          toolTarget.value → { path, files, folders }
 */
import { computed } from 'vue'
import { useFileStore } from '@/store/editor/file.js'

export function useToolTarget() {
  const fileStore = useFileStore()

  const toolTarget = computed(() => {
    const selected = fileStore.pageFiles.filter(f => fileStore.selectedIds.has(f.id))
    const files = selected.map(f => f.path)
    const base = fileStore.currentPath || ''
    // 去掉 "/" 前缀，发相对路径，避免 Linux 下被 Path.isAbsolute() 误判
    const folderPaths = [...fileStore.selectedFolderNames].map(name =>
      base && base !== '/' ? `${base}/${name}` : name,
    )
    return { path: base, files, folders: folderPaths }
  })

  return { toolTarget }
}
