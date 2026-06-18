

export function streamUrl(rawPath) {
  return `/api/player/stream?rawPath=${encodeURIComponent(rawPath)}`
}


export function streamUrlById(songId) {
  return `/api/player/song/${songId}`
}
