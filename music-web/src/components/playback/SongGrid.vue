<template>
  <div class="song-grid">
    <SongCard
      v-for="(row, idx) in songs"
      :key="row.id"
      :song="row"
      :starred="row._starred"
      :show-rating="true"
      :show-extra-actions="true"
      @play="$emit('play', row)"
      @toggleFav="$emit('toggleFav', row)"
      @addToQueue="$emit('addToQueue', row)"
      @addToPlaylist="$emit('addToPlaylist', row)"
      @rate="(val) => $emit('rate', val)"
    />
  </div>
</template>

<script setup>
import SongCard from '@/components/playback/SongCard.vue'

defineProps({
  songs: { type: Array, default: () => [] },
})

defineEmits(['play', 'addToQueue', 'toggleFav', 'rate', 'addToPlaylist'])
</script>

<style scoped>
.song-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(175px, 1fr));
  gap: 12px;
}
</style>
