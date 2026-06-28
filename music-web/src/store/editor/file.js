import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { fetchDirectory, fetchDirectoryMetadata } from '@/api/editor/browse.js'

export const useFileStore = defineStore('file', () => {
  const currentPath = ref('')
  const folders = ref([])
  const files = ref([])           // 侧边栏：全部文件（基础信息，不解析标签）
  const pageFiles = ref([])       // 表格：当前页文件（带元数据）
  const loading = ref(false)
  const metadataLoading = ref(false)

  // 分页
  const totalFiles = ref(0)
  const currentPage = ref(1)
  const pageSize = ref(50)

  const searchKeyword = ref('')
  const selectedIds = ref(new Set())
  const selectedFolderNames = ref(new Set())

  // ── 计算属性 ──

  const filteredFiles = computed(() => {
    let result = files.value

    if (searchKeyword.value.trim()) {
      const kw = searchKeyword.value.trim().toLowerCase()
      result = result.filter((f) =>
        f.name.toLowerCase().includes(kw) ||
        (f.title || '').toLowerCase().includes(kw) ||
        (f.artist || '').toLowerCase().includes(kw) ||
        (f.album || '').toLowerCase().includes(kw) ||
        (f.format || '').toLowerCase().includes(kw),
      )
    }

    return result
  })

  const selectedFiles = computed(() =>
    files.value.filter((f) => selectedIds.value.has(f.id)),
  )

  const selectedFileCount = computed(() => selectedIds.value.size)
  const selectedFolderCount = computed(() => selectedFolderNames.value.size)
  const selectedCount = computed(() => selectedFileCount.value + selectedFolderCount.value)

  const isRoot = computed(() => !currentPath.value || currentPath.value === '/')

  // ── 方法 ──

  async function loadDirectory(path) {
    loading.value = true
    try {
      const res = await fetchDirectory(path ?? '')
      currentPath.value = res.path
      folders.value = res.folders || []
      files.value = res.files || []
      clearSelection()
    } catch (err) {
      console.error('[FileStore] 加载目录失败:', err)
    } finally {
      loading.value = false
    }
  }

  async function loadPage(page) {
    metadataLoading.value = true
    try {
      const res = await fetchDirectoryMetadata(currentPath.value, page, pageSize.value)
      pageFiles.value = res.files || []
      totalFiles.value = res.total || 0
      currentPage.value = res.page || page
    } catch (err) {
      console.error('[FileStore] 加载元数据失败:', err)
    } finally {
      metadataLoading.value = false
    }
  }

  function goUp() {
    if (isRoot.value) return
    const parts = currentPath.value.split('/').filter(Boolean)
    parts.pop()
    loadDirectory(parts.join('/') || '')
  }

  function buildPath(segments) {
    return segments.join('/') || ''
  }

  function toggleSelect(id) {
    const next = new Set(selectedIds.value)
    next.has(id) ? next.delete(id) : next.add(id)
    selectedIds.value = next
  }

  function toggleFolderSelect(name) {
    const next = new Set(selectedFolderNames.value)
    next.has(name) ? next.delete(name) : next.add(name)
    selectedFolderNames.value = next
  }

  function setSelectedIds(ids) {
    selectedIds.value = new Set(ids)
  }

  function clearSelection() {
    selectedIds.value = new Set()
    selectedFolderNames.value = new Set()
  }

  return {
    currentPath, folders, files, pageFiles, loading, metadataLoading,
    totalFiles, currentPage, pageSize,
    searchKeyword, selectedIds, selectedFolderNames,
    filteredFiles, selectedFiles, selectedCount, selectedFileCount, selectedFolderCount, isRoot,
    loadDirectory, loadPage, goUp, toggleSelect, toggleFolderSelect, setSelectedIds, clearSelection,
  }
})
