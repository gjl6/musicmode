

export function streamUrl(rawPath) {
  return `/api/player/stream/_?rawPath=${encodeURIComponent(rawPath)}`
}
