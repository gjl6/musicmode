import client from '../client.js'

/**
 * music-playback 模块 — playlist API。
 *
 * 后端对应：
 * - com.gjl.music.playback.playlist.PlaylistController  — M3U 播放列表 CRUD
 * - com.gjl.music.playback.playlist.PlayQueueController — 播放队列管理
 *
 * 认证通过 HttpOnly Cookie (music-jwt) 自动携带，无需手动拼接 token。
 */

// ══════════════════════════════════════════
// 播放列表
// ══════════════════════════════════════════

export function listPlaylists() {
  return client.get('/playlists')
}

export function getPlaylist(id) {
  return client.get(`/playlists/${id}`)
}

export function createPlaylist(name, comment = '', isPublic = false, coverPath = null) {
  return client.post('/playlists', { name, comment, isPublic, coverPath })
}

export function updatePlaylist(id, name, comment, isPublic, coverPath) {
  return client.put(`/playlists/${id}`, { name, comment, isPublic, coverPath })
}

/** 上传歌单封面，返回 { coverPath } */
export function uploadPlaylistCover(id, file) {
  const form = new FormData()
  form.append('file', file)
  return client.post(`/playlists/${id}/cover`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function deletePlaylist(id) {
  return client.delete(`/playlists/${id}`)
}

export function addSongsToPlaylist(id, songIds) {
  return client.post(`/playlists/${id}/songs`, { songIds })
}

export function removeSongFromPlaylist(id, position) {
  return client.delete(`/playlists/${id}/songs/${position}`)
}

/** 移动歌单中曲目的位置 (fromPosition → toPosition，均为 1-based) */
export function movePlaylistTrack(playlistId, fromPosition, toPosition) {
  return client.put(`/playlists/${playlistId}/tracks/${fromPosition}/move?to=${toPosition}`)
}

/** 全量替换歌单歌曲顺序（前端排序后一次落盘） */
export function savePlaylistOrder(playlistId, songIds) {
  return client.put(`/playlists/${playlistId}/tracks`, { songIds })
}

/** 查询包含指定歌曲的歌单列表 */
export function getPlaylistsContainingSong(songId) {
  return client.get(`/playlists/containing/${songId}`)
}

/** 导出 M3U8 播放列表文件 URL */
export function exportM3uUrl(id) {
  return `/api/playlists/${id}/export.m3u`
}

// ══════════════════════════════════════════
// 播放队列
// ══════════════════════════════════════════

/** 获取当前用户播放队列（歌曲列表） */
export function getPlayQueue() {
  return client.get('/play-queue')
}

/** 获取队列原始条目（含 position） */
export function getPlayQueueEntries() {
  return client.get('/play-queue/entries')
}

/** 保存（替换）播放队列 */
export function savePlayQueue(songIds) {
  return client.post('/play-queue', { songIds })
}

/** 从队列指定位置移除 */
export function removeFromQueue(position) {
  return client.delete(`/play-queue/${position}`)
}

/** 清空队列 */
export function clearQueue() {
  return client.delete('/play-queue')
}
