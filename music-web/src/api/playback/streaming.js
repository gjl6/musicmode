/**
 * music-playback 模块 — 流媒体 API。
 *
 * 后端对应：
 * - com.gjl.music.playback.streaming.StreamController — 音频流媒体（HTTP Range / 206 / 304）
 *
 * 与 music-core 的 PlayerController (/api/player/stream/_) 不同，
 * 此模块使用自有端点，认证通过 HttpOnly Cookie 自动携带。
 */

/**
 * 按文件路径构造音频流 URL。
 * @param {string} rawPath — 音乐文件路径（绝对路径或相对路径）
 * @returns {string}
 */
export function streamUrl(rawPath) {
  return `/api/player/stream?rawPath=${encodeURIComponent(rawPath)}`
}

/**
 * 按歌曲数据库 ID 构造音频流 URL。
 * @param {number|string} songId — 歌曲 ID
 * @returns {string}
 */
export function streamUrlById(songId) {
  return `/api/player/song/${songId}`
}
