<template>
  <div v-if="fileStore.currentPath" class="path-bar">
    <div class="path-left">
      <n-button v-if="appStore.sidebarCollapsed" size="tiny" text @click="appStore.toggleSidebar()">
        <template #icon><n-icon :size="16"><ChevronForwardOutline /></n-icon></template>
      </n-button>
      <span class="dir-path">{{ fileStore.currentPath || $t('workbench.rootDir') }}</span>
      <span class="file-count">{{ fileStore.totalFiles }} {{ $t('workbench.table.tracks') }}</span>
    </div>
    <div class="path-right">
      <n-button size="tiny" text :disabled="fileStore.selectedCount !== 1" @click="$emit('play-selected')">
        <template #icon><n-icon :size="16"><PlayOutline /></n-icon></template>
        {{ $t('common.play') }}
      </n-button>
      <n-button size="tiny" text :disabled="fileStore.selectedCount !== 1" @click="$emit('edit-selected')">
        <template #icon><n-icon :size="16"><CreateOutline /></n-icon></template>
        {{ $t('common.edit') }}
      </n-button>
      <n-popover trigger="click" placement="bottom-end">
        <template #trigger>
          <n-button size="tiny" text>
            <template #icon><n-icon :size="16"><SettingsOutline /></n-icon></template>
            {{ $t('workbench.table.showColumns') }}
          </n-button>
        </template>
        <div class="column-popover">
          <div class="popover-actions">
            <n-button size="tiny" text @click="prefs.showAll()">{{ $t('workbench.table.selectAll') }}</n-button>
            <n-button size="tiny" text @click="prefs.hideAll()">{{ $t('workbench.table.deselectAll') }}</n-button>
            <n-button size="tiny" text @click="prefs.reset()">{{ $t('workbench.table.resetColumns') }}</n-button>
          </div>
          <n-divider style="margin: 6px 0" />
          <div class="col-section-label">{{ $t('workbench.table.visible') }}</div>
          <div class="col-drop-zone" @dragover.prevent @drop="onDropVisible($event)">
            <div
              v-for="col in prefs.sortedVisibleColumns()"
              :key="col.key"
              class="col-drag-item"
              draggable="true"
              @dragstart="onDragStart($event, col.key)"
              @dragover="onDragOver($event, col.key)"
              @drop="onDropReorder($event, col.key)"
              @dragend="dragKey = null"
            >
              <n-icon :size="14" class="drag-handle"><MenuOutline /></n-icon>
              <span class="drag-label">{{ col.title }}</span>
              <n-button size="tiny" text class="col-hide-btn" @click="prefs.toggle(col.key)">
                <n-icon :size="14"><CloseOutline /></n-icon>
              </n-button>
            </div>
            <div v-if="prefs.sortedVisibleColumns().length === 0" class="col-empty">{{ $t('workbench.table.dragHint') }}</div>
          </div>
          <n-divider style="margin: 8px 0" />
          <div class="col-section-label">{{ $t('workbench.table.hidden') }}</div>
          <div class="col-drop-zone hidden-zone" @dragover.prevent @drop="onDropHidden($event)">
            <div
              v-for="col in prefs.sortedHiddenColumns()"
              :key="col.key"
              class="col-drag-item hidden-item"
              draggable="true"
              @dragstart="onDragStart($event, col.key)"
              @dragend="dragKey = null"
            >
              <span class="drag-label">{{ col.title }}</span>
              <n-button size="tiny" text class="col-show-btn" @click="prefs.toggle(col.key)">
                <n-icon :size="14"><AddOutline /></n-icon>
              </n-button>
            </div>
            <div v-if="prefs.sortedHiddenColumns().length === 0" class="col-empty">{{ $t('workbench.table.allShown') }}</div>
          </div>
        </div>
      </n-popover>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import {
  ChevronForwardOutline, CreateOutline, PlayOutline,
  SettingsOutline, MenuOutline, CloseOutline, AddOutline,
} from '@vicons/ionicons5'
import { useFileStore } from '@/store/editor/file.js'
import { useAppStore } from '@/store/editor/app.js'

const props = defineProps({
  prefs: { type: Object, required: true },
})

defineEmits(['play-selected', 'edit-selected'])
const fileStore = useFileStore()
const appStore = useAppStore()

const dragKey = ref(null)

function onDragStart(e, key) { dragKey.value = key; e.dataTransfer.effectAllowed = 'move'; e.dataTransfer.setData('text/plain', key) }
function onDragOver(e) { e.preventDefault(); e.dataTransfer.dropEffect = 'move' }
function onDropReorder(e, targetKey) {
  e.preventDefault()
  const src = dragKey.value
  if (!src || src === targetKey || !props.prefs.isVisible(src)) return
  const order = [...props.prefs.columnOrder.value]
  order.splice(order.indexOf(src), 1)
  order.splice(order.indexOf(targetKey), 0, src)
  props.prefs.columnOrder.value = order
  dragKey.value = null
}
function onDropVisible(e) {
  e.preventDefault()
  const key = dragKey.value || e.dataTransfer.getData('text/plain')
  if (!key) return
  if (!props.prefs.isVisible(key)) {
    props.prefs.toggle(key)
    const order = [...props.prefs.columnOrder.value]
    order.splice(order.indexOf(key), 1)
    order.push(key)
    props.prefs.columnOrder.value = order
  }
  dragKey.value = null
}
function onDropHidden(e) {
  e.preventDefault()
  const key = dragKey.value || e.dataTransfer.getData('text/plain')
  if (!key) return
  if (props.prefs.isVisible(key)) props.prefs.toggle(key)
  dragKey.value = null
}
</script>

<style scoped>
.path-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 16px;
  flex-shrink: 0;
  border-bottom: var(--border-width-default) solid var(--ct-border);
  border-radius: 0 0 var(--radius-md) var(--radius-md);
  background: var(--gradient-button, var(--ct-bg-secondary));
}
.path-left { display: flex; align-items: center; gap: 12px; }
.path-right { display: flex; align-items: center; gap: 8px; }
.dir-path { font-size: 12px; font-weight: 500; }
.file-count { font-size: 11px; color: var(--ct-text-2); }

.column-popover { width: 240px; max-height: 360px; overflow-y: auto; padding: 4px 0; }
.popover-actions { display: flex; gap: 4px; padding: 0 8px; }
.col-section-label {
  font-size: 10.5px; font-weight: 600; letter-spacing: 0.05em;
  text-transform: uppercase; color: var(--ct-text-2); padding: 4px 8px 2px;
}
.col-drop-zone { min-height: 28px; padding: 2px 0; }
.col-drop-zone.hidden-zone { opacity: 0.55; }
.col-drag-item {
  display: flex; align-items: center; gap: 6px;
  padding: 4px 8px; border-radius: 4px; cursor: grab; transition: background 0.1s;
}
.col-drag-item:hover { background: var(--sb-bg-hover); }
.col-drag-item:active { cursor: grabbing; background: rgb(var(--ct-accent-rgb) / 0.15); }
.hidden-item .drag-label { color: var(--ct-text-2); }
.drag-handle { flex-shrink: 0; color: var(--ct-text-2); opacity: 0.4; }
.drag-label { flex: 1; font-size: 12px; user-select: none; }
.col-hide-btn, .col-show-btn { flex-shrink: 0; padding: 2px !important; opacity: 0.3; }
.col-hide-btn:hover, .col-show-btn:hover { opacity: 1; }
.col-empty { font-size: 11px; color: var(--ct-text-2); font-style: italic; padding: 8px; text-align: center; }
</style>
