import client from '../client.js'


export function fetchConfigs(params = {}) {
  return client.get('/config', { params })
}


export function fetchConfig(key) {
  return client.get(`/config/${encodeURIComponent(key)}`)
}


export function updateConfig(key, value) {
  return client.put(`/config/${encodeURIComponent(key)}`, { value })
}


export function refreshConfigCache() {
  return client.post('/config/refresh')
}


export function fetchCategories() {
  return client.get('/config/categories')
}
