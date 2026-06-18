import axios from 'axios'
import { getToken } from '../client.js'


const TOKEN_KEY = 'auth-token'

const subsonicClient = axios.create({ baseURL: '/', timeout: 30000 })
subsonicClient.interceptors.request.use((config) => {
  try {
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) config.headers.Authorization = `Bearer ${token}`
  } catch {  }
    if (config.params) {
    config.params.f = 'json'
  } else if (config.url && config.url.includes('?')) {
    config.url += '&f=json'
  } else if (config.url) {
    config.url += '?f=json'
  }
  return config
})
subsonicClient.interceptors.response.use((res) => {
  const data = res.data
    if (data?.['subsonic-response']?.status === 'failed') {
    const err = data['subsonic-response'].error
    const msg = err?.message || err?.code ? `Subsonic error ${err.code}` : 'Subsonic API error'
    return Promise.reject(new Error(msg))
  }
  return data
})


export function subsonicPing() {
  return subsonicClient.get('/rest/ping')
}

export function subsonicGetLicense() {
  return subsonicClient.get('/rest/getLicense')
}


export function subsonicSearch(query, opts = {}) {
  const params = new URLSearchParams({
    query,
    artistCount: opts.artistCount || 20,
    albumCount: opts.albumCount || 20,
    songCount: opts.songCount || 20,
    ...opts,
  })
  return subsonicClient.get(`/rest/search3?${params}`)
}


export function subsonicGetPlaylists() {
  return subsonicClient.get('/rest/getPlaylists')
}

export function subsonicGetPlaylist(id) {
  return subsonicClient.get(`/rest/getPlaylist?id=${id}`)
}


export function subsonicGetAlbumList(type = 'newest', size = 10) {
  return subsonicClient.get(`/rest/getAlbumList2?type=${type}&size=${size}`)
}

export function subsonicGetGenres() {
  return subsonicClient.get('/rest/getGenres')
}


export function subsonicGetArtists(params = {}) {
  return subsonicClient.get('/rest/getArtists', { params })
}

export function subsonicGetArtist(id) {
  return subsonicClient.get(`/rest/getArtist?id=${id}`)
}

export function subsonicGetAlbum(id) {
  return subsonicClient.get(`/rest/getAlbum?id=${id}`)
}

export function subsonicGetSong(id) {
  return subsonicClient.get(`/rest/getSong?id=${id}`)
}

export function subsonicGetMusicDirectory(id) {
  return subsonicClient.get(`/rest/getMusicDirectory?id=${id}`)
}


export function subsonicGetRandomSongs(size = 20) {
  return subsonicClient.get(`/rest/getRandomSongs?size=${size}`)
}

export function subsonicGetSongsByGenre(genre, count = 20, offset = 0) {
  return subsonicClient.get(`/rest/getSongsByGenre?genre=${encodeURIComponent(genre)}&count=${count}&offset=${offset}`)
}


export function subsonicGetSongs({ letter, sort, count = 50, offset = 0 } = {}) {
  const params = new URLSearchParams({ count, offset })
  if (letter) params.set('letter', letter)
  if (sort) params.set('sort', sort)
  return subsonicClient.get(`/rest/getSongs?${params}`)
}


export function subsonicGetSongLetters() {
  return subsonicClient.get('/rest/getSongLetters')
}


export function subsonicGetCoverArtUrl(id, size = 300) {
  const TOKEN_KEY = 'auth-token'
  const token = localStorage.getItem(TOKEN_KEY)
    return `/rest/getCoverArt?id=${id}&size=${size}`
}


export function subsonicCreatePlaylist(name, songIds = []) {
  const params = new URLSearchParams({ name })
  songIds.forEach(id => params.append('songId', id))
  return subsonicClient.get(`/rest/createPlaylist?${params}`)
}

export function subsonicDeletePlaylist(id) {
  return subsonicClient.get(`/rest/deletePlaylist?id=${id}`)
}

export function subsonicUpdatePlaylist(playlistId, { name, comment, isPublic } = {}) {
  const params = new URLSearchParams({ playlistId })
  if (name) params.append('name', name)
  if (comment !== undefined) params.append('comment', comment)
  if (isPublic !== undefined) params.append('public', String(isPublic))
  return subsonicClient.get(`/rest/updatePlaylist?${params}`)
}


export function subsonicGetStarred() {
  return subsonicClient.get('/rest/getStarred2')
}

export function subsonicStar(id, type = 'song') {
  const params = new URLSearchParams()
    if (type === 'song') {
    params.append('id', String(id))
  } else if (type === 'album') {
    params.append('albumId', String(id))
  } else if (type === 'artist') {
    params.append('artistId', String(id))
  } else if (type === 'playlist') {
    params.append('playlistId', String(id))
  }
  return subsonicClient.get(`/rest/star?${params}`)
}

export function subsonicUnstar(id, type = 'song') {
  const params = new URLSearchParams()
  if (type === 'song') {
    params.append('id', String(id))
  } else if (type === 'album') {
    params.append('albumId', String(id))
  } else if (type === 'artist') {
    params.append('artistId', String(id))
  } else if (type === 'playlist') {
    params.append('playlistId', String(id))
  }
  return subsonicClient.get(`/rest/unstar?${params}`)
}


export function subsonicGetIndexes() {
  return subsonicClient.get('/rest/getIndexes')
}


export function subsonicGetLyricsBySongId(id) {
  return subsonicClient.get(`/rest/getLyricsBySongId?id=${id}`)
}


export function subsonicScrobble(id, submission = true) {
  return subsonicClient.get(`/rest/scrobble?id=${id}&submission=${submission}`)
}


export function subsonicReportPlayback({ mediaId, positionMs, state } = {}) {
  const params = new URLSearchParams()
  if (mediaId) params.set('mediaId', mediaId)
  if (positionMs != null) params.set('positionMs', String(positionMs))
  if (state) params.set('state', state)
  return subsonicClient.get(`/rest/reportPlayback?${params}`)
}


export function subsonicSetRating(id, rating) {
  return subsonicClient.get(`/rest/setRating?id=${id}&rating=${rating}`)
}
