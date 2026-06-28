import client from '../client.js'

/** 查询注册开关状态（公开，无需登录） */
export function getRegisterStatus() {
  return client.get('/auth/register-status')
}

/** 修改注册开关（需认证 + config:write 权限） */
export function updateRegisterStatus(allowed) {
  return client.put('/auth/register-status', { allowRegistration: allowed })
}

/** 登录 */
export function login(username, password) {
  return client.post('/auth/login', { username, password })
}

/** 注册 */
export function register(username, password, displayName, email) {
  return client.post('/auth/register', { username, password, displayName, email })
}

/** 刷新 Token */
export function refreshToken(refreshToken) {
  return client.post('/auth/refresh', { refreshToken })
}

/** 登出 */
export function logout() {
  return client.post('/auth/logout')
}

/** 获取当前用户信息 */
export function getMe() {
  return client.get('/auth/me')
}

/** 获取当前用户权限列表 */
export function getMyPermissions() {
  return client.get('/auth/permissions')
}

/** 获取当前用户菜单/路由树（按权限过滤） */
export function getMenus() {
  return client.get('/auth/menus')
}

/** 修改密码（自服务，需旧密码验证） */
export function changePassword(oldPassword, newPassword) {
  return client.post('/auth/change-password', { oldPassword, newPassword })
}

/** 更新个人资料 */
export function updateProfile(displayName, email) {
  return client.put('/auth/profile', { displayName, email })
}

/** 上传头像 */
export function uploadAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return client.post('/auth/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 获取用户头像 URL */
export function getAvatarUrl(userId) {
  return `/api/auth/avatar/${userId}`
}
