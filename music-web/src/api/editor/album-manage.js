import client from '../client.js'

/**
 * music-editor 模块 — 专辑管理 API
 *
 * 后端对应：
 * - com.gjl.music.controller.AlbumManageController
 */

/** 分页搜索专辑列表（支持 mode 选择和叠加过滤） */
export function getAlbums(params = {}) {
  return client.get('/editor/albums', { params })
}

/** 查找大小写重复的专辑分组 */
export function getDuplicates() {
  return client.get('/editor/albums/duplicates')
}

/** 提交专辑 Pipeline 任务（enrich / normalize / merge 等） */
export function submitAlbumPipeline(template, selection = {}) {
  return client.post('/editor/albums/pipeline', { template, selection })
}

/** 获取支持专辑详情查询的标签源列表 */
export function getAlbumProviders() {
  return client.get('/editor/albums/providers')
}

/** 提交专辑合并任务 */
export function submitMerge(groups) {
  return client.post('/editor/albums/merge', { groups })
}

/**
 * 单专辑多源查询预览 — 对所有 Provider 查询并返回全部结果（按评分降序）。
 * @param {number} id - 专辑 DB ID
 * @param {string[]} [providers] - 指定标签源列表，省略则使用默认全部
 */
export function searchAlbumProviders(id, providers) {
  return client.post(`/editor/albums/${id}/search`, { providers })
}

/**
 * 应用选定的增强结果到 DB。
 * @param {number} id - 专辑 DB ID
 * @param {string} source - 选定的标签源名称
 * @param {'fill'|'overwrite'} mode - 写入模式
 * @param {object} [fields] - 可选的手动字段覆盖
 */
export function applyAlbumEnrich(id, source, mode, fields) {
  return client.post(`/editor/albums/${id}/apply`, { source, mode, fields })
}
