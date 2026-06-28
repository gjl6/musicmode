import client from '../client.js'

/** 获取配置列表（支持筛选） */
export function fetchConfigs(params = {}) {
  return client.get('/config', { params })
}

/** 获取单个配置 */
export function fetchConfig(key) {
  return client.get(`/config/${encodeURIComponent(key)}`)
}

/** 修改配置值 */
export function updateConfig(key, value) {
  return client.put(`/config/${encodeURIComponent(key)}`, { value })
}

/** 刷新缓存 */
export function refreshConfigCache() {
  return client.post('/config/refresh')
}

/** 获取所有分类 */
export function fetchCategories() {
  return client.get('/config/categories')
}
