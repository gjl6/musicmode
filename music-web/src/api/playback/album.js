import client from '../client.js'

/**
 * 自定义专辑 API — 对应 music-playback AlbumController。
 * 弥补 Subsonic getAlbumList2 的不足（无 total、无字母索引、详情需两次请求）。
 */

/** 分页查询专辑列表 */
export function getAlbums({ sort = 'newest', letter, starred, limit = 60, offset = 0 } = {}) {
  const params = { sort, limit, offset }
  if (letter) params.letter = letter
  if (starred != null) params.starred = starred
  return client.get('/albums', { params })
}

/** 获取各首字母专辑数量统计 */
export function getAlbumLetters(starred) {
  const params = {}
  if (starred != null) params.starred = starred
  return client.get('/albums/letters', { params })
}

/** 获取专辑详情（含歌曲列表） */
export function getAlbum(id) {
  return client.get(`/albums/${id}`)
}

/** 获取专辑的歌曲列表（仅歌曲） */
export function getAlbumSongs(id) {
  return client.get(`/albums/${id}/songs`)
}

/** 更新专辑元数据 */
export function updateAlbum(id, data) {
  return client.put(`/albums/${id}`, data)
}
