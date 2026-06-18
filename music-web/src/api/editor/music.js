import client from '../client.js'


export function fetchMetadata(rawPath) {
  return client.get('/browse/metadata', {
    params: { rawPath: rawPath || '' },
  })
}


export function saveMetadata(rawPath, meta) {
  return client.post('/music/save', {
    path: rawPath,
    metadata: meta,
  })
}


export function downloadCover(url) {
  return client.post('/music/download-cover', { url })
}
