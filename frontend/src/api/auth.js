import { http } from './request'

const TOKEN_KEY = 'admin_token'
const USER_KEY = 'admin_user'

/** 登录并保存会话 */
export async function login(username, password) {
  const data = await http.post('/api/admin/login', { username, password })
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(USER_KEY, JSON.stringify({ username: data.username, displayName: data.displayName, role: data.role }))
  return data
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getCurrentUser() {
  const raw = localStorage.getItem(USER_KEY)
  return raw ? JSON.parse(raw) : null
}

export function logout() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
