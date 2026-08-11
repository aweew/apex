import { watch } from 'vue'

const VIEW_STATE_PREFIX = 'apex.viewState.'

function resolveStorage(storage) {
  if (storage) return storage
  if (typeof sessionStorage === 'undefined') return null
  return sessionStorage
}

export function readViewState(key, storage) {
  try {
    const raw = resolveStorage(storage)?.getItem(`${VIEW_STATE_PREFIX}${key}`)
    const value = raw ? JSON.parse(raw) : null
    return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
  } catch {
    return {}
  }
}

export function writeViewState(key, value, storage) {
  try {
    resolveStorage(storage)?.setItem(`${VIEW_STATE_PREFIX}${key}`, JSON.stringify(value))
  } catch {
    /* ignore unavailable browser storage */
  }
}

export function useSessionViewState(key, fields, storage) {
  const saved = readViewState(key, storage)
  for (const [name, field] of Object.entries(fields)) {
    if (Object.prototype.hasOwnProperty.call(saved, name)) {
      const current = field.value
      const stored = saved[name]
      if (current && stored && typeof current === 'object' && typeof stored === 'object') {
        field.value = { ...current, ...stored }
      } else {
        field.value = stored
      }
    }
  }

  watch(
    () => Object.fromEntries(Object.entries(fields).map(([name, field]) => [name, field.value])),
    (value) => writeViewState(key, value, storage),
    { deep: true, flush: 'sync' },
  )
}
