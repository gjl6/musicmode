<template>
  <aside class="sidebar" :class="{ collapsed: appStore.sidebarCollapsed }">
    <!-- 面包屑 -->
    <div class="breadcrumb">
      <span
        v-for="(seg, i) in breadcrumbs"
        :key="i"
        class="crumb"
        :class="{ active: i === breadcrumbs.length - 1 }"
        @click="navigateTo(i)"
      >
        {{ seg.label }}
        <span v-if="i < breadcrumbs.length - 1" class="crumb-sep">/</span>
      </span>
      <button class="collapse-btn" :title="appStore.sidebarCollapsed ? $t('workbench.expandSidebar') : $t('workbench.collapseSidebar')" @click="appStore.toggleSidebar()">
        <n-icon :size="14">
          <ChevronBackOutline v-if="!appStore.sidebarCollapsed" />
          <ChevronForwardOutline v-else />
        </n-icon>
      </button>
    </div>

    <!-- 搜索 -->
    <div class="search-bar">
      <n-input
        :value="fileStore.searchKeyword"
        :placeholder="t('workbench.searchPlaceholder')"
        size="small"
        clearable
        @update:value="fileStore.searchKeyword = $event"
      >
        <template #prefix>
          <n-icon :size="14"><SearchOutline /></n-icon>
        </template>
      </n-input>
    </div>

<!-- 返回上级 -->
    <div v-if="!fileStore.isRoot" class="back-row" @click="fileStore.goUp()">
      <n-icon :size="14"><ArrowUpOutline /></n-icon>
      <span>{{ t('workbench.backToParent') }}</span>
    </div>

    <!-- 文件树 -->
    <div class="sidebar-body">
      <n-spin :show="fileStore.loading" size="small">
        <ul class="tree-list">
          <li
            v-for="folder in fileStore.folders"
            :key="'d-' + folder"
            class="tree-item folder"
            :class="{ selected: fileStore.selectedFolderNames.has(folder) }"
            @click="handleFolderClick(folder)"
            @dblclick="enterFolder(folder)"
          >
            <span class="select-bar" />
            <n-icon :size="16">
              <FolderOutline />
            </n-icon>
            <span class="item-name">{{ folder }}</span>
          </li>

          <li
            v-for="file in fileStore.filteredFiles"
            :key="file.id"
            class="tree-item file"
            :class="{ selected: fileStore.selectedIds.has(file.id) }"
            @click="handleFileClick(file)"
          >
            <span class="select-bar" />
            <n-icon :size="16" color="var(--n-text-color-3)">
              <MusicalNoteOutline />
            </n-icon>
            <span class="item-name">{{ file.name || file.fileName }}</span>
            <span class="item-format">{{ file.format }}</span>
          </li>
        </ul>
      </n-spin>

      <n-empty
        v-if="!fileStore.loading && fileStore.folders.length === 0 && fileStore.files.length === 0"
        :description="t('workbench.emptyDir')"
        size="small"
      />
    </div>

    <!-- 选中计数 -->
    <div v-if="fileStore.selectedCount > 0" class="select-footer">
      已选 {{ fileStore.selectedFileCount }} 个文件<span v-if="fileStore.selectedFolderCount > 0">, {{ fileStore.selectedFolderCount }} 个文件夹</span>
    </div>
  </aside>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useFileStore } from '@/store/editor/file.js'
import { useAppStore } from '@/store/editor/app.js'
import { useEditAction } from '@/composables/editor/useEditAction.js'
import {
  FolderOutline,
  MusicalNoteOutline,
  SearchOutline,
  ArrowUpOutline,
  ChevronBackOutline,
  ChevronForwardOutline,
} from '@vicons/ionicons5'

const { t } = useI18n()
const fileStore = useFileStore()
const appStore = useAppStore()
const { openEditor } = useEditAction()

// ── 双击检测 ──

const clickTimer = ref(null)

// ── 面包屑 ──

const breadcrumbs = computed(() => {
  const path = fileStore.currentPath
  if (!path || path === '/') return [{ label: t('workbench.rootDir'), path: '' }]
  const segs = path.split('/').filter(Boolean)
  const crumbs = [{ label: t('workbench.rootDir'), path: '' }]
  for (let i = 0; i < segs.length; i++) {
    crumbs.push({
      label: segs[i],
      path: segs.slice(0, i + 1).join('/'),
    })
  }
  return crumbs
})

function navigateTo(index) {
  const target = breadcrumbs.value[index]
  if (target && target.path !== fileStore.currentPath) {
    fileStore.loadDirectory(target.path)
  }
}

function enterFolder(folder) {
  const base = fileStore.currentPath || ''
  const sep = base.endsWith('/') || base === '' ? '' : '/'
  fileStore.loadDirectory(base + sep + folder)
}

function handleFolderClick(folder) {
  // 双击 → 进入目录
  if (clickTimer.value && clickTimer.value.id === folder && clickTimer.value.type === 'folder') {
    clearTimeout(clickTimer.value.timer)
    clickTimer.value = null
    fileStore.clearSelection()
    enterFolder(folder)
    return
  }
  // 单击 → 切换选中
  fileStore.toggleFolderSelect(folder)
  clickTimer.value = { id: folder, type: 'folder', timer: setTimeout(() => { clickTimer.value = null }, 300) }
}

function handleFileClick(file) {
  // 双击 → 打开编辑
  if (clickTimer.value && clickTimer.value.id === file.id && clickTimer.value.type === 'file') {
    clearTimeout(clickTimer.value.timer)
    clickTimer.value = null
    fileStore.clearSelection()
    fileStore.toggleSelect(file.id)
    openEditor(file)
    return
  }
  // 单击 → 切换选中
  fileStore.toggleSelect(file.id)
  clickTimer.value = { id: file.id, type: 'file', timer: setTimeout(() => { clickTimer.value = null }, 300) }
}

onMounted(() => {
  if (fileStore.folders.length === 0 && fileStore.files.length === 0) {
    fileStore.loadDirectory('')
  }
})
</script>


<style scoped>
/* ─────────────────────────────────────
   Audio Precision — Sidebar
   ───────────────────────────────────── */
.sidebar {
  width: 260px;
  height: 100vh;
  display: flex;
  flex-direction: column;
  border-right: var(--border-width-strong) solid var(--sb-border);
  background: var(--gradient-sidebar, var(--sb-bg));
  color: var(--sb-text);
  position: relative;
  transition: width 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  flex-shrink: 0;
  overflow: hidden;
}

.sidebar.collapsed {
  width: 0;
  border-right: none;
}

/* 噪点纹理 */
.sidebar::before {
  content: '';
  position: absolute;
  inset: 0;
  opacity: 0.03;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
  pointer-events: none;
  z-index: 1;
}

:root.dark .sidebar::before {
  opacity: 0.018;
  filter: invert(1);
}

/* ── 面包屑 ── */

.breadcrumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  padding: 14px 14px 10px;
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.01em;
  flex-shrink: 0;
  position: relative;
  z-index: 2;
}

.crumb {
  color: var(--sb-text-2);
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.15s;
  padding: 1px 0;
}

.crumb:hover {
  color: var(--sb-text);
}

.crumb.active {
  color: var(--sb-text);
  font-weight: 600;
  cursor: default;
}

.collapse-btn {
  margin-left: auto;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--sb-text-3);
  cursor: pointer;
  transition: all 0.15s;
  padding: 0;
}

.collapse-btn:hover {
  background: var(--sb-bg-hover);
  color: var(--sb-text);
  box-shadow: var(--shadow-sm);
}

.crumb-sep {
  color: var(--sb-text-3);
  margin: 0 5px;
  cursor: default;
  font-weight: 400;
}

/* ── 搜索 ── */

.search-bar {
  padding: 0 14px 10px;
  flex-shrink: 0;
  position: relative;
  z-index: 2;
}

/* ── 返回上级 ── */

.back-row {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 5px 14px;
  margin: 0 6px 6px;
  border-radius: 7px;
  font-size: 11.5px;
  font-weight: 500;
  letter-spacing: 0.01em;
  color: var(--sb-text-2);
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.15s;
  position: relative;
  z-index: 2;
}

.back-row:hover {
  background: var(--sb-bg-hover);
  color: var(--sb-text);
  box-shadow: var(--shadow-sm);
}

/* ── 树列表 ── */

.sidebar-body {
  flex: 1;
  overflow-y: auto;
  padding: 0 6px 16px;
  position: relative;
  z-index: 2;
}

.sidebar-body::-webkit-scrollbar {
  width: 4px;
}

.sidebar-body::-webkit-scrollbar-track {
  background: transparent;
}

.sidebar-body::-webkit-scrollbar-thumb {
  background: var(--sb-scrollbar);
  border-radius: 10px;
}

.tree-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

/* ── 列表项共用 ── */

.tree-item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 6px 10px;
  margin: 0 0 1px;
  border-radius: 7px;
  cursor: default;
  transition:
    background 0.12s ease,
    padding-left 0.18s ease;
  position: relative;
  font-size: 12.5px;
  letter-spacing: 0.01em;
  color: var(--sb-text);
}

.tree-item.folder {
  cursor: pointer;
  font-weight: 500;
}

.tree-item.folder:hover {
  background: var(--sb-bg-hover);
  padding-left: 13px;
  box-shadow: var(--shadow-sm);
}

.tree-item.folder.selected {
  background: rgb(var(--sb-selection-rgb) / 0.12);
  box-shadow: var(--effect-selection-glow);
}

.tree-item.folder.selected :deep(.n-icon) {
  color: rgb(var(--sb-selection-rgb)) !important;
}

.tree-item.folder.selected .select-bar {
  background: rgb(var(--sb-selection-rgb));
  box-shadow: var(--effect-selection-bar);
}

.tree-item.file {
  cursor: pointer;
  color: var(--sb-text-2);
}

.tree-item.file:hover {
  background: var(--sb-bg-hover);
  padding-left: 13px;
  box-shadow: var(--shadow-sm);
}

/* 文件夹图标 */
.tree-item.folder :deep(.n-icon) {
  color: var(--sb-folder) !important;
  transition: transform 0.2s ease;
}

.tree-item.folder:hover :deep(.n-icon) {
  transform: scale(1.08);
}

/* 文件图标 */
.tree-item.file :deep(.n-icon) {
  color: var(--sb-text-3) !important;
  transition: color 0.15s;
}

/* ── 选中态 ── */

.tree-item.file.selected {
  background: rgb(var(--sb-selection-rgb) / 0.12);
  color: var(--sb-text);
  box-shadow: var(--effect-selection-glow);
}

.tree-item.file.selected :deep(.n-icon) {
  color: rgb(var(--sb-selection-rgb)) !important;
}

.select-bar {
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: var(--selection-bar-width);
  border-radius: 0 4px 4px 0;
  background: transparent;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.tree-item.file.selected .select-bar {
  background: rgb(var(--sb-selection-rgb));
  box-shadow: var(--effect-selection-bar);
}

/* ── 文件名 ── */

.item-name {
  font-size: 12.5px;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}

.tree-item.file.selected .item-name {
  font-weight: 500;
}

/* ── 格式标签 ── */

.item-format {
  font-size: 10.5px;
  color: var(--sb-format-text);
  text-transform: lowercase;
  font-weight: 500;
  letter-spacing: 0.08em;
  padding: 1px 5px;
  border-radius: 3px;
  background: var(--sb-format-bg);
  min-width: 28px;
  text-align: center;
}

.tree-item.file.selected .item-format {
  color: rgb(var(--sb-selection-rgb));
  background: rgb(var(--sb-selection-rgb) / 0.1);
}

/* ── 选中底部状态栏 ── */

.select-footer {
  flex-shrink: 0;
  padding: 8px 14px;
  font-size: 10.5px;
  font-weight: 500;
  letter-spacing: 0.03em;
  color: rgb(var(--sb-selection-rgb));
  border-top: var(--border-width-default) solid rgb(var(--sb-selection-rgb) / 0.15);
  background: rgb(var(--sb-selection-rgb) / 0.05);
  text-align: center;
  position: relative;
  z-index: 2;
  font-feature-settings: 'tnum';
}
</style>
