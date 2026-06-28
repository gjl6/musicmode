import client from '../client.js'

/** 获取所有 WatchProfile */
export function fetchProfiles() {
  return client.get('/watch/profiles')
}

/** 获取单个 WatchProfile */
export function fetchProfile(id) {
  return client.get(`/watch/profiles/${id}`)
}

/** 创建 WatchProfile */
export function createProfile(data) {
  return client.post('/watch/profiles', data)
}

/** 更新 WatchProfile */
export function updateProfile(id, data) {
  return client.put(`/watch/profiles/${id}`, data)
}

/** 删除 WatchProfile */
export function deleteProfile(id) {
  return client.delete(`/watch/profiles/${id}`)
}

/** 手动执行 profile（传入文件路径列表） */
export function executeProfile(id, paths) {
  return client.post(`/watch/profiles/${id}/execute`, { paths })
}

/** 获取可用的处理步骤类型 */
export function fetchSteps() {
  return client.get('/watch/steps')
}

/** 暂停任务的自动扫描 */
export function pauseProfile(id) {
  return client.post(`/watch/profiles/${id}/pause`)
}

/** 恢复任务的自动扫描 */
export function resumeProfile(id) {
  return client.post(`/watch/profiles/${id}/resume`)
}

/** 获取任务运行状态 */
export function getProfileState(id) {
  return client.get(`/watch/profiles/${id}/state`)
}

/** 立即触发一次扫描 */
export function scanNow(id) {
  return client.post(`/watch/profiles/${id}/scan`)
}
