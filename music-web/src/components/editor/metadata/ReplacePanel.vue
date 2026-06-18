<template>
  <div class="replace-panel">

    <div class="rp-section">
      <label>{{ t('replace.sourceField') }}</label>
      <n-select
        v-model:value="rep.sourceField.value"
        :options="rep.FIELD_OPTIONS"
        size="small"
        @update:value="rep.syncSourceText()"
      />
    </div>


    <div class="rp-section">
      <div class="rp-section-header">
        <label>{{ t('replace.originalText') }}</label>
        <n-button size="tiny" text @click="rep.syncSourceText()">刷新</n-button>
      </div>
      <div
        ref="sourceTextRef"
        class="rp-source-text"
        @mouseup="onTextSelect"
      >{{ rep.sourceText.value || '—' }}</div>


      <div  class="rp-selection-bar">
        <n-button size="tiny" type="error" @click="handleDelete">
          删除 "{{ selectedText }}"
        </n-button>
        <n-button
          v-if="bracketPattern"
          size="tiny"
          type="warning"
          @click="handleDeleteBracket"
        >
          删除所有{{ bracketPattern.label }}内容
        </n-button>
        <n-input
          v-model:value="replacementText"
          size="tiny"
          placeholder="替换为…"
          style="width: 120px"
        />
        <n-button size="tiny" type="primary" :disabled="!replacementText" @click="handleReplace">替换</n-button>
        <n-button size="tiny" :disabled="!replacementText" @click="handleAddBefore">之前添加</n-button>
        <n-button size="tiny" :disabled="!replacementText" @click="handleAddAfter">之后添加</n-button>
      </div>
    </div>


    <div class="rp-section">
      <div class="rp-section-header">
        <label>{{ t('split.rules') }}</label>
        <div class="rp-section-actions">
          <n-button size="tiny" text @click="rep.addRule()">{{ t('replace.addRule') }}</n-button>
        </div>
      </div>

      <div class="rp-rule-list">
        <div v-for="(rule, i) in rep.rules.value" :key="rule.id" class="rp-rule-row">
          <n-switch
            :value="rule.enabled"
            size="small"
            @update:value="rep.toggleRule(i)"
          />
          <n-input
            :value="editingInputs[rule.id] ?? rep.toFormula(rule)"
            size="tiny"
            :placeholder="t('replace.formulaPlaceholder') || '/正则/g  或  s/查找/替换/g'"
            class="rp-rule-find"
            @update:value="v => { editingInputs[rule.id] = v }"
            @focus="() => { editingInputs[rule.id] = rep.toFormula(rule) }"
            @blur="() => { commitFormula(rule) }"
          />

          <n-button
            size="tiny"
            text
            type="error"
            :disabled="rep.rules.value.length <= 1"
            @click="rep.removeRule(i)"
          >×</n-button>
        </div>
      </div>
    </div>


    <div class="rp-section">
      <n-button size="small" type="primary" @click="rep.executePreview()">{{ t('replace.preview') }}</n-button>

      <div v-if="rep.previewResult.value" class="rp-preview">
        <div class="rp-preview-header">
          <span>{{ t('replace.result') }}</span>
          <span v-if="rep.previewResult.value.changes > 0" class="rp-changes">
            {{ t('replace.changes', { n: rep.previewResult.value.changes }) }}
          </span>
          <span v-else class="rp-no-changes">{{ t('replace.noChanges') }}</span>
        </div>
        <div class="rp-preview-text">{{ rep.previewResult.value.text }}</div>
        <n-button
          v-if="rep.previewResult.value.changes > 0"
          size="small"
          type="primary"
          @click="rep.applyReplace()"
        >{{ t('replace.apply') }}</n-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NSwitch, NInput, NButton, NSelect, NCheckbox } from 'naive-ui'
import { useReplace } from '@/composables/editor/useReplace.js'

const { t } = useI18n()
const rep = useReplace()

onMounted(() => {
  rep.syncSourceText()
})


const sourceTextRef = ref(null)
const selectedText = ref('')
const selectionActive = ref(false)
const replacementText = ref('')
const bracketPattern = ref(null)
const selRange = ref({ start: 0, end: 0 })

const editingInputs = reactive({})

function commitFormula(rule) {
  const raw = editingInputs[rule.id]
  if (raw !== undefined) {
    const p = rep.parseFormula(raw)
    rule.find = p.find
    rule.replace = p.replace
    rule.isRegex = p.isRegex
    delete editingInputs[rule.id]
  }
}

function onTextSelect(e) {
    if (e.target instanceof Element && e.target.closest('.rp-selection-bar')) return

  const sel = window.getSelection()
  const text = sel.toString()
  if (text.length === 0 || sel.rangeCount === 0 || !sourceTextRef.value) {
    return
  }
  const range = sel.getRangeAt(0)
  if (!sourceTextRef.value.contains(range.commonAncestorContainer)) {
    return
  }
  selectedText.value = text
  replacementText.value = ''
  selRange.value = { start: range.startOffset, end: range.endOffset }
  bracketPattern.value = rep.detectBracketPattern(rep.sourceText.value, range.startOffset, range.endOffset)
  selectionActive.value = true
}

function dismissSelection() {
  selectionActive.value = false
  selectedText.value = ''
  replacementText.value = ''
  bracketPattern.value = null
}

function handleDelete() {
  rep.addRuleFromSelection({ text: selectedText.value, action: 'delete', start: selRange.value.start, end: selRange.value.end })
  dismissSelection()
}

function handleDeleteBracket() {
  rep.addRuleFromSelection({ text: selectedText.value, action: 'deleteBracket', start: selRange.value.start, end: selRange.value.end })
  dismissSelection()
}

function handleReplace() {
  if (!replacementText.value) return
  rep.addRuleFromSelection({ text: selectedText.value, action: 'replace', replacement: replacementText.value, start: selRange.value.start, end: selRange.value.end })
  dismissSelection()
}

function handleAddBefore() {
  if (!replacementText.value) return
  rep.addRuleFromSelection({ text: selectedText.value, action: 'addBefore', replacement: replacementText.value, start: selRange.value.start, end: selRange.value.end })
  dismissSelection()
}

function handleAddAfter() {
  if (!replacementText.value) return
  rep.addRuleFromSelection({ text: selectedText.value, action: 'addAfter', replacement: replacementText.value, start: selRange.value.start, end: selRange.value.end })
  dismissSelection()
}
</script>

<style scoped>
.replace-panel {
  width: 460px;
  flex-shrink: 0;
  height: 100%;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.rp-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rp-section label {
  font-size: 11px;
  color: var(--ct-text-2);
  font-weight: 500;
}

.rp-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.rp-section-actions {
  display: flex;
  gap: 4px;
}


.rp-rule-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rp-rule-row {
  display: flex;
  align-items: center;
  gap: 4px;
}

.rp-rule-find {
  flex: 1;
  min-width: 0;
}

.rp-rule-replace {
  flex: 1;
  min-width: 0;
}


.rp-source-text {
  padding: 8px 10px;
  border: 1px solid var(--ct-border);
  border-radius: 6px;
  background: var(--ct-bg-secondary);
  font-size: 12px;
  font-family: 'SF Mono', monospace;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  min-height: 32px;
  user-select: text;
  cursor: text;
}


.rp-selection-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  padding: 6px 8px;
  background: var(--ct-bg);
  border: 1px solid var(--n-color-primary);
  border-radius: 6px;
}


.rp-preview {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  background: var(--ct-bg-secondary);
  border-radius: 6px;
}

.rp-preview-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
}

.rp-changes {
  color: var(--n-color-primary);
  font-weight: 600;
}

.rp-no-changes {
  color: var(--ct-text-2);
}

.rp-preview-text {
  font-size: 12px;
  font-family: 'SF Mono', monospace;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.5;
  padding: 6px 8px;
  background: var(--ct-bg);
  border-radius: 4px;
  max-height: 150px;
  overflow-y: auto;
}
</style>
