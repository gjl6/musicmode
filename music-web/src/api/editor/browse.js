import client from '../client.js'

export function fetchDirectory(rawPath) {
  return client.get('/browse/directory', { params: { rawPath: rawPath || '' } })
}

export function fetchDirectoryMetadata(rawPath, page = 1, pageSize = 50) {
  return client.get('/browse/directory-metadata', {
    params: { rawPath: rawPath || '', page, pageSize },
  })
}
