import client from '../client.js'


export function listPlaylists() {
  return client.get('/playlists')
}

export function getPlaylist(id) {
  return client.get(`/playlists/${id}`)
}

export function createPlaylist(name, comment = '', isPublic = false, coverPath = null) {
  return client.post('/playlists', { name, comment, isPublic, coverPath })
}

export function updatePlaylist(id, name, comment, isPublic, coverPath) {
  return client.put(`/playlists/${id}`, { name, comment, isPublic, coverPath })
}


export function uploadPlaylistCover(id, file) {
  const form = new FormData()
  form.append('file', file)
  return client.post(`/playlists/${id}/cover`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function deletePlaylist(id) {
  return client.delete(`/playlists/${id}`)
}

export function addSongsToPlaylist(id, songIds) {
  return client.post(`/playlists/${id}/songs`, { songIds })
}

export function removeSongFromPlaylist(id, position) {
  return client.delete(`/playlists/${id}/songs/${position}`)
}


export function movePlaylistTrack(playlistId, fromPosition, toPosition) {
  return client.put(`/playlists/${playlistId}/tracks/${fromPosition}/move?to=${toPosition}`)
}


export function savePlaylistOrder(playlistId, songIds) {
  return client.put(`/playlists/${playlistId}/tracks`, { songIds })
}


export function getPlaylistsContainingSong(songId) {
  return client.get(`/playlists/containing/${songId}`)
}


export function exportM3uUrl(id) {
  return `/api/playlists/${id}/export.m3u`
}


export function getPlayQueue() {
  return client.get('/play-queue')
}


export function getPlayQueueEntries() {
  return client.get('/play-queue/entries')
}


export function savePlayQueue(songIds) {
  return client.post('/play-queue', { songIds })
}


export function removeFromQueue(position) {
  return client.delete(`/play-queue/${position}`)
}


export function clearQueue() {
  return client.delete('/play-queue')
}
