import client from '../client.js'


export function getAlbums({ sort = 'newest', letter, starred, limit = 60, offset = 0 } = {}) {
  const params = { sort, limit, offset }
  if (letter) params.letter = letter
  if (starred != null) params.starred = starred
  return client.get('/albums', { params })
}


export function getAlbumLetters(starred) {
  const params = {}
  if (starred != null) params.starred = starred
  return client.get('/albums/letters', { params })
}


export function getAlbum(id) {
  return client.get(`/albums/${id}`)
}


export function getAlbumSongs(id) {
  return client.get(`/albums/${id}/songs`)
}


export function updateAlbum(id, data) {
  return client.put(`/albums/${id}`, data)
}
