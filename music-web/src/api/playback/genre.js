import client from '../client.js'


export function getGenres(params = {}) {
  return client.get('/genres', { params })
}


export function getGenreLetters() {
  return client.get('/genres/letters')
}


export function getGenreSongs(name, params = {}) {
  return client.get(`/genres/${encodeURIComponent(name)}`, { params })
}


export function updateStyle(name, data) {
  return client.put(`/genres/${encodeURIComponent(name)}`, data)
}
