import client from '../client.js'

/** 获取单个文件完整嵌套元数据 */
export function fetchMetadata(rawPath) {
  return client.get('/browse/metadata', {
    params: { rawPath: rawPath || '' },
  })
}

/** 保存编辑后的完整 MusicMetadata 到文件 */
export function saveMetadata(rawPath, meta) {
  return client.post('/music/save', {
    path: rawPath,
    metadata: meta,
  })
}

/** 下载远程封面到本地 */
export function downloadCover(url) {
  return client.post('/music/download-cover', { url })
}
