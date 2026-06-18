

export function streamUrl(rawPath) {
  return `/api/player/stream?rawPath=${encodeURIComponent(rawPath)}`
}


export function streamUrlById(songId) {
  return `/api/player/song/${songId}`
}


export function transcodeUrl(songId, { maxBitRate = 0, format = 'mp3', timeOffset = 0 } = {}) {
  return `/rest/stream?id=${songId}&maxBitRate=${maxBitRate}&format=${format}&timeOffset=${timeOffset}`
}
