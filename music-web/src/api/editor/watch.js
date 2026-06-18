import client from '../client.js'


export function fetchProfiles() {
  return client.get('/watch/profiles')
}


export function fetchProfile(id) {
  return client.get(`/watch/profiles/${id}`)
}


export function createProfile(data) {
  return client.post('/watch/profiles', data)
}


export function updateProfile(id, data) {
  return client.put(`/watch/profiles/${id}`, data)
}


export function deleteProfile(id) {
  return client.delete(`/watch/profiles/${id}`)
}


export function executeProfile(id, paths) {
  return client.post(`/watch/profiles/${id}/execute`, { paths })
}


export function fetchSteps() {
  return client.get('/watch/steps')
}
