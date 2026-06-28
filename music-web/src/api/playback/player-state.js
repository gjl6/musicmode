import client from '../client.js'

/**
 * music-playback 模块 — sync API。
 *
 * 后端对应：
 * - com.gjl.music.playback.sync.PlayerStateController — 播放状态同步（多端协同）
 */

/** 上报播放状态（客户端定时发送） */
export function reportPlayerState(state) {
  return client.post('/player/state', state)
}

/**
 * 获取播放状态。
 * @param {string} [username] — 不传则获取当前用户状态，传则获取指定用户状态
 */
export function getPlayerState(username) {
  if (username) {
    return client.get(`/player/state/${username}`)
  }
  return client.get('/player/state')
}

/** 发送播放控制事件（play/pause/stop/seek） */
export function sendControlEvent(event, songId, position) {
  return client.post('/player/state/control', { event, songId, position })
}
