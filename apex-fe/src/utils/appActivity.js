import { computed, readonly, ref } from 'vue'

const activeRequestCount = ref(0)
const activeNavigationId = ref(0)
let navigationSequence = 0

export const requestCount = readonly(activeRequestCount)
export const isNavigating = computed(() => activeNavigationId.value > 0)

export function beginRequestActivity() {
  activeRequestCount.value += 1
  let finished = false

  return () => {
    if (finished) return
    finished = true
    activeRequestCount.value = Math.max(0, activeRequestCount.value - 1)
  }
}

export function beginNavigationActivity() {
  const navigationId = ++navigationSequence
  activeNavigationId.value = navigationId

  return () => {
    if (activeNavigationId.value === navigationId) {
      activeNavigationId.value = 0
    }
  }
}
