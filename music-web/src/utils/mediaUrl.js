/**
 * 媒体 URL 工具 — 为 img/audio 标签生成 URL。
 *
 * img 和 audio 标签不经过 Axios 拦截器，无法自动带 Authorization 头。
 * 认证通过 HttpOnly Cookie (music-jwt) 自动携带，无需手动拼接 token。
 */
import { streamUrl as editorStreamUrl } from '@/api/editor/player.js'

/**
 * 构造封面图片 URL。
 * @param {string|null|undefined} coverPath — DB 中存储的封面路径（相对或绝对）
 * @returns {string|null}
 */
export function getCoverUrl(coverPath) {
  if (!coverPath) return null
  if (coverPath.startsWith('http://') || coverPath.startsWith('https://')) return coverPath

  const normalized = coverPath.replace(/\\/g, '/')
  const idx = normalized.indexOf('/covers/')
  const relative = idx >= 0 ? normalized.substring(idx + '/covers/'.length) : normalized
  const base = `/api/browse/covers/${relative.split('/').map(encodeURIComponent).join('/')}`
  return base
}

/**
 * 构造音频流 URL（使用 music-core PlayerController，工作台 MiniPlayer）。
 * @param {string} rawPath — 音乐文件路径
 * @returns {string}
 */
export function getStreamUrl(rawPath) {
  return editorStreamUrl(rawPath)
}
