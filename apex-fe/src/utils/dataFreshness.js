import { ref } from 'vue'

export const dataFreshness = ref(null)

export function publishDataFreshness(payload) {
  if (!payload?.label || !payload?.route) return
  dataFreshness.value = {
    level: payload.level || 'YELLOW',
    label: payload.label,
    detail: payload.detail || '',
    route: payload.route,
  }
}

export function clearDataFreshness() {
  dataFreshness.value = null
}
