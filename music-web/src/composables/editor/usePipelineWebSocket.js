import { ref, onUnmounted } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'


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
          const item = JSON.parse(message.body)
          itemEvents.value = [...itemEvents.value, item]
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
