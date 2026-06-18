import client from '../client.js'


export function getArtist(id) {
  return client.get(`/artists/${id}`)
}


export function getArtistAlbums(id, params = {}) {
  return client.get(`/artists/${id}/albums`, { params })
}


export function getArtistSongs(id, params = {}) {
  return client.get(`/artists/${id}/songs`, { params })
}


export function updateArtist(id, data) {
  return client.put(`/artists/${id}`, data)
}


export function uploadArtistCover(id, file) {
  const formData = new FormData()
  formData.append('file', file)
  return client.post(`/artists/${id}/cover`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
