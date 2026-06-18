import { ref, watch } from 'vue'
import { useEditStore } from '@/store/editor/edit.js'

const STORAGE_KEY = 'music-split-rules'

function generateId() {
  return 'sr_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8)
}

const FIELD_ALIASES = {
  '标题': 'title', 'title': 'title',
  '艺术家': 'artist', '歌手': 'artist', 'artist': 'artist',
  '专辑': 'album', 'album': 'album',
  '音轨号': 'tracknumber', '序号': 'tracknumber', 'tracknumber': 'tracknumber', 'track': 'tracknumber',
  '碟号': 'discnumber', 'discnumber': 'discnumber', 'disc': 'discnumber',
  '年份': 'year', 'year': 'year',
  '风格': 'genre', '流派': 'genre', 'genre': 'genre',
  '语言': 'language', 'language': 'language',
  '作曲家': 'composer', 'composer': 'composer',
  '作词家': 'lyricist', 'lyricist': 'lyricist',
  '国家': 'country', 'country': 'country',
  '专辑年份': 'albumyear', 'albumyear': 'albumyear',
}

function escapeRegex(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function parseTemplate(template) {
  if (!template || !template.includes('{')) return null
  const groups = {}
  let groupIdx = 0
  const parts = []
  const seen = new Set()
    const re = /\{([^}]+)\}|([^{]+)/g
  let m
  while ((m = re.exec(template)) !== null) {
    if (m[1] !== undefined) {
            const rawName = m[1].trim()
      const key = FIELD_ALIASES[rawName]
      if (!key) return null
      groupIdx++
      groups[groupIdx] = key
      seen.add(rawName)
      parts.push('(.+?)')
    } else {
            const sep = m[2]
      if (!sep) continue
            if (/^\s+$/.test(sep)) {
        parts.push('\\s+')
      } else {
                parts.push('\\s*' + escapeRegex(sep.trim()) + '\\s*')
      }
    }
  }
  if (groupIdx === 0) return null
  const pattern = '^' + parts.join('') + '$'
    const name = Array.from(seen).join(' - ')
  return { pattern, groups, name }
}

const DEFAULT_RULES = [
  { id: 'sr_default_1', name: '序号 + 标题', template: '{序号}. {标题}', pattern: '^(\\d+)\\.\\s*(.+)$', groups: { 1: 'tracknumber', 2: 'title' }, enabled: true, notes: '' },
  { id: 'sr_default_2', name: '序号 标题', template: '{序号} {标题}', pattern: '^(\\d+)\\s+(.+)$', groups: { 1: 'tracknumber', 2: 'title' }, enabled: true, notes: '' },
  { id: 'sr_default_3', name: '标题 - 艺术家 - 专辑', template: '{标题} - {艺术家} - {专辑}', pattern: '^(.+?)\\s*-\\s*(.+?)\\s*-\\s*(.+?)$', groups: { 1: 'title', 2: 'artist', 3: 'album' }, enabled: true, notes: '' },
  { id: 'sr_default_4', name: '标题 - 艺术家', template: '{标题} - {艺术家}', pattern: '^(.+?)\\s*-\\s*(.+?)$', groups: { 1: 'title', 2: 'artist' }, enabled: true, notes: '' },
  { id: 'sr_default_5', name: '艺术家 - 标题', template: '{艺术家} - {标题}', pattern: '^(.+?)\\s*-\\s*(.+?)$', groups: { 1: 'artist', 2: 'title' }, enabled: false, notes: '' },
  { id: 'sr_default_6', name: '序号. 标题 - 艺术家', template: '{序号}. {标题} - {艺术家}', pattern: '^(\\d+)\\.\\s*(.+?)\\s*-\\s*(.+?)$', groups: { 1: 'tracknumber', 2: 'title', 3: 'artist' }, enabled: false, notes: '' },
]

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

const FILL_MODE_STORAGE_KEY = 'music-split-fillMode'

function loadFillMode() {
  try {
    const v = localStorage.getItem(FILL_MODE_STORAGE_KEY)
    if (v === 'overwrite' || v === 'gapFill') return v
  } catch (_) {}
  return 'gapFill'
}

function saveFillMode(v) {
  localStorage.setItem(FILL_MODE_STORAGE_KEY, v)
}

export function useSplit() {
  const editStore = useEditStore()
  const rules = ref(loadRules())
  const activeRuleIndex = ref(0)
  const fillMode = ref(loadFillMode())
  const sampleText = ref('')
  const previewResult = ref(null)
  const previewError = ref(null)

  watch(rules, (v) => saveRules(v), { deep: true })
  watch(fillMode, (v) => saveFillMode(v))

    function useCurrentFileName() {
    const fn = editStore.currentMeta?.song?.fileName || editStore.editingFile?.fileName || editStore.editingFile?.name || ''
    if (fn) sampleText.value = fn
  }

    function executePreview() {
    previewError.value = null
    const rule = rules.value[activeRuleIndex.value]
    if (!rule || !sampleText.value) {
      previewResult.value = null
      return
    }
    try {
            let text = sampleText.value
      const dot = text.lastIndexOf('.')
      if (dot > 0) text = text.substring(0, dot)

      const regex = new RegExp(rule.pattern)
      const match = regex.exec(text)
      if (!match) {
        previewResult.value = { matched: false }
        return
      }
      const fields = {}
      const groups = rule.groups || {}
      for (const [idx, fieldName] of Object.entries(groups)) {
        const gi = parseInt(idx, 10)
        if (gi <= match.length - 1 && match[gi]) {
          fields[fieldName] = match[gi].trim()
        }
      }
      previewResult.value = { matched: Object.keys(fields).length > 0, fields }
    } catch (e) {
      previewError.value = e.message
      previewResult.value = null
    }
  }

    const FIELD_PATH_MAP = {
    title: 'song.title',
    year: 'song.year',
    language: 'song.language',
    tracknumber: 'song.trackNumber',
    discnumber: 'song.discNumber',
    composer: 'song.composer',
    lyricist: 'song.lyricist',
    album: 'album.albumName',
    albumyear: 'album.albumYear',
    artist: 'artist.artistName',
    country: 'artist.country',
    genre: 'style.styleName',
  }

  function applyFields() {
    if (!previewResult.value?.matched || !previewResult.value.fields) return
    const fields = previewResult.value.fields
    const isOverwrite = fillMode.value === 'overwrite'
    for (const [key, path] of Object.entries(FIELD_PATH_MAP)) {
      const val = fields[key]
      if (val) {
        const cur = path.split('.').reduce((o, k) => (o || {})[k], editStore.currentMeta)
                if (isOverwrite || cur == null || cur === '' || cur === 0) {
          if ((key === 'tracknumber' || key === 'discnumber') && !isNaN(parseInt(val, 10))) {
            editStore.setField(path, parseInt(val, 10))
          } else {
            editStore.setField(path, val)
          }
        }
      }
    }
  }

    function addRule() {
    const r = { id: generateId(), name: '新规则', template: '{标题}', pattern: '^(.+)$', groups: { 1: 'title' }, enabled: true, notes: '' }
    rules.value.push(r)
    activeRuleIndex.value = rules.value.length - 1
  }

    function setTemplate(index, template) {
    const rule = rules.value[index]
    if (!rule) return false
    rule.template = template
    const parsed = parseTemplate(template)
    if (parsed) {
      rule.pattern = parsed.pattern
      rule.groups = parsed.groups
      if (!rule.name || rule.name === '新规则') rule.name = parsed.name
      return true
    }
    return false
  }

  function removeRule(index) {
    if (rules.value.length <= 1) return
    rules.value.splice(index, 1)
    if (activeRuleIndex.value >= rules.value.length) {
      activeRuleIndex.value = rules.value.length - 1
    }
  }

  function resetToDefault() {
    rules.value = JSON.parse(JSON.stringify(DEFAULT_RULES))
    activeRuleIndex.value = 0
  }

  function toggleRule(index) {
    rules.value[index].enabled = !rules.value[index].enabled
  }

  return {
    rules,
    activeRuleIndex,
    fillMode,
    sampleText,
    previewResult,
    previewError,
    useCurrentFileName,
    executePreview,
    applyFields,
    addRule,
    removeRule,
    resetToDefault,
    toggleRule,
    setTemplate,
  }
}

export { parseTemplate, FIELD_ALIASES }
