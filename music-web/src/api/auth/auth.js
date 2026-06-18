import client from '../client.js'


export function login(username, password) {
  return client.post('/auth/login', { username, password })
}


export function register(username, password, displayName, email) {
  return client.post('/auth/register', { username, password, displayName, email })
}


export function refreshToken(refreshToken) {
  return client.post('/auth/refresh', { refreshToken })
}


export function logout() {
  return client.post('/auth/logout')
}


export function getMe() {
  return client.get('/auth/me')
}


export function getMyPermissions() {
  return client.get('/auth/permissions')
}
