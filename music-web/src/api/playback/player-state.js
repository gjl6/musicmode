import client from '../client.js'


export function reportPlayerState(state) {
  return client.post('/player/state', state)
}


export function getPlayerState(username) {
  if (username) {
    return client.get(`/player/state/${username}`)
  }
  return client.get('/player/state')
}


export function sendControlEvent(event, songId, position) {
  return client.post('/player/state/control', { event, songId, position })
}
