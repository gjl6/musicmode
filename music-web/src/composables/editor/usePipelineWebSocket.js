import { ref, onUnmounted } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

/**
 * 管道进度 WebSocket 订阅。
 *
 * 订阅两个 topic：
 *   /topic/pipelines/{id}/progress — 聚合进度快照
 *   /topic/pipelines/{id}/items    — 逐文件处理结果（实时）
 *
 * 用法：
 *   const { progressEvent, itemEvents, connect, disconnect, connected } = usePipelineWebSocket()
 *   onMounted(() => connect(pipelineId))
 */
export function usePipelineWebSocket() {
  const progressEvent = ref(null)
  const itemEvents = ref([])
  const connected = ref(false)

  let client = null
  let progressSub = null
  let itemsSub = null

  function connect(pipelineId) {
    itemEvents.value = []
    if (client && client.active) {
      switchSubscription(pipelineId)
      return
    }

    const wsUrl = window.location.origin + '/ws'
    client = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        connected.value = true
        subscribeAll(pipelineId)
      },
      onDisconnect: () => {
        connected.value = false
      },
      onStompError: (frame) => {
        console.error('[PipelineWS] STOMP error:', frame.headers?.message)
      },
    })

    client.activate()
  }

  function subscribeAll(pipelineId) {
    if (progressSub) progressSub.unsubscribe()
    if (itemsSub) itemsSub.unsubscribe()

    progressSub = client.subscribe(
      `/topic/pipelines/${pipelineId}/progress`,
      (message) => {
        try {
          progressEvent.value = JSON.parse(message.body)
        } catch (e) {
          console.error('[PipelineWS] 解析进度消息失败:', e)
        }
      }
    )

    itemsSub = client.subscribe(
      `/topic/pipelines/${pipelineId}/items`,
      (message) => {
        try {
          const data = JSON.parse(message.body)
          // 批量消息：{ items: [...], count, final? } — 完整快照，直接替换
          if (data.items && Array.isArray(data.items)) {
            itemEvents.value = data.items
          } else {
            // 兼容旧的单条消息格式
            itemEvents.value = [...itemEvents.value, data]
          }
        } catch (e) {
          console.error('[PipelineWS] 解析 item 消息失败:', e)
        }
      }
    )
  }

  function switchSubscription(pipelineId) {
    itemEvents.value = []
    subscribeAll(pipelineId)
  }

  function disconnect() {
    if (progressSub) { progressSub.unsubscribe(); progressSub = null }
    if (itemsSub) { itemsSub.unsubscribe(); itemsSub = null }
    if (client) { client.deactivate(); client = null }
    connected.value = false
    progressEvent.value = null
    itemEvents.value = []
  }

  onUnmounted(() => {
    disconnect()
  })

  return { progressEvent, itemEvents, connect, disconnect, connected }
}
