

import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { toEditMeta } from '@/utils/musicMeta.js'

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

const DRAFT_INTERVAL = 5000

export const useEditStore = defineStore('edit', () => {

  const editingFile = ref(null)
  const originalMeta = ref(emptyMeta())
  const currentMeta = ref(emptyMeta())
  const dirtyFields = ref(new Set())
  const history = ref([])
  const historyIndex = ref(-1)
  const draftTime = ref(null)
  let draftTimer = null


  const isDirty = computed(() => dirtyFields.value.size > 0)
  const canUndo = computed(() => historyIndex.value >= 0)
  const canRedo = computed(() => historyIndex.value < history.value.length - 1)


  const dirtyCountByGroup = computed(() => {
    const counts = { song: 0, album: 0, artist: 0, lyric: 0, style: 0 }
    for (const path of dirtyFields.value) {
      const group = path.split('.')[0]
      if (counts[group] !== undefined) counts[group]++
    }
    return counts
  })


  function getByPath(obj, path) {
    return path.split('.').reduce((o, k) => (o || {})[k], obj)
  }


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


  function startEdit(file, meta) {
    if (isDirty.value) autoSaveDraft()
    editingFile.value = file

    let merged
    if (meta) {
      if (meta.songs) {
                merged = toEditMeta(meta)
      } else if (meta.song) {
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


  function refreshMeta(file, meta) {
    if (editingFile.value?.id !== file.id) return
    if (isDirty.value) return

    let merged
    if (meta?.songs) {
      merged = toEditMeta(meta)
    } else if (meta?.song) {
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


  function setField(path, value) {
    const oldValue = getByPath(currentMeta.value, path)

        if (path.includes('._readonly.')) return
    if (oldValue === value) return

        currentMeta.value = setByPath(currentMeta.value, path, value)

        const origValue = getByPath(originalMeta.value, path)
    const nextDirty = new Set(dirtyFields.value)
    if (value !== origValue) {
      nextDirty.add(path)
    } else {
      nextDirty.delete(path)
    }
    dirtyFields.value = nextDirty

        history.value = history.value.slice(0, historyIndex.value + 1)
    history.value.push({ path, oldValue, newValue: value })
    historyIndex.value = history.value.length - 1
  }


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


  function redo() {
    if (!canRedo.value) return
    historyIndex.value++
    const step = history.value[historyIndex.value]
    currentMeta.value = setByPath(currentMeta.value, step.path, step.newValue)

    const nextDirty = new Set(dirtyFields.value)
    nextDirty.add(step.path)
    dirtyFields.value = nextDirty
  }


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


  function resetAll() {
    currentMeta.value = JSON.parse(JSON.stringify(originalMeta.value))
    dirtyFields.value = new Set()
    history.value = []
    historyIndex.value = -1
  }


  function switchArtist(index) {
    const artists = currentMeta.value.artists
    if (!artists || index < 0 || index >= artists.length) return
        artists[currentMeta.value.activeArtistIndex] = { ...currentMeta.value.artist }
        currentMeta.value.activeArtistIndex = index
    currentMeta.value.artist = { ...artists[index] }
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


  function addArtist() {
    const newArtist = { artistName: '', artistCover: '', introduction: '', gender: 0, country: '' }
    const artists = currentMeta.value.artists
    artists.push(newArtist)
    switchArtist(artists.length - 1)
  }


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


  function clearDraft() {
    if (editingFile.value) {
      localStorage.removeItem(`editDraft_${editingFile.value.id}`)
    }
  }

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
