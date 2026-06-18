<template>
  <div class="tool-panel-layout">
    <div class="tpl-files-col">
      <div class="tpl-files-header">
        <template v-if="selectedFiles.length || selectedFolders.length">
          {{ t('toolPanel.processingItems', { n: selectedFiles.length + selectedFolders.length }) }}
        </template>
        <template v-else>{{ t('toolPanel.directory', { path: targetPath || '—' }) }}</template>
      </div>
      <div v-if="selectedFiles.length || selectedFolders.length" class="tpl-file-list">
        <div v-for="f in selectedFolders" :key="'d' + f" class="tpl-file-row tpl-folder-row">
          <span class="tpl-file-idx">📁</span>
          <span class="tpl-file-name">{{ f }}</span>
        </div>
        <div v-for="f in selectedFiles" :key="'f' + f" class="tpl-file-row tpl-file-row-file">
          <span class="tpl-file-idx">🎵</span>
          <span class="tpl-file-name">{{ f.split(/[/\\]/).pop() }}</span>
        </div>
      </div>
    </div>
    <div class="tpl-main">
      <slot />
    </div>
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'

defineProps({
  selectedFiles: { type: Array, default: () => [] },
  selectedFolders: { type: Array, default: () => [] },
  targetPath: { type: String, default: '' },
})

const { t } = useI18n()
</script>

<style scoped>
.tool-panel-layout {
  display: flex;
  gap: 16px;
  overflow: hidden;
  height: 100%;
}

.tpl-files-col {
  flex-shrink: 0;
  width: 180px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--color-border);
  padding-right: 12px;
}

.tpl-files-header {
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
  font-weight: 500;
  padding: 4px 0 8px;
  flex-shrink: 0;
}

.tpl-file-list {
  flex: 1;
  overflow-y: auto;
  font-size: var(--text-xs);
}

.tpl-file-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 0;
  color: var(--color-text);
}

.tpl-folder-row {
  color: var(--color-text-secondary);
}

.tpl-file-idx {
  flex-shrink: 0;
  font-size: 11px;
}

.tpl-file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tpl-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.tpl-file-list::-webkit-scrollbar {
  width: 4px;
}

.tpl-file-list::-webkit-scrollbar-track {
  background: transparent;
}

.tpl-file-list::-webkit-scrollbar-thumb {
  background: var(--color-scrollbar);
  border-radius: 10px;
}
</style>
