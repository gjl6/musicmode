import client from '../client.js'

// ── 用户管理 ──

/** 分页搜索用户 */
export function getUsers({ keyword = '', page = 1, size = 20, orderBy = 'id', orderDir = 'ASC' } = {}) {
  return client.get('/admin/users', { params: { keyword, page, size, orderBy, orderDir } })
}

/** 获取用户详情 */
export function getUser(id) {
  return client.get(`/admin/users/${id}`)
}

/** 创建用户 */
export function createUser(data) {
  return client.post('/admin/users', data)
}

/** 更新用户 */
export function updateUser(id, data) {
  return client.put(`/admin/users/${id}`, data)
}

/** 删除用户 */
export function deleteUser(id) {
  return client.delete(`/admin/users/${id}`)
}

/** 重置用户密码 */
export function resetPassword(id, data) {
  return client.put(`/admin/users/${id}/password`, data)
}

/** 为用户分配角色 */
export function assignRoles(id, roleIds) {
  return client.put(`/admin/users/${id}/roles`, roleIds)
}

// ── 角色管理 ──

/** 获取角色列表 */
export function getRoles() {
  return client.get('/admin/roles')
}

/** 获取角色详情 */
export function getRole(id) {
  return client.get(`/admin/roles/${id}`)
}

/** 创建角色 */
export function createRole(data) {
  return client.post('/admin/roles', data)
}

/** 更新角色 */
export function updateRole(id, data) {
  return client.put(`/admin/roles/${id}`, data)
}

/** 删除角色 */
export function deleteRole(id) {
  return client.delete(`/admin/roles/${id}`)
}

/** 为角色分配权限 */
export function assignPermissions(id, permissionIds) {
  return client.put(`/admin/roles/${id}/permissions`, { permissionIds })
}

// ── 权限管理 ──

/** 获取权限列表 */
export function getPermissions() {
  return client.get('/admin/permissions')
}
