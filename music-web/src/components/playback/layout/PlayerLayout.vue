<template>
  <div class="player-layout">
    <PlayerSidebar />
    <main class="player-content">
      <router-view />
    </main>
    <MiniPlayer />
  </div>
</template>

<script setup>
import PlayerSidebar from './PlayerSidebar.vue'
import MiniPlayer from '@/components/playback/MiniPlayer.vue'
</script>

<style scoped>
.player-layout {
  display: flex;
  height: calc(100vh - 48px);
  overflow: hidden;
}

.player-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  background: var(--gradient-page, var(--ct-bg));
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
