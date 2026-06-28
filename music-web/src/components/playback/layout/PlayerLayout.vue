<template>
  <div class="player-layout">
    <PlayerSidebar />
    <main class="player-content">
      <router-view />
    </main>
    <QueuePanel v-if="player.hasTrack" />
    <MiniPlayer />
  </div>
</template>

<script setup>
import PlayerSidebar from './PlayerSidebar.vue'
import QueuePanel from '@/components/playback/QueuePanel.vue'
import MiniPlayer from '@/components/playback/MiniPlayer.vue'
import { usePlayerStore } from '@/store/playback/player.js'

const player = usePlayerStore()
</script>

<style scoped>
.player-layout {
  display: flex;
  height: 100vh;
  padding: 12px;
  gap: 12px;
  overflow: hidden;
  box-sizing: border-box;
}

.player-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  border: var(--border-width-strong, 3px) solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--gradient-card, var(--ct-bg));
  box-shadow: var(--effect-card-inner), var(--effect-card-outer);
  min-width: 0;
}

.player-content::-webkit-scrollbar {
  width: 6px;
}
.player-content::-webkit-scrollbar-track {
  background: transparent;
}
.player-content::-webkit-scrollbar-thumb {
  background: transparent;
  border-radius: 10px;
}
.player-content:hover::-webkit-scrollbar-thumb {
  background: var(--sb-scrollbar);
}
</style>
