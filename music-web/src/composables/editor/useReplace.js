import { ref, watch } from 'vue'
import { useEditStore } from '@/store/editor/edit.js'

const STORAGE_KEY = 'music-replace-rules'

function generateId() {
  return 'rr_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8)
}

const DEFAULT_RULES = []

function loadRules() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed) && parsed.length > 0) return parsed
    }
  } catch (_) {}
  return JSON.parse(JSON.stringify(DEFAULT_RULES))
}

function saveRules(rules) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(rules))
}

// 可选的目标字段
const FIELD_OPTIONS = [
  { label: '歌曲标题 (song.title)', value: 'song.title' },
  { label: '艺术家 (artist.artistName)', value: 'artist.artistName' },
  { label: '专辑名 (album.albumName)', value: 'album.albumName' },
  { label: '专辑简介 (album.introduction)', value: 'album.introduction' },
  { label: '发行商 (album.company)', value: 'album.company' },
  { label: '作曲家 (song.composer)', value: 'song.composer' },
  { label: '作词家 (song.lyricist)', value: 'song.lyricist' },
  { label: '风格 (style.styleName)', value: 'style.styleName' },
  { label: '风格描述 (style.description)', value: 'style.description' },
  { label: '歌词 (lyric.content)', value: 'lyric.content' },
  { label: '国家/地区 (artist.country)', value: 'artist.country' },
  { label: '艺术家简介 (artist.introduction)', value: 'artist.introduction' },
]

function getCurrentFieldValue(editStore, fieldPath) {
  return fieldPath.split('.').reduce((o, k) => (o || {})[k], editStore.currentMeta) || ''
}

export function useReplace() {
  const editStore = useEditStore()
  const rules = ref(loadRules())
  const sourceField = ref('song.title')
  const sourceText = ref('')
  const previewResult = ref(null)   // { text: '...', changes: 3 }

  watch(rules, (v) => saveRules(v), { deep: true })

  // 切换目标字段时同步当前值
  function syncSourceText() {
    sourceText.value = getCurrentFieldValue(editStore, sourceField.value)
  }

  // 执行预览
  function executePreview() {
    if (!sourceText.value) {
      previewResult.value = null
      return
    }
    let text = sourceText.value
    let changes = 0
    for (const rule of rules.value) {
      if (!rule.enabled) continue
      try {
        const before = text
        text = text.replace(new RegExp(rule.find, 'g'), rule.replace)
        if (text !== before) changes++
      } catch (_) {}
    }
    previewResult.value = { text, changes }
  }

  // 应用替换结果
  function applyReplace() {
    if (!previewResult.value) return
    editStore.setField(sourceField.value, previewResult.value.text)
  }

  // ── 括号智能检测 ──

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
    const bracket = detectBracketPattern(sourceText.value, sel.start, sel.end)
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

  // 规则管理
  function addRule() {
    rules.value.push({ id: generateId(), name: '', find: '', replace: '', isRegex: true, enabled: true })
  }

  function removeRule(index) {
    if (rules.value.length <= 1) return
    rules.value.splice(index, 1)
  }

  function resetToDefault() {
    rules.value = JSON.parse(JSON.stringify(DEFAULT_RULES))
  }

  function toggleRule(index) {
    rules.value[index].enabled = !rules.value[index].enabled
  }

  // ── 公式 ↔ find/replace 转换 ──
  // 显示格式: s/查找/替换/g 或 /查找/g（替换为空时）
  function toFormula(rule) {
    if (!rule) return ''
    const f = rule.find || ''
    const r = rule.replace || ''
    if (!f && !r) return ''
    if (r) return `s/${f}/${r}/g`
    return `/${f}/g`
  }

  function parseFormula(formula) {
    if (!formula) return { find: '', replace: '', isRegex: false }
    // 匹配 s/pattern/replacement/flags 或 /pattern/flags
    const full = formula.match(/^s\/(.*?)(?<!\\)\/(.*?)(?<!\\)\/([gim]*)$/)
    if (full) {
      return { find: full[1], replace: full[2], isRegex: true }
    }
    const simple = formula.match(/^\/(.*?)(?<!\\)\/([gim]*)$/)
    if (simple) {
      return { find: simple[1], replace: '', isRegex: true }
    }
    // 不匹配格式时当作纯正则
    return { find: formula, replace: '', isRegex: true }
  }

  return {
    rules,
    sourceField,
    sourceText,
    previewResult,
    FIELD_OPTIONS,
    syncSourceText,
    executePreview,
    applyReplace,
    detectBracketPattern,
    addRuleFromSelection,
    addRule,
    removeRule,
    resetToDefault,
    toggleRule,
    toFormula,
    parseFormula,
  }
}
