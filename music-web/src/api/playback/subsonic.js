import axios from 'axios'
import { getToken } from '../client.js'

/**
 * music-playback 模块 — Subsonic API。
 *
 * 后端对应：
 * - com.gjl.music.playback.subsonic.SubsonicController — Subsonic/OpenSubsonic 兼容层
 *
 * Subsonic API 使用独立 Axios 实例（baseURL=/，非 /api），
 * 自有 Token+Salt 认证，只需请求拦截器附加 Bearer Token。
 */
const TOKEN_KEY = 'auth-token'

const subsonicClient = axios.create({ baseURL: '/', timeout: 30000 })
subsonicClient.interceptors.request.use((config) => {
  try {
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) config.headers.Authorization = `Bearer ${token}`
  } catch { /* ignore */ }
  // Subsonic API 默认返回 XML，前端需要 JSON
  if (config.params) {
    config.params.f = 'json'
  } else if (config.url && config.url.includes('?')) {
    config.url += '&f=json'
  } else if (config.url) {
    config.url += '?f=json'
  }
  return config
})
subsonicClient.interceptors.response.use((res) => {
  const data = res.data
  // 检测 Subsonic 协议层 status（后端 HTTP 200 但 Subsonic status=failed）
  if (data?.['subsonic-response']?.status === 'failed') {
    const err = data['subsonic-response'].error
    const msg = err?.message || err?.code ? `Subsonic error ${err.code}` : 'Subsonic API error'
    return Promise.reject(new Error(msg))
  }
  return data
})

// ══════════════════════════════════════════
// System
// ══════════════════════════════════════════

export function subsonicPing() {
  return subsonicClient.get('/rest/ping')
}

export function subsonicGetLicense() {
  return subsonicClient.get('/rest/getLicense')
}

// ══════════════════════════════════════════
// Searching
// ══════════════════════════════════════════

export function subsonicSearch(query, opts = {}) {
  const params = new URLSearchParams({
    query,
    artistCount: opts.artistCount || 20,
    albumCount: opts.albumCount || 20,
    songCount: opts.songCount || 20,
    ...opts,
  })
  return subsonicClient.get(`/rest/search3?${params}`)
}

// ══════════════════════════════════════════
// Playlists
// ══════════════════════════════════════════

export function subsonicGetPlaylists() {
  return subsonicClient.get('/rest/getPlaylists')
}

export function subsonicGetPlaylist(id) {
  return subsonicClient.get(`/rest/getPlaylist?id=${id}`)
}

// ══════════════════════════════════════════
// Browsing
// ══════════════════════════════════════════

export function subsonicGetAlbumList(type = 'newest', size = 10) {
  return subsonicClient.get(`/rest/getAlbumList2?type=${type}&size=${size}`)
}

export function subsonicGetGenres() {
  return subsonicClient.get('/rest/getGenres')
}

// ══════════════════════════════════════════
// Browsing — 艺术家 / 专辑 / 歌曲详情
// ══════════════════════════════════════════

export function subsonicGetArtists(params = {}) {
  return subsonicClient.get('/rest/getArtists', { params })
}

export function subsonicGetArtist(id) {
  return subsonicClient.get(`/rest/getArtist?id=${id}`)
}

export function subsonicGetAlbum(id) {
  return subsonicClient.get(`/rest/getAlbum?id=${id}`)
}

export function subsonicGetSong(id) {
  return subsonicClient.get(`/rest/getSong?id=${id}`)
}

export function subsonicGetMusicDirectory(id) {
  return subsonicClient.get(`/rest/getMusicDirectory?id=${id}`)
}

// ══════════════════════════════════════════
// Random / Songs by Genre
// ══════════════════════════════════════════

export function subsonicGetRandomSongs(size = 20) {
  return subsonicClient.get(`/rest/getRandomSongs?size=${size}`)
}

export function subsonicGetSongsByGenre(genre, count = 20, offset = 0) {
  return subsonicClient.get(`/rest/getSongsByGenre?genre=${encodeURIComponent(genre)}&count=${count}&offset=${offset}`)
}

/** 分页查询歌曲，支持按首字母过滤 + 排序（newest/alphabetical） */
export function subsonicGetSongs({ letter, sort, count = 50, offset = 0 } = {}) {
  const params = new URLSearchParams({ count, offset })
  if (letter) params.set('letter', letter)
  if (sort) params.set('sort', sort)
  return subsonicClient.get(`/rest/getSongs?${params}`)
}

/** 获取各首字母歌曲数量统计 */
export function subsonicGetSongLetters() {
  return subsonicClient.get('/rest/getSongLetters')
}

// ══════════════════════════════════════════
// Cover Art
// ══════════════════════════════════════════

/** 获取封面图 URL（直接返回图片流，非 JSON） */
export function subsonicGetCoverArtUrl(id, size = 300) {
  const TOKEN_KEY = 'auth-token'
  const token = localStorage.getItem(TOKEN_KEY)
  // Subsonic coverArt 使用 Token+Salt 认证，这里用简单 Bearer 兼容
  return `/rest/getCoverArt?id=${id}&size=${size}`
}

// ══════════════════════════════════════════
// Playlists CRUD（Subsonic 兼容）
// ══════════════════════════════════════════

export function subsonicCreatePlaylist(name, songIds = []) {
  const params = new URLSearchParams({ name })
  songIds.forEach(id => params.append('songId', id))
  return subsonicClient.get(`/rest/createPlaylist?${params}`)
}

export function subsonicDeletePlaylist(id) {
  return subsonicClient.get(`/rest/deletePlaylist?id=${id}`)
}

export function subsonicUpdatePlaylist(playlistId, { name, comment, isPublic } = {}) {
  const params = new URLSearchParams({ playlistId })
  if (name) params.append('name', name)
  if (comment !== undefined) params.append('comment', comment)
  if (isPublic !== undefined) params.append('public', String(isPublic))
  return subsonicClient.get(`/rest/updatePlaylist?${params}`)
}

// ══════════════════════════════════════════
// Starred / 收藏（OpenSubsonic）
// ══════════════════════════════════════════

export function subsonicGetStarred() {
  return subsonicClient.get('/rest/getStarred2')
}

export function subsonicStar(id, type = 'song') {
  const params = new URLSearchParams()
  // 歌曲用 id，其他类型用对应的 xxxId 参数（互斥，避免重复收藏）
  if (type === 'song') {
    params.append('id', String(id))
  } else if (type === 'album') {
    params.append('albumId', String(id))
  } else if (type === 'artist') {
    params.append('artistId', String(id))
  } else if (type === 'playlist') {
    params.append('playlistId', String(id))
  }
  return subsonicClient.get(`/rest/star?${params}`)
}

export function subsonicUnstar(id, type = 'song') {
  const params = new URLSearchParams()
  if (type === 'song') {
    params.append('id', String(id))
  } else if (type === 'album') {
    params.append('albumId', String(id))
  } else if (type === 'artist') {
    params.append('artistId', String(id))
  } else if (type === 'playlist') {
    params.append('playlistId', String(id))
  }
  return subsonicClient.get(`/rest/unstar?${params}`)
}

// ══════════════════════════════════════════
// Indexes
// ══════════════════════════════════════════

export function subsonicGetIndexes() {
  return subsonicClient.get('/rest/getIndexes')
}

// ══════════════════════════════════════════
// Lyrics (OpenSubsonic)
// ══════════════════════════════════════════

/** 根据 songId 获取结构化歌词（含 LRC 时间戳） */
export function subsonicGetLyricsBySongId(id) {
  return subsonicClient.get(`/rest/getLyricsBySongId?id=${id}`)
}

// ══════════════════════════════════════════
// User Data — scrobble / rating (OpenSubsonic)
// ══════════════════════════════════════════

/** 上报播放（scrobble）。submit=true 表示已播放完（计入计数），false 表示正在播放 */
export function subsonicScrobble(id, submission = true) {
  return subsonicClient.get(`/rest/scrobble?id=${id}&submission=${submission}`)
}

/** 上报播放进度（客户端播放停止时调用） */
export function subsonicReportPlayback({ mediaId, positionMs, state } = {}) {
  const params = new URLSearchParams()
  if (mediaId) params.set('mediaId', mediaId)
  if (positionMs != null) params.set('positionMs', String(positionMs))
  if (state) params.set('state', state)
  return subsonicClient.get(`/rest/reportPlayback?${params}`)
}

/** 评分（1-5） */
export function subsonicSetRating(id, rating) {
  return subsonicClient.get(`/rest/setRating?id=${id}&rating=${rating}`)
}
