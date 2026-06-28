import { ref } from 'vue'
import { listProviders, enrichSearch, fetchSongDetail } from '@/api/editor/enrich.js'
import { useEditStore } from '@/store/editor/edit.js'

/** 可用的 provider 列表（全局缓存，只加载一次） */
const providerList = ref([])
let providersLoaded = false

/**
 * 增强搜索 composable — 搜索快速返回基础字段，详情按需加载。
 */
export function useEnrich() {
  const provider = ref(['netease'])
  const results = ref([])
  const loading = ref(false)
  const error = ref(null)
  const overwriteMode = ref('fill') // 'fill' | 'overwrite'

  /** 加载 provider 列表（幂等，全局缓存） */
  async function loadProviders() {
    if (providersLoaded) return
    try {
      const data = await listProviders()
      providerList.value = Array.isArray(data) ? data : []
      providersLoaded = true
    } catch {
      providerList.value = [
        { name: 'all', label: '全部源（聚合搜索）' },
        { name: 'qqmusic', label: 'QQ音乐' },
        { name: 'kugou', label: '酷狗音乐' },
        { name: 'kuwo', label: '酷我音乐' },
        { name: 'netease', label: '网易云音乐' },
        { name: 'itunes', label: 'iTunes' },
        { name: 'musicbrainz', label: 'MusicBrainz' },
        { name: 'migu', label: '咪咕音乐' },
      ]
      providersLoaded = true
    }
  }

  /** 执行搜索 —— 一次返回完整数据。支持多源（数组 → 逗号字符串） */
  async function search() {
    const meta = useEditStore().currentMeta
    const title = meta?.song?.title || ''
    if (!title || loading.value) return
    loading.value = true
    error.value = null
    try {
      const pv = Array.isArray(provider.value) ? provider.value.join(',') : provider.value
      const data = await enrichSearch(pv, meta)
      results.value = data.results || []
    } catch (e) {
      error.value = e?.response?.data?.error || e.message || 'search failed'
      results.value = []
    } finally {
      loading.value = false
    }
  }

  /**
   * 按需加载歌曲完整详情（genre, language, company, description, lyrics 等）。
   * 将返回的详情字段合并回 results 中对应行。
   */
  async function loadDetail(rowIndex) {
    const row = results.value[rowIndex]
    if (!row) return
    const songId = row.songs?.[0]?.fileName // enrichSearch 暂存于此
    if (!songId) return
    try {
      const pv = Array.isArray(provider.value) ? provider.value[0] : provider.value
      const data = await fetchSongDetail(pv, songId)
      const detail = data?.detail
      if (!detail) return
      // 合并详情字段到 results 行
      const updated = { ...row }
      if (detail.genre && !updated.styles?.[0]) {
        updated.styles = [{ styleName: detail.genre }]
      } else if (detail.genre && updated.styles?.[0]) {
        updated.styles = [{ ...updated.styles[0], styleName: detail.genre }]
      }
      if (detail.lyricContent && !updated.lyrics?.[0]) {
        updated.lyrics = [{ content: detail.lyricContent }]
      }
      if (detail.company || detail.description || detail.totalTracks || detail.albumName) {
        const alb = updated.albums?.[0] || {}
        if (detail.company) alb.company = detail.company
        if (detail.description) alb.introduction = detail.description
        if (detail.totalTracks) alb.songCount = detail.totalTracks
        if (detail.albumName) alb.albumName = detail.albumName
        updated.albums = [{ ...alb }]
      }
      if (detail.trackNumber != null && !updated.songs?.[0]?.trackNumber) {
        updated.songs = [{ ...updated.songs[0], trackNumber: detail.trackNumber }]
      }
      if (detail.discNumber != null && !updated.songs?.[0]?.discNumber) {
        updated.songs = [{ ...updated.songs[0], discNumber: detail.discNumber }]
      }
      if (detail.language && !updated.songs?.[0]?.language) {
        updated.songs = [{ ...updated.songs[0], language: detail.language }]
      }
      results.value[rowIndex] = updated
    } catch {
      // 静默失败
    }
  }

  /** 清除 HTML 标签，保留 LRC 时间标记 */
  function cleanLyric(lrc) {
    if (!lrc) return ''
    return lrc
      .replace(/<[^>]+>/g, '')
      .split('\n')
      .map(l => l.trim())
      .filter(l => l)
      .join('\n')
  }

  function reset() {
    results.value = []
    error.value = null
  }

  return {
    provider,
    providerList,
    results,
    loading,
    error,
    overwriteMode,
    loadProviders,
    search,
    loadDetail,
    cleanLyric,
    reset,
  }
}
