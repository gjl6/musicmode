import client from '../client.js'

/**
 * music-editor 模块 — 艺术家管理 API
 *
 * 后端对应：
 * - com.gjl.music.controller.ArtistManageController
 */

/** 分页搜索艺术家列表（支持 mode 选择和叠加过滤） */
export function getArtists(params = {}) {
  return client.get('/editor/artists', { params })
}

/** 查找大小写重复的艺术家分组 */
export function getDuplicates() {
  return client.get('/editor/artists/duplicates')
}

/** 提交艺术家 Pipeline 任务（enrich / normalize / merge 等） */
export function submitArtistPipeline(template, selection = {}) {
  return client.post('/editor/artists/pipeline', { template, selection })
}

/** 获取支持艺术家详情查询的标签源列表 */
export function getArtistProviders() {
  return client.get('/editor/artists/providers')
}

/** 提交艺术家合并任务（传统 API，保留兼容） */
export function submitMerge(groups) {
  return client.post('/editor/artists/merge', { groups })
}

/**
 * 单艺术家多源查询预览 — 对所有 Provider 查询并返回全部结果（按评分降序）。
 * @param {number} id - 艺术家 DB ID
 * @param {string[]} [providers] - 指定标签源列表，省略则使用默认全部
 */
export function searchArtistProviders(id, providers) {
  return client.post(`/editor/artists/${id}/search`, { providers })
}

/**
 * 应用选定的增强结果到 DB。
 * @param {number} id - 艺术家 DB ID
 * @param {string} source - 选定的标签源名称
 * @param {'fill'|'overwrite'} mode - 写入模式
 * @param {object} [fields] - 可选的手动字段覆盖
 */
export function applyArtistEnrich(id, source, mode, fields) {
  return client.post(`/editor/artists/${id}/apply`, { source, mode, fields })
}
