import client from '../client.js'

/**
 * 自定义艺术家 API — 对应 music-playback ArtistController。
 * 支持分区懒加载：详情 / 专辑列表 / 歌曲列表 / 编辑。
 */

/** 获取艺术家详情（仅艺术家信息，不含列表） */
export function getArtist(id) {
  return client.get(`/artists/${id}`)
}

/** 获取艺术家的专辑列表（分页 + 排序 + 可选字母过滤） */
export function getArtistAlbums(id, params = {}) {
  return client.get(`/artists/${id}/albums`, { params })
}

/** 获取艺术家的歌曲列表（分页 + 排序 + 可选字母过滤） */
export function getArtistSongs(id, params = {}) {
  return client.get(`/artists/${id}/songs`, { params })
}

/** 更新艺术家元数据 */
export function updateArtist(id, data) {
  return client.put(`/artists/${id}`, data)
}

/** 上传艺术家封面（multipart/form-data） */
export function uploadArtistCover(id, file) {
  const formData = new FormData()
  formData.append('file', file)
  return client.post(`/artists/${id}/cover`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 获取热门艺术家（按全局播放量排序） */
export function getTopArtists(limit = 12) {
  return client.get('/artists', { params: { sort: 'mostPlayed', limit } })
}
