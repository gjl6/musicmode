import client from '../client.js'


export function getUsers({ keyword = '', page = 1, size = 20, orderBy = 'id', orderDir = 'ASC' } = {}) {
  return client.get('/admin/users', { params: { keyword, page, size, orderBy, orderDir } })
}


export function getUser(id) {
  return client.get(`/admin/users/${id}`)
}


export function createUser(data) {
  return client.post('/admin/users', data)
}


export function updateUser(id, data) {
  return client.put(`/admin/users/${id}`, data)
}


export function deleteUser(id) {
  return client.delete(`/admin/users/${id}`)
}


export function resetPassword(id, data) {
  return client.put(`/admin/users/${id}/password`, data)
}


export function assignRoles(id, roleIds) {
  return client.put(`/admin/users/${id}/roles`, roleIds)
}


export function getRoles() {
  return client.get('/admin/roles')
}


export function getRole(id) {
  return client.get(`/admin/roles/${id}`)
}


export function createRole(data) {
  return client.post('/admin/roles', data)
}


export function updateRole(id, data) {
  return client.put(`/admin/roles/${id}`, data)
}


export function deleteRole(id) {
  return client.delete(`/admin/roles/${id}`)
}


export function assignPermissions(id, permissionIds) {
  return client.put(`/admin/roles/${id}/permissions`, { permissionIds })
}


export function getPermissions() {
  return client.get('/admin/permissions')
}
