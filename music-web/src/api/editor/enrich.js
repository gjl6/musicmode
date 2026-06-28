import client from '../client.js'

/** 获取可用 provider 列表 */
export function listProviders() {
  return client.get('/pipelines/enrich-providers')
}

/** 搜索增强数据 —— 发送当前 meta 的字段，返回 List<MusicMetadata> */
export function enrichSearch(provider, meta) {
  const params = {
    provider,
    title: meta?.song?.title || '',
    artist: meta?.artist?.artistName || '',
  }
  const a = meta?.album
  if (a?.albumName) params.album = a.albumName
  if (a?.company) params.company = a.company
  if (a?.albumYear) params.albumYear = String(a.albumYear)
  const s = meta?.song
  if (s?.year) params.year = String(s.year)
  if (s?.trackNumber) params.trackNumber = String(s.trackNumber)
  if (s?.discNumber) params.discNumber = String(s.discNumber)
  if (s?.language) params.language = s.language
  const st = meta?.style
  if (st?.styleName) params.genre = st.styleName
  return client.get('/pipelines/enrich-search', { params })
}

/** 获取歌曲完整详情（genre, language, company, description, lyrics 等） */
export function fetchSongDetail(provider, songId) {
  return client.get('/pipelines/enrich-detail', {
    params: { provider, songId },
  })
}
