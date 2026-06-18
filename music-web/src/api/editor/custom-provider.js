import client from '../client.js'

export function fetchAll() {
  return client.get('/custom-providers')
}

export function fetchOne(id) {
  return client.get(`/custom-providers/${id}`)
}

export function create(data) {
  return client.post('/custom-providers', data)
}

export function update(id, data) {
  return client.put(`/custom-providers/${id}`, data)
}

export function remove(id) {
  return client.delete(`/custom-providers/${id}`)
}

export function testProvider(id) {
  return client.post(`/custom-providers/${id}/test`)
}

export function updateParam(id, name, value) {
  return client.put(`/custom-providers/${id}/params`, { name, value })
}

export function downloadTemplate(className, label) {
  return client.get('/custom-providers/template', { params: { className, label } })
}

export function refreshAll() {
  return client.post('/custom-providers/refresh-all')
}
