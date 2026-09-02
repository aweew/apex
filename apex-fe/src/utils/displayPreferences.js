import { ref } from 'vue'

export const SHOW_DASHBOARD_KLINE_KEY = 'apex.ui.show-dashboard-kline'

function readBooleanPreference(key, fallback) {
  try {
    const stored = localStorage.getItem(key)
    if (stored === null) return fallback
    return stored === 'true'
  } catch {
    return fallback
  }
}

export const showDashboardKline = ref(readBooleanPreference(SHOW_DASHBOARD_KLINE_KEY, true))

export function setShowDashboardKline(enabled) {
  const nextValue = Boolean(enabled)
  showDashboardKline.value = nextValue
  try {
    localStorage.setItem(SHOW_DASHBOARD_KLINE_KEY, String(nextValue))
  } catch {
    // 当前页面仍然应用该偏好。
  }
}
