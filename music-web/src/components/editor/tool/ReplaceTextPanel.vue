<template>
  <ToolPanelLayout
    :selected-files="selectedFiles"
    :selected-folders="selectedFolders"
    :target-path="targetPath"
  >
    <div class="rp-main">
      <!-- 1. 顶部操作栏 -->
      <div class="rp-topbar">
        <span class="rp-title">{{ t('replace.title') }}</span>
        <n-tag type="warning" size="small" :bordered="false">
          {{ t('replace.batchReplace') }}
        </n-tag>
      </div>

      <!-- 2. 目标字段区 -->
      <div class="rp-section">
        <div class="rp-section-header">
          <span class="rp-section-title">{{ t('replace.targetFields') }}</span>
          <n-tag type="warning" size="small" :bordered="false">
            {{ t('replace.fieldsCount', { n: selectedFields.length }) }}
          </n-tag>
        </div>
        <div class="rp-fields-row">
          <n-tag
            v-for="f in selectedFields"
            :key="f"
            closable
            @close="removeField(f)"
          >
            {{ fieldLabel(f) }}
          </n-tag>
          <n-select
            v-if="availableFields.length > 0"
            v-model:value="selectValue"
            :placeholder="t('replace.addField')"
            :options="availableFields"
            size="small"
            style="width: 140px"
            :consistent-menu-width="false"
            @update:value="addField"
          />
          <n-text v-else depth="3" class="rp-all-selected">{{ t('replace.allFieldsSelected') }}</n-text>
        </div>
        <n-text depth="3" class="rp-hint">{{ t('replace.fieldsHint') }}</n-text>
      </div>

      <!-- 3. 原始文本（操作栏） -->
      <div class="rp-section">
        <div class="rp-section-header">
          <span class="rp-section-title">{{ t('replace.originalText') }}</span>
          <span class="rp-section-sub">{{ t('replace.originalTextHint') }}</span>
        </div>
        <div
          ref="sourceTextRef"
          class="rp-source-text"
          contenteditable="true"
          @mouseup="onTextSelect"
          @keyup="onTextSelect"
          @blur="onTextBlur"
        ></div>

        <!-- 选中文字操作栏 -->
        <div v-if="selectionActive" class="rp-selection-bar">
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

      <!-- 4. 替换规则区 -->
      <div class="rp-section">
        <div class="rp-section-header">
          <span class="rp-section-title">{{ t('split.rules') }}</span>
          <span class="rp-section-sub">{{ t('replace.rulesHint') }}</span>
        </div>
        <div class="rp-rule-list">
          <div v-for="(rule, i) in rules" :key="rule.id" class="rp-rule-row">
            <n-switch
              :value="rule.enabled !== false"
              size="small"
              @update:value="v => rule.enabled = v"
            />
            <n-input
              :value="editingInputs[rule.id] ?? toFormula(rule)"
              size="tiny"
              placeholder="/正则/g  或  s/查找/替换/g"
              class="rp-rule-find"
              @update:value="v => { editingInputs[rule.id] = v }"
              @focus="e => { editingInputs[rule.id] = toFormula(rule) }"
              @blur="e => { commitFormula(rule) }"
            />
            <n-button
              size="tiny"
              text
              type="error"
              @click="removeRule(i)"
            >×</n-button>
          </div>
          <n-button
            dashed
            size="small"
            block
            @click="addRule"
          >{{ t('replace.addRule') }}</n-button>
        </div>
      </div>

      <!-- 5. 处理结果 -->
      <div class="rp-section">
        <n-button
          type="primary"
          block
          :loading="processing"
          :disabled="selectedFields.length === 0 || (!hasTargets && !props.targetPath)"
          @click="handleSubmit"
        >
          {{ processing ? t('tool.processing') : t('tool.startProcessing') }}
        </n-button>

        <n-alert v-if="error" type="error" class="rp-result-alert">{{ error }}</n-alert>
      </div>
    </div>
  </ToolPanelLayout>
</template>

<script setup>
import { computed, reactive, ref, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { NTag, NSelect, NSwitch, NButton, NAlert, NText, NInput, useMessage } from 'naive-ui'
import { runTool } from '@/api/editor/tools.js'
import ToolPanelLayout from '@/components/editor/tool/ToolPanelLayout.vue'

const props = defineProps({
  mode: { type: String, default: 'tool' },
  targetPath: { type: String, default: '' },
  selectedFiles: { type: Array, default: () => [] },
  selectedFolders: { type: Array, default: () => [] },
})

const emit = defineEmits(['done'])

const { t } = useI18n()
const message = useMessage()

const processing = ref(false)
const error = ref(null)

const ALL_FIELDS = [
  { value: 'song.title', labelKey: 'songTitle' },
  { value: 'artist.artistName', labelKey: 'artistName' },
  { value: 'album.albumName', labelKey: 'albumName' },
  { value: 'album.introduction', labelKey: 'albumIntroduction' },
  { value: 'album.company', labelKey: 'albumCompany' },
  { value: 'song.composer', labelKey: 'songComposer' },
  { value: 'song.lyricist', labelKey: 'songLyricist' },
  { value: 'style.styleName', labelKey: 'styleName' },
  { value: 'style.description', labelKey: 'styleDescription' },
  { value: 'lyric.content', labelKey: 'lyricContent' },
  { value: 'artist.country', labelKey: 'artistCountry' },
  { value: 'artist.introduction', labelKey: 'artistIntroduction' },
]

const selectedFields = ref(['song.title'])
const selectValue = ref(null)

const availableFields = computed(() =>
  ALL_FIELDS
    .filter(f => !selectedFields.value.includes(f.value))
    .map(f => ({ label: t('replace.fields.' + f.labelKey), value: f.value })),
)

const hasTargets = computed(
  () => props.selectedFiles.length > 0 || props.selectedFolders.length > 0,
)

function fieldLabel(field) {
  const entry = ALL_FIELDS.find(f => f.value === field)
  return entry ? t('replace.fields.' + entry.labelKey) : field
}

function removeField(field) {
  selectedFields.value = selectedFields.value.filter(f => f !== field)
}

function addField(field) {
  if (field && !selectedFields.value.includes(field)) {
    selectedFields.value = [...selectedFields.value, field]
  }
  selectValue.value = null
}

// ── 原始文本 / 操作栏 ──

const sourceTextRef = ref(null)
const selectedText = ref('')
const selectionActive = ref(false)
const replacementText = ref('')
const bracketPattern = ref(null)
const selRange = ref({ start: 0, end: 0 })

function getSourceText() {
  return sourceTextRef.value?.textContent || ''
}

function onTextSelect(e) {
  if (e.target instanceof Element && e.target.closest('.rp-selection-bar')) return

  setTimeout(() => {
    const sel = window.getSelection()
    const text = sel.toString()
    if (text.length === 0 || sel.rangeCount === 0 || !sourceTextRef.value) {
      selectionActive.value = false
      return
    }
    const range = sel.getRangeAt(0)
    if (!sourceTextRef.value.contains(range.commonAncestorContainer)) {
      selectionActive.value = false
      return
    }
    selectedText.value = text
    replacementText.value = ''
    selRange.value = { start: range.startOffset, end: range.endOffset }
    bracketPattern.value = detectBracketPattern(getSourceText(), range.startOffset, range.endOffset)
    selectionActive.value = true
  }, 10)
}

function onTextBlur() {
  // 延迟清除，让操作栏按钮有机会响应点击
  setTimeout(() => {
    if (!sourceTextRef.value?.contains(document.activeElement)) {
      // 不要在这里清除 selection，让按钮有机会处理
    }
  }, 200)
}

function dismissSelection() {
  selectionActive.value = false
  selectedText.value = ''
  replacementText.value = ''
  bracketPattern.value = null
  window.getSelection().removeAllRanges()
}

// ── 括号检测 ──

const BRACKET_PAIRS = [
  { open: '[', close: ']', label: '方括号', regex: '\\[.*?\\]' },
  { open: '【', close: '】', label: '中文方括号', regex: '【.*?】' },
  { open: '(', close: ')', label: '圆括号', regex: '\\(.*?\\)' },
  { open: '（', close: '）', label: '中文圆括号', regex: '（.*?）' },
  { open: '《', close: '》', label: '书名号', regex: '《.*?》' },
]

function detectBracketPattern(text, selStart, selEnd) {
  for (const pair of BRACKET_PAIRS) {
    let depth = 0
    for (let i = selStart - 1; i >= 0; i--) {
      if (text[i] === pair.close) depth++
      else if (text[i] === pair.open) {
        if (depth === 0) {
          depth = 0
          for (let j = selEnd; j < text.length; j++) {
            if (text[j] === pair.open) depth++
            else if (text[j] === pair.close) {
              if (depth === 0) return { ...pair, start: i, end: j }
              depth--
            }
          }
          break
        }
        depth--
      }
    }
  }
  return null
}

function addRuleFromSelection(sel) {
  const { text, action, replacement } = sel
  const bracket = detectBracketPattern(getSourceText(), sel.start, sel.end)
  let rule

  if (bracket && action === 'deleteBracket') {
    rule = {
      id: generateId(), name: `删除所有${bracket.label}内容`,
      find: bracket.regex, replace: '', isRegex: true, enabled: true,
    }
  } else if (action === 'delete') {
    rule = {
      id: generateId(), name: `删除 "${text}"`,
      find: text, replace: '', isRegex: true, enabled: true,
    }
  } else if (action === 'replace') {
    rule = {
      id: generateId(), name: `"${text}" → "${replacement}"`,
      find: text, replace: replacement, isRegex: true, enabled: true,
    }
  } else if (action === 'addBefore') {
    rule = {
      id: generateId(), name: `在 "${text}" 前添加 "${replacement}"`,
      find: text, replace: replacement + text, isRegex: true, enabled: true,
    }
  } else if (action === 'addAfter') {
    rule = {
      id: generateId(), name: `在 "${text}" 后添加 "${replacement}"`,
      find: text, replace: text + replacement, isRegex: true, enabled: true,
    }
  }
  if (rule) rules.value.push(rule)
  return rule
}

// ── 编辑态缓存：避免击键中间态被 toFormula 错误格式化 ──
const editingInputs = reactive({})

function commitFormula(rule) {
  const raw = editingInputs[rule.id]
  if (raw !== undefined) {
    const p = parseFormula(raw)
    rule.find = p.find
    rule.replace = p.replace
    // 公式格式 (s/.../.../g 或 /.../g) 自动开启正则模式
    rule.isRegex = FORMULA_FULL_RE.test(raw) || FORMULA_SIMPLE_RE.test(raw)
    console.log('[commitFormula] raw:', raw, '→ find:', rule.find, 'replace:', rule.replace, 'isRegex:', rule.isRegex)
    delete editingInputs[rule.id]
  } else {
    console.log('[commitFormula] editingInputs 中无此 rule.id:', rule.id, 'keys:', Object.keys(editingInputs))
  }
}

// ── 公式 ↔ find/replace 转换 ──
function toFormula(rule) {
  if (!rule) return ''
  const f = rule.find || ''
  const r = rule.replace || ''
  if (!f && !r) return ''
  if (r) return `s/${f}/${r}/g`
  return `/${f}/g`
}

const FORMULA_FULL_RE = /^s\/(.*)\/(.*)\/([gim]*)$/
const FORMULA_SIMPLE_RE = /^\/(.*)\/([gim]*)$/

function parseFormula(formula) {
  if (!formula) return { find: '', replace: '' }
  const full = formula.match(FORMULA_FULL_RE)
  if (full) return { find: full[1], replace: full[2] }
  const simple = formula.match(FORMULA_SIMPLE_RE)
  if (simple) return { find: simple[1], replace: '' }
  return { find: formula, replace: '' }
}

function handleDelete() {
  addRuleFromSelection({ text: selectedText.value, action: 'delete', start: selRange.value.start, end: selRange.value.end })
  dismissSelection()
}

function handleDeleteBracket() {
  addRuleFromSelection({ text: selectedText.value, action: 'deleteBracket', start: selRange.value.start, end: selRange.value.end })
  dismissSelection()
}

function handleReplace() {
  if (!replacementText.value) return
  addRuleFromSelection({ text: selectedText.value, action: 'replace', replacement: replacementText.value, start: selRange.value.start, end: selRange.value.end })
  dismissSelection()
}

function handleAddBefore() {
  if (!replacementText.value) return
  addRuleFromSelection({ text: selectedText.value, action: 'addBefore', replacement: replacementText.value, start: selRange.value.start, end: selRange.value.end })
  dismissSelection()
}

function handleAddAfter() {
  if (!replacementText.value) return
  addRuleFromSelection({ text: selectedText.value, action: 'addAfter', replacement: replacementText.value, start: selRange.value.start, end: selRange.value.end })
  dismissSelection()
}

// ── 规则管理 ──

const STORAGE_KEY = 'music-replace-rules'

function generateId() {
  return 'rr_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8)
}

function loadRules() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed) && parsed.length > 0) return parsed
    }
  } catch (_) {}
  return []
}

function saveRules(v) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(v))
}

const rules = ref(loadRules())

watch(rules, saveRules, { deep: true })

function addRule() {
  rules.value.push({
    id: generateId(), name: '', find: '', replace: '', isRegex: false, enabled: true,
  })
}

function removeRule(index) {
  rules.value.splice(index, 1)
}

// ── 提交 ──

async function handleSubmit() {
  processing.value = true
  error.value = null
  try {
    const allTargets = [...props.selectedFiles, ...props.selectedFolders]
    const options = {
      path: props.targetPath,
      files: allTargets,
      'replace-text': {
        fields: selectedFields.value,
        rules: rules.value.filter(r => r.find),
      },
    }
   const res = await runTool('replaceText', options)
    if (res?.pipelineId) {
      message?.success('已提交: ' + res.pipelineId)
      emit('done')
    } else {
      message?.error(res?.error || t('tool.failure'))
    }
  } catch (err) {
    const msg = err?.response?.data?.error || err.message || t('common.error')
    error.value = msg
    message?.error(msg)
  } finally {
    processing.value = false
  }
}
</script>

<style scoped>
.replace-text-panel {
  display: flex;
  gap: 16px;
  overflow: hidden;
}

/* 左侧：文件/目录列表 */
.rp-files-col {
  width: 175px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--ct-border);
  padding-right: 12px;
  max-height: 500px;
}
.rp-files-header {
  font-size: 11px;
  font-weight: 600;
  color: var(--ct-text);
  margin-bottom: 8px;
  flex-shrink: 0;
}
.rp-file-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.rp-file-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-size: 11px;
  padding: 1px 0;
}
.rp-file-idx {
  color: var(--ct-text-3);
  min-width: 18px;
  text-align: right;
  font-size: 10px;
  flex-shrink: 0;
}
.rp-file-name {
  font-family: 'SF Mono', monospace;
  color: var(--ct-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 右侧主内容 */
.rp-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow-y: auto;
}

/* 1. 顶部操作栏 */
.rp-topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--ct-border);
}
.rp-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ct-text);
}

/* section 通用 */
.rp-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.rp-section-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rp-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ct-text);
}
.rp-section-sub {
  font-size: 11px;
  color: var(--ct-text-3);
}

/* 2. 目标字段 */
.rp-fields-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.rp-hint {
  font-size: 12px;
}
.rp-all-selected {
  font-size: 12px;
}

/* 3. 原始文本 */
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
  max-height: 80px;
  overflow-y: auto;
  outline: none;
}
.rp-source-text:empty::before {
  content: '粘贴或输入原始文本，选中文字后可快速生成替换规则';
  color: var(--ct-text-3);
}

/* 选中文字操作栏 */
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

/* 4. 替换规则 */
.rp-rule-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.rp-rule-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.rp-rule-find {
  flex: 1;
  min-width: 0;
}

/* 5. 结果 */
.rp-result-alert {
  margin-top: 4px;
}
</style>
