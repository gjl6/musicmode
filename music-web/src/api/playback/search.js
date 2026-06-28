import client from '@/api/client.js'
import { listPlaylists } from './playlist.js'

/**
 * 全文搜索 — 并行查询歌曲/专辑/艺术家/歌词/歌单。
 *
 * 后端端点: GET /api/search?q=...&type=song|album|artist|lyric&offset=...&limit=...
 * 歌单使用客户端过滤（调用 listPlaylists 后按名称匹配）。
 */
export function searchAll(query, { songCount = 50, albumCount = 30, artistCount = 30, lyricCount = 30 } = {}) {
  return Promise.all([
    client.get('/search', { params: { query, type: 'song',   limit: songCount } }),
    client.get('/search', { params: { query, type: 'album',  limit: albumCount } }),
    client.get('/search', { params: { query, type: 'artist', limit: artistCount } }),
    client.get('/search', { params: { query, type: 'lyric',  limit: lyricCount } }),
    searchPlaylists(query),
  ]).then(([songRes, albumRes, artistRes, lyricRes, playlists]) => ({
    songs:     songRes?.results   || [],
    albums:    albumRes?.results  || [],
    artists:   artistRes?.results || [],
    lyrics:    lyricRes?.results  || [],
    playlists: playlists          || [],
  }))
}

/** 按类型搜索（单路） */
export function searchByType(query, type, { offset = 0, limit = 20 } = {}) {
  return client.get('/search', { params: { query, type, offset, limit } })
}

/** 客户端搜索歌单（按名称模糊匹配） */
async function searchPlaylists(query) {
  try {
    const all = await listPlaylists()
    const q = query.toLowerCase()
    return (all || []).filter(p =>
      (p.name || '').toLowerCase().includes(q)
    ).slice(0, 20)
  } catch {
    return []
  }
}
