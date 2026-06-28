<template>
  <div v-if="toolList.length > 0" class="quick-tools" :class="{ compact: toolsCompact }">
    <div class="tools-row" :class="{ compact: toolsCompact }">
      <div
        v-for="tool in toolList"
        :key="tool.key"
        class="tool-card"
        :class="{ 'icon-only': toolsCompact }"
        :title="toolsCompact ? tool.label + ' — ' + tool.desc : ''"
        @click="$emit('tool-click', tool.key)"
      >
        <div class="tool-icon">
          <n-icon :size="toolsCompact ? 18 : 20"><component :is="tool.icon" /></n-icon>
        </div>
        <div v-show="!toolsCompact" class="tool-info">
          <span class="tool-name">{{ tool.label }}</span>
          <span class="tool-desc">{{ tool.desc }}</span>
        </div>
      </div>
    </div>
    <div class="tools-actions">
      <n-button size="tiny" text @click="toolsCompact = !toolsCompact">
        <template #icon>
          <n-icon :size="15">
            <GridOutline v-if="toolsCompact" />
            <ListOutline v-else />
          </n-icon>
        </template>
        {{ toolsCompact ? t('workbench.tools.fullMode') : t('workbench.tools.compactMode') }}
      </n-button>
      <n-popover trigger="click" placement="bottom-end" :width="420">
        <template #trigger>
          <n-button size="tiny" text>
            <template #icon><n-icon :size="15"><AppsOutline /></n-icon></template>
            {{ t('workbench.tools.allTools') }}
          </n-button>
        </template>
        <div class="all-tools-popover">
          <div
            v-for="tool in toolList"
            :key="tool.key"
            class="all-tool-item"
            @click="$emit('tool-click', tool.key)"
          >
            <div class="all-tool-icon">
              <n-icon :size="18"><component :is="tool.icon" /></n-icon>
            </div>
            <div class="all-tool-text">
              <span class="all-tool-name">{{ tool.label }}</span>
              <span class="all-tool-desc">{{ tool.desc }}</span>
            </div>
          </div>
        </div>
      </n-popover>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  CodeSlashOutline, CopyOutline, LanguageOutline, TextOutline,
  LayersOutline, CloudDownloadOutline, SwapHorizontalOutline,
  CutOutline, SaveOutline, FolderOutline, TrashOutline,
  AddCircleOutline, AppsOutline, GridOutline, ListOutline,
} from '@vicons/ionicons5'
import { useAuthStore } from '@/store/auth.js'

const props = defineProps({
  tools: { type: Array, default: null },
})

defineEmits(['tool-click'])
const { t } = useI18n()
const auth = useAuthStore()
const toolsCompact = ref(false)

const allTools = [
  { key: 'split',          label: t('workbench.tools.split'),          desc: t('workbench.tools.splitDesc'),          icon: CodeSlashOutline,    perm: 'music:write' },
  { key: 'encodingRepair', label: t('workbench.tools.encodingRepair'), desc: t('workbench.tools.encodingRepairDesc'), icon: CopyOutline,          perm: 'music:write' },
  { key: 'langConvert',    label: t('workbench.tools.langConvert'),    desc: t('workbench.tools.langConvertDesc'),    icon: LanguageOutline,      perm: 'music:write' },
  { key: 'replaceText',    label: t('workbench.tools.replaceText'),    desc: t('workbench.tools.replaceTextDesc'),    icon: TextOutline,          perm: 'music:write' },
  { key: 'dedup',          label: t('workbench.tools.dedup'),          desc: t('workbench.tools.dedupDesc'),          icon: LayersOutline,        perm: 'music:write' },
  { key: 'qqEnrich',       label: t('workbench.tools.qqEnrich'),       desc: t('workbench.tools.qqEnrichDesc'),       icon: CloudDownloadOutline,  perm: 'music:write' },
  { key: 'formatConvert',  label: t('workbench.tools.formatConvert'),  desc: t('workbench.tools.formatConvertDesc'),  icon: SwapHorizontalOutline, perm: 'music:write' },
  { key: 'cueSplit',       label: t('workbench.tools.cueSplit'),       desc: t('workbench.tools.cueSplitDesc'),       icon: CutOutline,           perm: 'music:write' },
  { key: 'batchWrite',     label: t('workbench.tools.batchWrite'),     desc: t('workbench.tools.batchWriteDesc'),     icon: SaveOutline,          perm: 'music:write' },
  { key: 'organize',       label: t('workbench.tools.organize'),       desc: t('workbench.tools.organizeDesc'),       icon: FolderOutline,        perm: 'music:write' },
  { key: 'deleteFiles',    label: t('workbench.tools.deleteFiles'),    desc: t('workbench.tools.deleteFilesDesc'),    icon: TrashOutline,         perm: 'music:delete' },
  { key: 'importCollection', label: t('workbench.tools.importCollection'), desc: t('workbench.tools.importCollectionDesc'), icon: AddCircleOutline, perm: 'music:import' },
]

/** 根据权限过滤工具列表；传了 tools prop 则用它，否则用默认歌曲工具 */
const toolList = computed(() => {
  const source = props.tools || allTools
  return source.filter((t) => !t.perm || auth.hasPermission(t.perm))
})
</script>

<style scoped>
.quick-tools {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  border-top: var(--border-width-default) solid var(--ct-border);
  background: var(--gradient-button, var(--ct-bg-secondary));
  padding: 4px 16px;
  gap: 10px;
}
.tools-row {
  display: flex; flex: 1; gap: 10px;
  overflow-x: auto; padding-bottom: 2px;
  scroll-behavior: smooth; min-width: 0;
}
.tools-row.compact { gap: 6px; }
.tools-row::-webkit-scrollbar { height: 3px; }
.tools-row::-webkit-scrollbar-track { background: transparent; }
.tools-row::-webkit-scrollbar-thumb { background: transparent; border-radius: 10px; }
.tools-row:hover::-webkit-scrollbar-thumb { background: var(--sb-scrollbar); }

.tools-actions {
  display: flex; flex-direction: column; align-items: flex-start;
  gap: 0; flex-shrink: 0; width: 64px; min-width: 64px;
}

/* 工具卡片 */
.tool-card {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-xl);
  background: var(--gradient-tool-card, var(--ct-card-bg));
  border: var(--border-width-strong) solid var(--ct-border);
  cursor: pointer; transition: all var(--transition-base);
  flex: 1 0 auto; min-width: 140px;
  box-shadow: var(--effect-card-inner), var(--effect-card-outer);
}
.tool-card:hover {
  background: var(--gradient-tool-card-hover, var(--ct-card-hover));
  border-color: rgb(var(--ct-accent-rgb) / 0.3);
  transform: var(--transform-card-hover);
  box-shadow: var(--shadow-sm);
}
.tool-card:active {
  transform: var(--transform-card-active);
  box-shadow: var(--effect-press);
}
.tool-card.icon-only {
  justify-content: center; padding: 6px; gap: 0;
  flex: 0 0 auto; min-width: unset; width: 38px; height: 38px;
}
.tool-card.icon-only .tool-icon { width: 26px; height: 26px; }

.tool-icon {
  flex-shrink: 0; width: 32px; height: 32px;
  display: flex; align-items: center; justify-content: center;
  border-radius: var(--radius-sm);
  background: rgb(var(--ct-accent-rgb) / 0.08);
  color: rgb(var(--ct-accent-rgb));
  transition: all var(--transition-base);
  border: var(--border-width-default) solid rgb(var(--ct-accent-rgb) / 0.2);
  box-shadow: var(--effect-card-inner);
}
.tool-card:hover .tool-icon { background: rgb(var(--ct-accent-rgb) / 0.15); }

.tool-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
.tool-name { font-size: 12px; font-weight: 500; color: var(--ct-text); line-height: 1.3; }
.tool-desc {
  font-size: 10.5px; color: var(--ct-text-2); line-height: 1.3;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

/* 全部工具弹出面板 */
.all-tools-popover { display: grid; grid-template-columns: repeat(2, 1fr); gap: 6px; padding: 4px 0; }
.all-tool-item {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 10px; border-radius: 6px; cursor: pointer;
  transition: background 0.1s;
}
.all-tool-item:hover { background: var(--sb-bg-hover); box-shadow: var(--shadow-sm); }
.all-tool-icon {
  flex-shrink: 0; width: 30px; height: 30px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 5px;
  background: rgb(var(--ct-accent-rgb) / 0.08);
  color: rgb(var(--ct-accent-rgb));
}
.all-tool-text { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.all-tool-name { font-size: 12px; font-weight: 500; color: var(--ct-text); line-height: 1.4; }
.all-tool-desc { font-size: 10.5px; color: var(--ct-text-2); line-height: 1.3; }
</style>
