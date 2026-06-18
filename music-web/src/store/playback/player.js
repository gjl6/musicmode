

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import i18n from '@/i18n/index.js'
import { streamUrl } from '@/api/playback/streaming.js'
import { subsonicStar, subsonicUnstar, subsonicGetStarred, subsonicGetLyricsBySongId, subsonicScrobble } from '@/api/playback/subsonic.js'


export const PlayMode = Object.freeze({
  NORMAL: 'normal',
  REPEAT_ALL: 'repeatAll',
  REPEAT_ONE: 'repeatOne',
  SHUFFLE: 'shuffle',
})


const MODE_ORDER = [PlayMode.NORMAL, PlayMode.REPEAT_ALL, PlayMode.REPEAT_ONE, PlayMode.SHUFFLE]


let audio = null
function getAudio() {
  if (!audio) {
    audio = new Audio()
    audio.preload = 'auto'
  }
  return audio
}

export const usePlayerStore = defineStore('playbackPlayer', () => {

  const queue = ref([])
  const currentIndex = ref(-1)
  const playMode = ref(PlayMode.NORMAL)
  const isMinimized = ref(false)

  const isPlaying = ref(false)
  const currentTime = ref(0)
  const duration = ref(0)
  const volume = ref(0.8)
  const muted = ref(false)


  const favoriteIds = ref(new Set())

  const favoritePlaylistIds = ref(new Set())

  const favoriteAlbumIds = ref(new Set())

  const favoriteArtistIds = ref(new Set())
  const favoriteLoaded = ref(false)


  async function loadFavoriteIds() {
    if (favoriteLoaded.value) return
    try {
      const res = await subsonicGetStarred()
      const starred = res?.['subsonic-response']?.starred2
      const songs = starred?.song || []
      if (songs.length > 0) {
        favoriteIds.value = new Set(songs.map(s => Number(s.id)).filter(Boolean))
      }
            const playlists = starred?.playlist || []
      if (playlists.length > 0) {
        favoritePlaylistIds.value = new Set(playlists.map(p => Number(p.id)).filter(Boolean))
      }
            const albums = starred?.album || []
      if (albums.length > 0) {
        favoriteAlbumIds.value = new Set(albums.map(a => Number(a.id)).filter(Boolean))
      }
            const artists = starred?.artist || []
      if (artists.length > 0) {
        favoriteArtistIds.value = new Set(artists.map(a => Number(a.id)).filter(Boolean))
      }
      favoriteLoaded.value = true
    } catch (err) {
      console.warn('[Player] 加载收藏列表失败:', err?.message || err)
    }
  }


  async function starSong(id) {
    if (id == null) return
    const numId = Number(id)
    await subsonicStar(numId, 'song')
    favoriteIds.value.add(numId)
    favoriteIds.value = new Set(favoriteIds.value)
  }


  async function unstarSong(id) {
    if (id == null) return
    const numId = Number(id)
    await subsonicUnstar(numId, 'song')
    favoriteIds.value.delete(numId)
    favoriteIds.value = new Set(favoriteIds.value)
  }


  function isSongStarred(id) {
    return id != null ? favoriteIds.value.has(Number(id)) : false
  }


  async function starPlaylist(id) {
    if (id == null) return
    const numId = Number(id)
    await subsonicStar(numId, 'playlist')
    favoritePlaylistIds.value.add(numId)
    favoritePlaylistIds.value = new Set(favoritePlaylistIds.value)
  }


  async function unstarPlaylist(id) {
    if (id == null) return
    const numId = Number(id)
    await subsonicUnstar(numId, 'playlist')
    favoritePlaylistIds.value.delete(numId)
    favoritePlaylistIds.value = new Set(favoritePlaylistIds.value)
  }


  function isPlaylistStarred(id) {
    return id != null ? favoritePlaylistIds.value.has(Number(id)) : false
  }


  async function starAlbum(id) {
    if (id == null) return
    const numId = Number(id)
    await subsonicStar(numId, 'album')
    favoriteAlbumIds.value.add(numId)
    favoriteAlbumIds.value = new Set(favoriteAlbumIds.value)
  }


  async function unstarAlbum(id) {
    if (id == null) return
    const numId = Number(id)
    await subsonicUnstar(numId, 'album')
    favoriteAlbumIds.value.delete(numId)
    favoriteAlbumIds.value = new Set(favoriteAlbumIds.value)
  }


  function isAlbumStarred(id) {
    return id != null ? favoriteAlbumIds.value.has(Number(id)) : false
  }


  async function starArtist(id) {
    if (id == null) return
    const numId = Number(id)
    await subsonicStar(numId, 'artist')
    favoriteArtistIds.value.add(numId)
    favoriteArtistIds.value = new Set(favoriteArtistIds.value)
  }


  async function unstarArtist(id) {
    if (id == null) return
    const numId = Number(id)
    await subsonicUnstar(numId, 'artist')
    favoriteArtistIds.value.delete(numId)
    favoriteArtistIds.value = new Set(favoriteArtistIds.value)
  }


  function isArtistStarred(id) {
    return id != null ? favoriteArtistIds.value.has(Number(id)) : false
  }

    let _playStartTime = 0
  let _seekAccumulated = 0
  let _scrobbled = false

  function _resetPlayTimer() {
    _playStartTime = Date.now()
    _seekAccumulated = 0
    _scrobbled = false
  }

  function _onSeeked() {
    if (el && !el.paused) {
      _seekAccumulated += Date.now() - _playStartTime
      _playStartTime = Date.now()
    }
  }

  function _shouldScrobble() {
    const totalMs = _seekAccumulated + (Date.now() - _playStartTime)
    const duraMs = (current.value?.duration || 0) * 1000
    if (duraMs <= 0) return false
    if (duraMs < 60000)  return totalMs >= duraMs * 0.8
    if (duraMs < 480000) return totalMs >= duraMs * 0.5 || totalMs >= 120000
    return totalMs >= 240000
  }


  function _tryHalfScrobble() {
    if (_scrobbled || !current.value?.id) return
    const dur = duration.value
    if (dur <= 0) return
    if (currentTime.value >= dur * 0.5) {
      _scrobbled = true
      subsonicScrobble(current.value.id, true).catch(() => {})
    }
  }


  const lyrics = ref([])

  const lyricsSynced = ref(false)

  const lyricsLoading = ref(false)


  const current = computed(() => {
    if (currentIndex.value >= 0 && currentIndex.value < queue.value.length) {
      return queue.value[currentIndex.value]
    }
    return null
  })

  const hasTrack = computed(() => current.value !== null)

  const progress = computed(() =>
    duration.value > 0 ? currentTime.value / duration.value : 0,
  )

  const displayTitle = computed(() =>
    current.value?.title || current.value?.name || '—'
  )

  const displayArtist = computed(() =>
    current.value?.artist || '—'
  )


  const hasPrev = computed(() => {
    if (queue.value.length <= 1) return false
    if (playMode.value === PlayMode.SHUFFLE) return queue.value.length > 1
    return currentIndex.value > 0
  })


  const hasNext = computed(() => {
    if (queue.value.length === 0) return false
    if (playMode.value === PlayMode.REPEAT_ALL || playMode.value === PlayMode.REPEAT_ONE) return true
    if (playMode.value === PlayMode.SHUFFLE) return queue.value.length > 1
    return currentIndex.value < queue.value.length - 1
  })


  const isFavorited = computed(() => {
    const id = current.value?.id
    return id != null ? favoriteIds.value.has(Number(id)) : false
  })


  const playModeLabel = computed(() => {
    switch (playMode.value) {
      case PlayMode.REPEAT_ALL: return i18n.global.t('player.repeatAll')
      case PlayMode.REPEAT_ONE: return i18n.global.t('player.repeatOne')
      case PlayMode.SHUFFLE: return i18n.global.t('player.shuffle')
      default: return i18n.global.t('player.normal')
    }
  })


  const hasLyrics = computed(() => lyrics.value.length > 0)


  const currentLyricIndex = computed(() => {
    const lines = lyrics.value
    if (lines.length === 0) return -1
    if (!lyricsSynced.value) return 0
    const t = currentTime.value * 1000
        let lo = 0, hi = lines.length - 1, idx = -1
    while (lo <= hi) {
      const mid = (lo + hi) >> 1
      if ((lines[mid].start || 0) <= t) {
        idx = mid
        lo = mid + 1
      } else {
        hi = mid - 1
      }
    }
    return idx
  })


  function getShuffledIndices() {
    const len = queue.value.length
    const indices = Array.from({ length: len }, (_, i) => i)
        for (let i = len - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1))
      ;[indices[i], indices[j]] = [indices[j], indices[i]]
    }
    return indices
  }


  let _shuffledIndices = null
  let _shufflePos = -1

  function getNextIndex() {
    const len = queue.value.length
    if (len === 0) return -1

    switch (playMode.value) {
      case PlayMode.REPEAT_ONE:
        return currentIndex.value

      case PlayMode.SHUFFLE: {
        if (!_shuffledIndices || _shuffledIndices.length !== len) {
          _shuffledIndices = getShuffledIndices()
          _shufflePos = -1
        }
        _shufflePos++
        if (_shufflePos >= _shuffledIndices.length) {
                    _shuffledIndices = getShuffledIndices()
          _shufflePos = 0
        }
        return _shuffledIndices[_shufflePos]
      }

      case PlayMode.REPEAT_ALL: {
        const next = currentIndex.value + 1
        return next >= len ? 0 : next
      }

      default:
        const next = currentIndex.value + 1
        return next < len ? next : -1
    }
  }

  function getPrevIndex() {
    const len = queue.value.length
    if (len === 0) return -1

    switch (playMode.value) {
      case PlayMode.REPEAT_ONE:
        return currentIndex.value

      case PlayMode.SHUFFLE: {
        if (!_shuffledIndices || _shuffledIndices.length !== len) {
          _shuffledIndices = getShuffledIndices()
          _shufflePos = 0
        }
        _shufflePos = Math.max(0, _shufflePos - 1)
        return _shuffledIndices[_shufflePos]
      }

      case PlayMode.REPEAT_ALL: {
        const prev = currentIndex.value - 1
        return prev < 0 ? len - 1 : prev
      }

      default:
        const prev = currentIndex.value - 1
        return prev >= 0 ? prev : 0
    }
  }


  function playIndex(index) {
    if (index < 0 || index >= queue.value.length) return
    currentIndex.value = index
    const song = queue.value[index]
    if (!song?.path) return
    el.src = streamUrl(song.path)
    el.volume = muted.value ? 0 : volume.value
    isPlaying.value = true
    _resetPlayTimer()
    el.play().catch((err) => {
      console.error('[Playback Player] 播放失败:', err)
      window.$message?.warning(i18n.global.t('player.playError'))
      isPlaying.value = false
    })
    fetchLyrics()
  }


  const el = getAudio()

  el.ontimeupdate = () => {
    currentTime.value = el.currentTime
    _tryHalfScrobble()
  }

  el.onloadedmetadata = () => {
    if (isFinite(el.duration)) duration.value = el.duration
  }

  el.onplay = () => { isPlaying.value = true }
  el.onpause = () => { isPlaying.value = false }

  el.onended = () => {
        if (!_scrobbled && _shouldScrobble() && current.value?.id != null) {
      _scrobbled = true
      subsonicScrobble(current.value.id, true).catch(() => {})
    }
    const nextIdx = getNextIndex()
    if (nextIdx >= 0) {
      playIndex(nextIdx)
    } else {
            isPlaying.value = false
      currentTime.value = 0
    }
  }

  el.onseeked = () => { _onSeeked() }

  el.onerror = () => {
        if (!current.value) return
    console.error('[Playback Player] 音频加载失败:', el.error?.message)
    window.$message?.warning(i18n.global.t('player.loadError'))
    isPlaying.value = false
        const nextIdx = getNextIndex()
    if (nextIdx >= 0 && nextIdx !== currentIndex.value) {
      setTimeout(() => playIndex(nextIdx), 500)
    }
  }


  function play(song) {
    if (!song) return
    const path = song.path || ''
    if (!path) {
      window.$message?.warning(i18n.global.t('player.noFilePath'))
      return
    }

        const existingIdx = queue.value.findIndex(
      (s) => s.path === path || (song.id != null && s.id === song.id)
    )

    if (existingIdx >= 0) {
      currentIndex.value = existingIdx
    } else {
      queue.value.push({ ...song })
      currentIndex.value = queue.value.length - 1
    }
        _shuffledIndices = null
    _shufflePos = -1

    el.src = streamUrl(path)
    el.volume = muted.value ? 0 : volume.value
    isPlaying.value = true
    _resetPlayTimer()
    el.play().catch((err) => {
      console.error('[Playback Player] 播放失败:', err)
      window.$message?.warning(i18n.global.t('player.playError'))
      isPlaying.value = false
    })
    fetchLyrics()
  }


  function playAll(songs, startIndex = 0) {
    if (!songs || songs.length === 0) return
    queue.value = songs.map((s) => ({ ...s }))
    _shuffledIndices = null
    _shufflePos = -1

    if (playMode.value === PlayMode.SHUFFLE) {
      _shuffledIndices = getShuffledIndices()
      _shufflePos = 0
      playIndex(_shuffledIndices[0])
    } else {
      playIndex(Math.max(0, Math.min(startIndex, songs.length - 1)))
    }
  }


  function addToQueue(songs) {
    if (!songs || songs.length === 0) return
    queue.value.push(...songs.map((s) => ({ ...s })))
  }


  function removeFromQueue(index) {
    if (index < 0 || index >= queue.value.length) return
    queue.value.splice(index, 1)
    if (index < currentIndex.value) {
      currentIndex.value--
    } else if (index === currentIndex.value) {
            if (queue.value.length === 0) {
        stop()
      } else {
        const next = Math.min(currentIndex.value, queue.value.length - 1)
        playIndex(next)
      }
    }
  }

  function clearQueue() {
    stop()
    queue.value = []
  }


  let _lyricsFetchingForId = null


  async function fetchLyrics() {
    lyrics.value = []
    lyricsSynced.value = false
    const song = current.value
    if (!song || song.id == null) return
    const songId = song.id
    _lyricsFetchingForId = songId
    lyricsLoading.value = true
    try {
      const res = await subsonicGetLyricsBySongId(songId)
            if (_lyricsFetchingForId !== songId) return
      const structuredLyrics = res?.['subsonic-response']?.lyricsList?.structuredLyrics
      if (structuredLyrics && structuredLyrics.length > 0) {
        const sl = structuredLyrics[0]
        lyricsSynced.value = sl.synced === true
        lyrics.value = sl.line || []
      }
    } catch (err) {
      console.warn('[Playback] 歌词获取失败:', err?.message || err)
    } finally {
      if (_lyricsFetchingForId === songId) {
        lyricsLoading.value = false
      }
    }
  }


  function clearLyrics() {
    lyrics.value = []
    lyricsSynced.value = false
  }

  function togglePlay() {
    if (!current.value) return
    if (el.paused) {
      el.play().catch(() => {})
    } else {
      el.pause()
    }
  }

  function stop() {
    el.pause()
    el.removeAttribute('src')
    queue.value = []
    currentIndex.value = -1
    isPlaying.value = false
    currentTime.value = 0
    duration.value = 0
    _shuffledIndices = null
    _shufflePos = -1
    clearLyrics()
  }


  function playPrev() {
    if (queue.value.length === 0) return
        if (currentTime.value > 3) {
      el.currentTime = 0
      currentTime.value = 0
      el.play().catch(() => {})
      return
    }
    const idx = getPrevIndex()
    if (idx >= 0 && idx !== currentIndex.value) playIndex(idx)
  }


  function playNext() {
    const idx = getNextIndex()
    if (idx >= 0) playIndex(idx)
  }


  function togglePlayMode() {
    const currentIdx = MODE_ORDER.indexOf(playMode.value)
    const nextIdx = (currentIdx + 1) % MODE_ORDER.length
    playMode.value = MODE_ORDER[nextIdx]
        if (playMode.value === PlayMode.SHUFFLE && queue.value.length > 0) {
      _shuffledIndices = getShuffledIndices()
      _shufflePos = _shuffledIndices.indexOf(currentIndex.value)
      if (_shufflePos < 0) _shufflePos = 0
    }
  }


  async function toggleFavorite() {
    const song = current.value
    if (!song) return
    const id = song.id
    if (id == null) return
    const wasFavorited = isSongStarred(id)
    try {
      if (wasFavorited) {
        await unstarSong(id)
      } else {
        await starSong(id)
      }
      window.$message?.success(
        wasFavorited
          ? i18n.global.t('player.unfavorited')
          : i18n.global.t('player.favorited')
      )
    } catch {
      window.$message?.warning(i18n.global.t('player.favoriteFailed'))
    }
  }


  function setVolume(v) {
    volume.value = Math.max(0, Math.min(1, v))
    el.volume = volume.value
    if (v > 0 && muted.value) muted.value = false
  }

  function toggleMute() {
    muted.value = !muted.value
    el.volume = muted.value ? 0 : volume.value
  }


  function seek(time) {
    const t = Math.max(0, Math.min(duration.value || 0, time))
    el.currentTime = t
    currentTime.value = t
  }

  return {
        queue, currentIndex, playMode, isMinimized, favoriteIds,
    favoritePlaylistIds, favoriteAlbumIds,
    favoriteLoaded,
    isPlaying, currentTime, duration, volume, muted,
    lyrics, lyricsSynced, lyricsLoading,
        current, hasTrack, progress, displayTitle, displayArtist,
    hasPrev, hasNext, isFavorited, playModeLabel,
    hasLyrics, currentLyricIndex,
        play, playAll, addToQueue, removeFromQueue, clearQueue,
    playPrev, playNext, playIndex,
    togglePlay, togglePlayMode, toggleFavorite,
    stop, setVolume, toggleMute, seek,
    fetchLyrics, clearLyrics,
    loadFavoriteIds, starSong, unstarSong, isSongStarred,
    starPlaylist, unstarPlaylist, isPlaylistStarred,
    starAlbum, unstarAlbum, isAlbumStarred,
    starArtist, unstarArtist, isArtistStarred,
  }
})
