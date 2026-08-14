import http from './http'

const TOKEN_KEY = 'apex.accessToken'
const USER_KEY = 'apex.currentUser'

export function getAccessToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function getCurrentUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

export function saveSession(loginResult) {
  localStorage.setItem(TOKEN_KEY, loginResult.accessToken)
  localStorage.setItem(USER_KEY, JSON.stringify(loginResult.user))
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export async function login(payload) {
  const response = await http.post('/api/auth/login', payload, { activity: false })
  saveSession(response.data)
  return response.data
}

export function registerByInvite(payload) {
  return http.post('/api/auth/register', payload, { activity: false })
}

export async function logout() {
  try {
    await http.post('/api/auth/logout', null, { activity: false })
  } finally {
    clearSession()
  }
}
