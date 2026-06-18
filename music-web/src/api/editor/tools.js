import client from '../client.js'

const TOOL_ENDPOINT_MAP = {
  encodingRepair: 'repair',
  langConvert: 'convert',
  qqEnrich: 'enrich',
  dedup: 'dedup',
  batchWrite: 'write',
  importCollection: 'import-db',
  split: 'split',
  replaceText: 'replace',
  formatConvert: 'format-convert',
  cueSplit: 'cue-split',
  organize: 'organize',
  deleteFiles: 'delete',
}

const UNIMPLEMENTED_TOOLS = new Set([])

export function runTool(toolKey, options = {}) {
  const endpoint = TOOL_ENDPOINT_MAP[toolKey]
  if (!endpoint) {
    return Promise.reject(new Error(`Unknown tool: ${toolKey}`))
  }
  return client.post(`/tools/${endpoint}`, { options })
}

export function isToolImplemented(toolKey) {
  return !UNIMPLEMENTED_TOOLS.has(toolKey)
}

export { TOOL_ENDPOINT_MAP, UNIMPLEMENTED_TOOLS }
