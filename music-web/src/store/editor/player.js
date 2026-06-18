

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import i18n from '@/i18n/index.js'
import { streamUrl as editorStreamUrl } from '@/api/editor/player.js'


export function getStreamUrl(rawPath) {
  return editorStreamUrl(rawPath)
}


let audio = null
function getAudio() {
  if (!audio) {
    audio = new Audio()
    audio.preload = 'auto'
  }
  return audio
}

export const useEditorPlayerStore = defineStore('editorPlayer', () => {

  const currentFile = ref(null)
  const isPlaying = ref(false)
  const currentTime = ref(0)
  const duration = ref(0)
  const volume = ref(0.8)
  const muted = ref(false)


  const hasTrack = computed(() => currentFile.value !== null)

  const progress = computed(() =>
    duration.value > 0 ? currentTime.value / duration.value : 0,
  )


  const el = getAudio()

  el.ontimeupdate = () => {
    currentTime.value = el.currentTime
  }

  el.onloadedmetadata = () => {
    if (isFinite(el.duration)) duration.value = el.duration
  }

  el.onplay = () => { isPlaying.value = true }
  el.onpause = () => { isPlaying.value = false }

  el.onended = () => { stop() }

  el.onerror = () => {
    console.error('[Core Player] 音频加载失败:', el.error?.message)
    window.$message?.warning(i18n.global.t('workbench.player.loadError'))
    isPlaying.value = false
  }


  function play(file) {
    if (!file) return
    const path = file.path || file.rawPath || ''
    currentFile.value = file
    if (!path) {
      window.$message?.warning(i18n.global.t('workbench.player.noFilePath'))
      return
    }
    el.src = getStreamUrl(path)
    el.volume = volume.value
    isPlaying.value = true
    el.play().catch((err) => {
      console.error('[Core Player] 播放失败:', err)
      window.$message?.warning(i18n.global.t('workbench.player.playError'))
      isPlaying.value = false
    })
  }

  function togglePlay() {
    if (!currentFile.value) return
    if (el.paused) {
      el.play().catch(() => {})
    } else {
      el.pause()
    }
  }

  function stop() {
    el.pause()
    el.src = ''
    currentFile.value = null
    isPlaying.value = false
    currentTime.value = 0
    duration.value = 0
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
    const t = Math.max(0, Math.min(duration.value, time))
    el.currentTime = t
    currentTime.value = t
  }

  return {
    currentFile, isPlaying, currentTime, duration, volume, muted,
    hasTrack, progress,
    play, togglePlay, stop, setVolume, toggleMute, seek,
  }
})
