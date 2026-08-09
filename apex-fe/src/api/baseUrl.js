const LOCAL_API_BASE = 'http://127.0.0.1:8080/apex'

export function resolveApiBase(configuredBase, isDev) {
  const base = configuredBase?.trim() || (isDev ? LOCAL_API_BASE : '/apex')
  return base.replace(/\/+$/, '')
}

export function buildApiUrl(path, base = API_BASE) {
  const normalizedBase = String(base).replace(/\/+$/, '')
  return `${normalizedBase}/${String(path).replace(/^\/+/, '')}`
}

const viteEnv = import.meta.env || {}

export const API_BASE = resolveApiBase(viteEnv.VITE_API_BASE, Boolean(viteEnv.DEV))
