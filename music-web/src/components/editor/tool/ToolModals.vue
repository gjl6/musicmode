<template>

  <ToolDialog
    v-model:visible="toolDialogVisible"
    :tool-key="currentToolKey"
    :tool-label="currentToolLabel"
    :tool-desc="currentToolDesc"
    @done="onDone"
  />


  <n-modal :show="dedupVisible" preset="card" :title="$t('workbench.tools.dedup')"
    style="max-width: 840px; width: 840px" :mask-closable="true"
    @update:show="v => dedupVisible = v">
    <DedupPanel mode="tool" :target-path="target.path"
      :selected-files="target.files" :selected-folders="target.folders"
      @done="dedupVisible = false; onDone()" />
  </n-modal>

  <n-modal :show="splitToolVisible" preset="card" :title="$t('workbench.tools.split')"
    style="max-width: 960px; width: 960px" :mask-closable="true"
    @update:show="v => splitToolVisible = v">
    <SplitPanel mode="tool" :target-path="target.path"
      :selected-files="target.files" :selected-folders="target.folders"
      @done="splitToolVisible = false; onDone()" />
  </n-modal>

  <n-modal :show="replaceTextVisible" preset="card" :title="$t('workbench.tools.replaceText')"
    style="max-width: 900px; width: 900px" :mask-closable="true"
    @update:show="v => replaceTextVisible = v">
    <ReplaceTextPanel mode="tool" :target-path="target.path"
      :selected-files="target.files" :selected-folders="target.folders"
      @done="replaceTextVisible = false; onDone()" />
  </n-modal>

  <n-modal :show="langConvertVisible" preset="card" :title="$t('workbench.tools.langConvert')"
    style="max-width: 850px; width: 850px" :mask-closable="true"
    @update:show="v => langConvertVisible = v">
    <LangConvertPanel mode="tool" :target-path="target.path"
      :selected-files="target.files" :selected-folders="target.folders"
      @done="langConvertVisible = false; onDone()" />
  </n-modal>

  <n-modal :show="enrichToolVisible" preset="card" :title="$t('workbench.tools.qqEnrich')"
    style="max-width: 920px; width: 920px" :mask-closable="true"
    @update:show="v => enrichToolVisible = v">
    <EnrichToolPanel mode="tool" :target-path="target.path"
      :selected-files="target.files" :selected-folders="target.folders"
      @done="enrichToolVisible = false; onDone()" />
  </n-modal>

  <n-modal :show="batchWriteVisible" preset="card" :title="$t('workbench.tools.batchWrite')"
    style="max-width: 780px; width: 780px" :mask-closable="true"
    @update:show="v => batchWriteVisible = v">
    <BatchWritePanel mode="tool" :target-path="target.path"
      :selected-files="target.files" :selected-folders="target.folders"
      @done="batchWriteVisible = false; onDone()" />
  </n-modal>

  <n-modal :show="encodingRepairVisible" preset="card" :title="$t('workbench.tools.encodingRepair')"
    style="max-width: 900px; width: 900px" :mask-closable="true"
    @update:show="v => encodingRepairVisible = v">
    <EncodingRepairPanel mode="tool" :target-path="target.path"
      :selected-files="target.files" :selected-folders="target.folders"
      @done="encodingRepairVisible = false; onDone()" />
  </n-modal>

  <n-modal :show="formatConvertVisible" preset="card" :title="$t('workbench.tools.formatConvert')"
    style="max-width: 750px; width: 750px" :mask-closable="true"
    @update:show="v => formatConvertVisible = v">
    <FormatConvertPanel mode="tool" :target-path="target.path"
      :selected-files="target.files" :selected-folders="target.folders"
      @done="formatConvertVisible = false; onDone()" />
  </n-modal>

  <n-modal :show="cueSplitVisible" preset="card" :title="$t('cueSplit.title')"
    style="max-width: 600px; width: 600px" :mask-closable="true"
    @update:show="v => cueSplitVisible = v">
    <CueSplitPanel :target-path="target.path"
      :selected-files="target.files" :selected-folders="target.folders"
      @done="cueSplitVisible = false; onDone()" />
  </n-modal>

  <n-modal :show="organizeVisible" preset="card" :title="$t('organize.title')"
    style="max-width: 700px; width: 700px" :mask-closable="true"
    @update:show="v => organizeVisible = v">
    <OrganizePanel :target-path="target.path"
      :selected-files="target.files" :selected-folders="target.folders"
      @done="organizeVisible = false; onDone()" />
  </n-modal>

  <n-modal :show="deleteVisible" preset="card" :title="$t('deleteModule.title')"
    style="max-width: 640px; width: 640px" :mask-closable="true"
    @update:show="v => deleteVisible = v">
    <DeletePanel :target-path="target.path"
      :selected-files="target.files" :selected-folders="target.folders"
      @done="deleteVisible = false; onDone()" />
  </n-modal>
</template>

<script setup>
import { ref, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { useMessage } from 'naive-ui'
import { useToolTarget } from '@/composables/editor/useToolTarget.js'
import ToolDialog from '@/components/editor/tool/ToolDialog.vue'

const DedupPanel = defineAsyncComponent(() => import('@/components/editor/tool/DedupPanel.vue'))
const SplitPanel = defineAsyncComponent(() => import('@/components/editor/metadata/SplitPanel.vue'))
const ReplaceTextPanel = defineAsyncComponent(() => import('@/components/editor/tool/ReplaceTextPanel.vue'))
const LangConvertPanel = defineAsyncComponent(() => import('@/components/editor/tool/LangConvertPanel.vue'))
const EnrichToolPanel = defineAsyncComponent(() => import('@/components/editor/tool/EnrichToolPanel.vue'))
const BatchWritePanel = defineAsyncComponent(() => import('@/components/editor/tool/BatchWritePanel.vue'))
const EncodingRepairPanel = defineAsyncComponent(() => import('@/components/editor/tool/EncodingRepairPanel.vue'))
const FormatConvertPanel = defineAsyncComponent(() => import('@/components/editor/tool/FormatConvertPanel.vue'))
const CueSplitPanel = defineAsyncComponent(() => import('@/components/editor/tool/CueSplitPanel.vue'))
const OrganizePanel = defineAsyncComponent(() => import('@/components/editor/tool/OrganizePanel.vue'))
const DeletePanel = defineAsyncComponent(() => import('@/components/editor/tool/DeletePanel.vue'))

const emit = defineEmits(['done'])
const { t } = useI18n()
const message = useMessage()
const { toolTarget: target } = useToolTarget()

const TOOL_REGISTRY = {
  split:          { visible: ref(false), checkSelection: true },
  encodingRepair: { visible: ref(false), checkSelection: true },
  replaceText:    { visible: ref(false), checkSelection: true },
  langConvert:    { visible: ref(false), checkSelection: true },
  qqEnrich:       { visible: ref(false), checkSelection: true },
  batchWrite:     { visible: ref(false), checkSelection: true },
  dedup:          { visible: ref(false), checkSelection: true },
  formatConvert:  { visible: ref(false), checkSelection: true },
  cueSplit:       { visible: ref(false), checkSelection: true },
  organize:       { visible: ref(false), checkSelection: true },
  deleteFiles:    { visible: ref(false), checkSelection: false },
}

const {
  split: s, encodingRepair: er, replaceText: rt, langConvert: lc,
  qqEnrich: qe, batchWrite: bw, dedup: dd, formatConvert: fc,
  cueSplit: cs, organize: og, deleteFiles: df,
} = TOOL_REGISTRY

const splitToolVisible = s.visible
const encodingRepairVisible = er.visible
const replaceTextVisible = rt.visible
const langConvertVisible = lc.visible
const enrichToolVisible = qe.visible
const batchWriteVisible = bw.visible
const dedupVisible = dd.visible
const formatConvertVisible = fc.visible
const cueSplitVisible = cs.visible
const organizeVisible = og.visible
const deleteVisible = df.visible

const toolDialogVisible = ref(false)
const currentToolKey = ref('')
const currentToolLabel = ref('')
const currentToolDesc = ref('')

const ALL_TOOLS = [
  { key: 'split',          label: () => t('workbench.tools.split'),          desc: () => t('workbench.tools.splitDesc') },
  { key: 'encodingRepair', label: () => t('workbench.tools.encodingRepair'), desc: () => t('workbench.tools.encodingRepairDesc') },
  { key: 'langConvert',    label: () => t('workbench.tools.langConvert'),    desc: () => t('workbench.tools.langConvertDesc') },
  { key: 'replaceText',    label: () => t('workbench.tools.replaceText'),    desc: () => t('workbench.tools.replaceTextDesc') },
  { key: 'dedup',          label: () => t('workbench.tools.dedup'),          desc: () => t('workbench.tools.dedupDesc') },
  { key: 'qqEnrich',       label: () => t('workbench.tools.qqEnrich'),       desc: () => t('workbench.tools.qqEnrichDesc') },
  { key: 'formatConvert',  label: () => t('workbench.tools.formatConvert'),  desc: () => t('workbench.tools.formatConvertDesc') },
  { key: 'cueSplit',       label: () => t('workbench.tools.cueSplit'),       desc: () => t('workbench.tools.cueSplitDesc') },
  { key: 'batchWrite',     label: () => t('workbench.tools.batchWrite'),     desc: () => t('workbench.tools.batchWriteDesc') },
  { key: 'organize',       label: () => t('workbench.tools.organize'),       desc: () => t('workbench.tools.organizeDesc') },
  { key: 'deleteFiles',    label: () => t('workbench.tools.deleteFiles'),    desc: () => t('workbench.tools.deleteFilesDesc') },
]

function getToolList() {
  return ALL_TOOLS
}

function open(toolKey) {
  const entry = TOOL_REGISTRY[toolKey]
  if (!entry) return

    if (entry.visible !== undefined) {
    if (entry.checkSelection) {
      const t = target.value
      if (t.files.length === 0 && t.folders.length === 0) {
        message.warning(t('workbench.selectFilesFirst'))
        return
      }
    }
    entry.visible.value = true
    return
  }

    const tool = ALL_TOOLS.find(t => t.key === toolKey)
  if (!tool) return
  currentToolKey.value = tool.key
  currentToolLabel.value = tool.label()
  currentToolDesc.value = tool.desc()
  toolDialogVisible.value = true
}

function onDone() {
  emit('done')
}

defineExpose({ open, getToolList })
</script>
