

import { streamUrl as editorStreamUrl } from '@/api/editor/player.js'


export function getCoverUrl(coverPath) {
  if (!coverPath) return null
  if (coverPath.startsWith('http://') || coverPath.startsWith('https://')) return coverPath

  const normalized = coverPath.replace(/\\/g, '/')
  const idx = normalized.indexOf('/covers/')
  const relative = idx >= 0 ? normalized.substring(idx + '/covers/'.length) : normalized
  const base = `/api/browse/covers/${relative.split('/').map(encodeURIComponent).join('/')}`
  return base
}


export function getStreamUrl(rawPath) {
  return editorStreamUrl(rawPath)
}
