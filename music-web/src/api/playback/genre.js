import client from '../client.js'

/**
 * music-playback 模块 — 风格 API
 *
 * 后端对应：
 * - com.gjl.music.playback.genre.GenreController
 */

/** 分页查询风格列表，支持首字母过滤 + 排序 */
export function getGenres(params = {}) {
  return client.get('/genres', { params })
}

/** 获取各首字母风格数量统计 */
export function getGenreLetters() {
  return client.get('/genres/letters')
}

/** 获取风格下的歌曲列表（分页 + 排序 + 字母过滤） */
export function getGenreSongs(name, params = {}) {
  return client.get(`/genres/${encodeURIComponent(name)}`, { params })
}

/** 更新风格元数据 */
export function updateStyle(name, data) {
  return client.put(`/genres/${encodeURIComponent(name)}`, data)
}
