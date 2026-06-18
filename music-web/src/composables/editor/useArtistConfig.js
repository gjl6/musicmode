import { ref } from 'vue'
import apiClient from '@/api/client.js'

const joinSeparator = ref('')
let loaded = false

export function useArtistConfig() {
  if (!loaded) {
    loaded = true
    loadConfig()
  }
  return { joinSeparator }
}

async function loadConfig() {
  try {
    const res = await apiClient.get('/config/music.artist.join-separator')
    if (res?.configValue) joinSeparator.value = res.configValue
  } catch {
      }
}
