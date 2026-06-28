/**
 * music-playback 模块 — streaming + transcoding API。
 *
 * 后端对应：
 * - com.gjl.music.playback.streaming.StreamController  — HTTP Range 流媒体
 * - com.gjl.music.playback.transcoding.TranscodeController — FFmpeg 实时转码
 *
 * 认证通过 HttpOnly Cookie (music-jwt) 自动携带，无需手动拼接 token。
 */

/** 按原始文件路径流式播放（支持 HTTP Range） */
export function streamUrl(rawPath) {
  return `/api/player/stream?rawPath=${encodeURIComponent(rawPath)}`
}

/** 按歌曲数据库 ID 流式播放（支持 HTTP Range） */
export function streamUrlById(songId) {
  return `/api/player/song/${songId}`
}

/**
 * 转码流 URL（Subsonic 兼容参数）。
 * @param {number} songId 歌曲 ID
 * @param {object} [opts]
 * @param {number} [opts.maxBitRate=0] 最大比特率（kbps），0 = 不限
 * @param {string} [opts.format='mp3'] 目标格式（mp3/opus/aac/flac/raw）
 * @param {number} [opts.timeOffset=0] 时间偏移（秒）
 */
export function transcodeUrl(songId, { maxBitRate = 0, format = 'mp3', timeOffset = 0 } = {}) {
  return `/rest/stream?id=${songId}&maxBitRate=${maxBitRate}&format=${format}&timeOffset=${timeOffset}`
}
