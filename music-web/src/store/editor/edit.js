/*
 * useEditStore — 元数据编辑状态管理（嵌套结构版）
 *
 * 数据模型：5 个实体分组
 *   song:   { title, fileName, year, language, trackNumber, discNumber,
 *             composer, lyricist, coverPath,
 *             _readonly: { duration, bitrate, sampleRate, channels,
 *                          bitsPerSample, fileSize, fileFormat, filePath }}
 *   album:  { albumName, albumType, albumCover, introduction,
 *             albumYear, company, language }
 *   artist: { artistName, artistCover, introduction, gender, country }
 *   lyric:  { type, content, lrcPath }
 *   style:  { styleName, description }
 *
 * dirtyFields 使用路径表示法："song.title", "album.albumType"
 * setField('song.title', value) → 自动解析点号路径
 * resetGroup('song') → 将整个 song 分组恢复到原始值
 * autoSaveDraft / restoreDraft → localStorage 草稿
 */
import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { toEditMeta } from '@/utils/musicMeta.js'

// ---- 空元数据模板 ----
function emptyMeta() {
  return {
    song: {
      title: '', fileName: '', year: '', language: '',
      trackNumber: 0, discNumber: 0,
      composer: '', lyricist: '', coverPath: '',
      _readonly: {
        duration: 0, bitrate: 0, sampleRate: 0,
        channels: 0, bitsPerSample: 0,
        fileSize: 0, fileFormat: '', filePath: '',
      },
    },
    album: {
      albumName: '', albumType: '', albumCover: '',
      introduction: '', albumYear: 0, company: '', language: '',
    },
    artist: {
      artistName: '', artistCover: '',
      introduction: '', gender: 0, country: '',
    },
    artists: [],
    activeArtistIndex: 0,
    lyric: {
      type: 'NONE', content: '', lrcPath: '',
    },
    style: {
      styleName: '', description: '',
    },
  }
}

// 草稿自动保存间隔（毫秒）
const DRAFT_INTERVAL = 5000

export const useEditStore = defineStore('edit', () => {
  // ============ 状态 ============

  const editingFile = ref(null)
  const originalMeta = ref(emptyMeta())
  const currentMeta = ref(emptyMeta())
  const dirtyFields = ref(new Set())
  const history = ref([])
  const historyIndex = ref(-1)
  const draftTime = ref(null)        // 草稿保存时间
  let draftTimer = null              // 定时器 ID

  // ============ 计算属性 ============

  const isDirty = computed(() => dirtyFields.value.size > 0)
  const canUndo = computed(() => historyIndex.value >= 0)
  const canRedo = computed(() => historyIndex.value < history.value.length - 1)

  /** 按分组统计脏字段数 */
  const dirtyCountByGroup = computed(() => {
    const counts = { song: 0, album: 0, artist: 0, lyric: 0, style: 0 }
    for (const path of dirtyFields.value) {
      const group = path.split('.')[0]
      if (counts[group] !== undefined) counts[group]++
    }
    return counts
  })

  // ============ 工具函数 ============

  /** 按点号路径获取嵌套对象中的值 */
  function getByPath(obj, path) {
    return path.split('.').reduce((o, k) => (o || {})[k], obj)
  }

  /** 按点号路径设置嵌套对象中的值，返回新对象 */
  function setByPath(obj, path, value) {
    const parts = path.split('.')
    const result = { ...obj }
    let current = result
    for (let i = 0; i < parts.length - 1; i++) {
      current[parts[i]] = { ...current[parts[i]] }
      current = current[parts[i]]
    }
    current[parts[parts.length - 1]] = value
    return result
  }

  // ============ 方法 ============

  /**
   * 开始编辑文件（兼容扁平旧调用和嵌套新结构）
   * @param {object} file - 文件对象
   * @param {object} meta - 嵌套元数据 {song, album, artist, lyric, style} 或扁平 {title, artist, ...}
   */
  function startEdit(file, meta) {
    if (isDirty.value) autoSaveDraft()
    editingFile.value = file

    let merged
    if (meta) {
      if (meta.songs) {
        // Jackson 序列化的 MusicMetadata
        merged = toEditMeta(meta)
      } else if (meta.song) {
        // 旧嵌套结构（兼容）
        merged = emptyMeta()
        Object.assign(merged.song, meta.song)
        if (meta.album) Object.assign(merged.album, meta.album)
        if (meta.artist) {
          Object.assign(merged.artist, meta.artist)
          merged.artists = [{ ...merged.artist }]
        }
        if (meta.lyric) Object.assign(merged.lyric, meta.lyric)
        if (meta.style) Object.assign(merged.style, meta.style)
      } else {
        // 扁平旧结构 → 映射到嵌套
        merged = emptyMeta()
        if (meta.title != null) merged.song.title = meta.title
        if (meta.artist != null) {
          merged.artist.artistName = meta.artist
          merged.artists = [{ ...merged.artist }]
        }
        if (meta.album != null) merged.album.albumName = meta.album
        if (meta.year != null) merged.song.year = String(meta.year)
        if (meta.genre != null) merged.style.styleName = meta.genre
        if (meta.fileName != null) merged.song.fileName = meta.fileName
      }
    } else {
      merged = emptyMeta()
    }

    originalMeta.value = merged
    currentMeta.value = JSON.parse(JSON.stringify(merged))
    dirtyFields.value = new Set()
    history.value = []
    historyIndex.value = -1
    draftTime.value = null

    startAutoDraft()
  }

  /**
   * 后台刷新元数据——不重置编辑状态，仅在用户未修改时更新
   * @param {object} file - 文件对象（用于校验是否同一文件）
   * @param {object} meta - 嵌套元数据
   */
  function refreshMeta(file, meta) {
    if (editingFile.value?.id !== file.id) return
    if (isDirty.value) return

    let merged
    if (meta?.songs) {
      merged = toEditMeta(meta)
    } else if (meta?.song) {
      // 旧嵌套结构（兼容）
      merged = emptyMeta()
      Object.assign(merged.song, meta.song)
      if (meta.album) Object.assign(merged.album, meta.album)
      if (meta.artist) {
        Object.assign(merged.artist, meta.artist)
        merged.artists = [{ ...merged.artist }]
      }
      if (meta.lyric) Object.assign(merged.lyric, meta.lyric)
      if (meta.style) Object.assign(merged.style, meta.style)
    } else {
      return
    }

    originalMeta.value = merged
    currentMeta.value = JSON.parse(JSON.stringify(merged))
  }

  /**
   * 修改字段（路径表示法）
   * @param {string} path - "song.title" 或 "album.albumType"
   * @param {any}    value
   */
  function setField(path, value) {
    const oldValue = getByPath(currentMeta.value, path)

    // 排除 _readonly 字段的修改
    if (path.includes('._readonly.')) return
    if (oldValue === value) return

    // 更新 currentMeta
    currentMeta.value = setByPath(currentMeta.value, path, value)

    // 更新 dirtyFields
    const origValue = getByPath(originalMeta.value, path)
    const nextDirty = new Set(dirtyFields.value)
    if (value !== origValue) {
      nextDirty.add(path)
    } else {
      nextDirty.delete(path)
    }
    dirtyFields.value = nextDirty

    // 记录历史
    history.value = history.value.slice(0, historyIndex.value + 1)
    history.value.push({ path, oldValue, newValue: value })
    historyIndex.value = history.value.length - 1
  }

  /** 撤销 */
  function undo() {
    if (!canUndo.value) return
    const step = history.value[historyIndex.value]
    currentMeta.value = setByPath(currentMeta.value, step.path, step.oldValue)
    historyIndex.value--

    const origValue = getByPath(originalMeta.value, step.path)
    const nextDirty = new Set(dirtyFields.value)
    if (step.oldValue === origValue) nextDirty.delete(step.path)
    else nextDirty.add(step.path)
    dirtyFields.value = nextDirty
  }

  /** 重做 */
  function redo() {
    if (!canRedo.value) return
    historyIndex.value++
    const step = history.value[historyIndex.value]
    currentMeta.value = setByPath(currentMeta.value, step.path, step.newValue)

    const nextDirty = new Set(dirtyFields.value)
    nextDirty.add(step.path)
    dirtyFields.value = nextDirty
  }

  /**
   * 重置指定分组的所有字段到原始值
   * @param {'song'|'album'|'artist'|'lyric'|'style'} group
   */
  function resetGroup(group) {
    currentMeta.value = {
      ...currentMeta.value,
      [group]: JSON.parse(JSON.stringify(originalMeta.value[group])),
    }

    const nextDirty = new Set(dirtyFields.value)
    for (const path of dirtyFields.value) {
      if (path.startsWith(`${group}.`)) nextDirty.delete(path)
    }
    dirtyFields.value = nextDirty
  }

  /** 重置所有分组 */
  function resetAll() {
    currentMeta.value = JSON.parse(JSON.stringify(originalMeta.value))
    dirtyFields.value = new Set()
    history.value = []
    historyIndex.value = -1
  }

  /** 切换到指定索引的艺术家 */
  function switchArtist(index) {
    const artists = currentMeta.value.artists
    if (!artists || index < 0 || index >= artists.length) return
    // 保存当前艺术家编辑回数组
    artists[currentMeta.value.activeArtistIndex] = { ...currentMeta.value.artist }
    // 切换
    currentMeta.value.activeArtistIndex = index
    currentMeta.value.artist = { ...artists[index] }
    // 重新计算 artist.* 脏字段
    const nextDirty = new Set(dirtyFields.value)
    for (const path of dirtyFields.value) {
      if (path.startsWith('artist.')) nextDirty.delete(path)
    }
    const origArtist = originalMeta.value.artists?.[index] || {}
    for (const key of ['artistName', 'artistCover', 'introduction', 'gender', 'country']) {
      const curVal = currentMeta.value.artist[key]
      const origVal = origArtist[key]
      if (curVal !== origVal && !(curVal === '' && (origVal == null || origVal === '' || origVal === 0))) {
        nextDirty.add(`artist.${key}`)
      }
    }
    dirtyFields.value = nextDirty
  }

  /** 新增空白艺术家 */
  function addArtist() {
    const newArtist = { artistName: '', artistCover: '', introduction: '', gender: 0, country: '' }
    const artists = currentMeta.value.artists
    artists.push(newArtist)
    switchArtist(artists.length - 1)
  }

  /** 删除指定索引的艺术家（至少保留一个） */
  function removeArtist(index) {
    const artists = currentMeta.value.artists
    if (!artists || artists.length <= 1) return
    artists.splice(index, 1)
    const newIndex = Math.min(index, artists.length - 1)
    if (currentMeta.value.activeArtistIndex >= artists.length) {
      currentMeta.value.activeArtistIndex = newIndex
      currentMeta.value.artist = { ...artists[newIndex] }
    }
    if (currentMeta.value.activeArtistIndex === index) {
      switchArtist(newIndex)
    } else if (currentMeta.value.activeArtistIndex > index) {
      currentMeta.value.activeArtistIndex--
    }
  }

  /** 结束编辑并清空草稿 */
  function resetEdit() {
    editingFile.value = null
    originalMeta.value = emptyMeta()
    currentMeta.value = emptyMeta()
    dirtyFields.value = new Set()
    history.value = []
    historyIndex.value = -1
    draftTime.value = null
    clearDraft()
    stopAutoDraft()
  }

  // ---- 草稿功能 ----

  /**
   * 自动保存草稿到 localStorage
   * 每 DRAFT_INTERVAL ms 自动触发
   */
  function startAutoDraft() {
    stopAutoDraft()
    draftTimer = setInterval(() => {
      if (isDirty.value && editingFile.value) {
        autoSaveDraft()
      }
    }, DRAFT_INTERVAL)
  }

  function stopAutoDraft() {
    if (draftTimer) {
      clearInterval(draftTimer)
      draftTimer = null
    }
  }

  function autoSaveDraft() {
    const key = `editDraft_${editingFile.value?.id || 'unknown'}`
    const draft = {
      fileId: editingFile.value?.id,
      currentMeta: currentMeta.value,
      dirtyFields: [...dirtyFields.value],
      history: history.value,
      historyIndex: historyIndex.value,
      savedAt: Date.now(),
    }
    localStorage.setItem(key, JSON.stringify(draft))
    draftTime.value = new Date()
  }

  /** 恢复草稿 */
  function restoreDraft(fileId) {
    const key = `editDraft_${fileId}`
    const raw = localStorage.getItem(key)
    if (!raw) return false

    try {
      const draft = JSON.parse(raw)
      currentMeta.value = draft.currentMeta
      dirtyFields.value = new Set(draft.dirtyFields)
      history.value = draft.history
      historyIndex.value = draft.historyIndex
      draftTime.value = new Date(draft.savedAt)
      return true
    } catch {
      return false
    }
  }

  /** 清除指定文件的草稿 */
  function clearDraft() {
    if (editingFile.value) {
      localStorage.removeItem(`editDraft_${editingFile.value.id}`)
    }
  }

  // ============ 导出 ============
  return {
    editingFile, originalMeta, currentMeta, dirtyFields,
    history, historyIndex, draftTime,
    isDirty, canUndo, canRedo, dirtyCountByGroup,
    startEdit, refreshMeta, setField, undo, redo,
    resetGroup, resetAll, resetEdit,
    switchArtist, addArtist, removeArtist,
    startAutoDraft, stopAutoDraft,
    autoSaveDraft, restoreDraft, clearDraft,
  }
})
