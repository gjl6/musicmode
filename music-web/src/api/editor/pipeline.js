import client from '../client.js'

export function fetchPipelines() {
  return client.get('/pipelines')
}

export function fetchPipeline(id) {
  return client.get(`/pipelines/${id}`)
}

export function pausePipeline(id) {
  return client.post(`/pipelines/${id}/pause`)
}

export function resumePipeline(id) {
  return client.post(`/pipelines/${id}/resume`)
}

export function cancelPipeline(id) {
  return client.post(`/pipelines/${id}/cancel`)
}

export function fetchPipelineItems(pipelineId, status = 'SUCCESS', page = 0, size = 50) {
  return client.get(`/pipelines/${pipelineId}/items`, { params: { status, page, size } })
}

export function fetchDedupGroups(pipelineId, strategy) {
  return client.get(`/pipelines/${pipelineId}/dedup-groups`, { params: { strategy } })
}

export function executeDedupDelete(pipelineId, payload) {
  return client.post(`/pipelines/${pipelineId}/dedup-delete`, payload)
}
