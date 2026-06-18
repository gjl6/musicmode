import client from '../client.js'


export function getArtists(params = {}) {
  return client.get('/editor/artists', { params })
}


export function getDuplicates() {
  return client.get('/editor/artists/duplicates')
}


export function submitArtistPipeline(template, selection = {}) {
  return client.post('/editor/artists/pipeline', { template, selection })
}


export function getArtistProviders() {
  return client.get('/editor/artists/providers')
}


export function submitMerge(groups) {
  return client.post('/editor/artists/merge', { groups })
}


export function searchArtistProviders(id, providers) {
  return client.post(`/editor/artists/${id}/search`, { providers })
}


export function applyArtistEnrich(id, source, mode, fields) {
  return client.post(`/editor/artists/${id}/apply`, { source, mode, fields })
}
