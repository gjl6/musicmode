/**
 * music-core 模块 — Player API。
 *
 * 后端对应：
 * - com.gjl.music.controller.PlayerController — 音频流媒体（渐进式下载）
 *
 * URL 中 {@code /stream/_} 的 {@code _} 是 {@code {fileId}} 路径变量的占位符，
 * 后端通过 {@code rawPath} 查询参数获取真实文件路径。
 *
 * 认证通过 HttpOnly Cookie (music-jwt) 自动携带，无需手动拼接 token。
 */

/**
 * 按文件路径构造音频流 URL（HTML5 Audio 渐进式下载）。
 * @param {string} rawPath — 音乐文件路径
 * @returns {string} 流 URL
 */
export function streamUrl(rawPath) {
  return `/api/player/stream/_?rawPath=${encodeURIComponent(rawPath)}`
}
