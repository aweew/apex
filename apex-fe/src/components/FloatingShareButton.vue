<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { Loading, Share } from '@element-plus/icons-vue'
import {
  clampFloatingPosition,
  floatingPositionFromRatio,
  floatingPositionToRatio,
} from '../utils/floatingPosition.js'

const props = defineProps({
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  label: { type: String, default: '分享截图' },
})

const emit = defineEmits(['click'])
const buttonRef = ref(null)
const dragging = ref(false)
const POSITION_KEY = 'apex.ui.floatingSharePosition'
let dragState = null
let savedRatio = null
let suppressClick = false
let suppressClickTimer

function positionTarget() {
  return buttonRef.value?.closest('.floating-share-dropdown') || buttonRef.value
}

function boundsOf(target) {
  const rect = target.getBoundingClientRect()
  return {
    viewportWidth: window.visualViewport?.width || window.innerWidth,
    viewportHeight: window.visualViewport?.height || window.innerHeight,
    width: rect.width,
    height: rect.height,
  }
}

function setTargetPosition(target, position) {
  target.style.left = `${position.left}px`
  target.style.top = `${position.top}px`
  target.style.right = 'auto'
  target.style.bottom = 'auto'
}

function applySavedPosition() {
  const target = positionTarget()
  if (!target || !savedRatio) return
  setTargetPosition(target, floatingPositionFromRatio(savedRatio, boundsOf(target)))
}

function savePosition(target) {
  const rect = target.getBoundingClientRect()
  savedRatio = floatingPositionToRatio({ left: rect.left, top: rect.top }, boundsOf(target))
  try {
    localStorage.setItem(POSITION_KEY, JSON.stringify(savedRatio))
  } catch {
    /* ignore unavailable browser storage */
  }
}

function onPointerDown(event) {
  if (props.disabled || props.loading || (event.pointerType === 'mouse' && event.button !== 0)) return
  const target = positionTarget()
  if (!target) return
  const rect = target.getBoundingClientRect()
  dragState = {
    pointerId: event.pointerId,
    startX: event.clientX,
    startY: event.clientY,
    left: rect.left,
    top: rect.top,
    moved: false,
  }
  buttonRef.value?.setPointerCapture?.(event.pointerId)
}

function onPointerMove(event) {
  if (!dragState || dragState.pointerId !== event.pointerId) return
  const deltaX = event.clientX - dragState.startX
  const deltaY = event.clientY - dragState.startY
  if (!dragState.moved && Math.hypot(deltaX, deltaY) < 5) return
  const target = positionTarget()
  if (!target) return
  event.preventDefault()
  dragState.moved = true
  dragging.value = true
  setTargetPosition(
    target,
    clampFloatingPosition(
      { left: dragState.left + deltaX, top: dragState.top + deltaY },
      boundsOf(target),
    ),
  )
}

function finishPointer(event) {
  if (!dragState || dragState.pointerId !== event.pointerId) return
  const moved = dragState.moved
  const target = positionTarget()
  try {
    buttonRef.value?.releasePointerCapture?.(event.pointerId)
  } catch {
    // Pointer capture may already be released after a cancelled gesture.
  }
  dragState = null
  dragging.value = false
  if (!moved || !target) return
  savePosition(target)
  suppressClick = true
  clearTimeout(suppressClickTimer)
  suppressClickTimer = window.setTimeout(() => {
    suppressClick = false
  }, 300)
}

function onClick(event) {
  if (suppressClick) {
    suppressClick = false
    event.preventDefault()
    event.stopPropagation()
    return
  }
  emit('click')
}

function onResize() {
  window.requestAnimationFrame(applySavedPosition)
}

onMounted(async () => {
  try {
    const stored = JSON.parse(localStorage.getItem(POSITION_KEY) || 'null')
    if (Number.isFinite(stored?.x) && Number.isFinite(stored?.y)) savedRatio = stored
  } catch {
    savedRatio = null
  }
  await nextTick()
  applySavedPosition()
  window.addEventListener('resize', onResize)
  window.visualViewport?.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  clearTimeout(suppressClickTimer)
  window.removeEventListener('resize', onResize)
  window.visualViewport?.removeEventListener('resize', onResize)
})
</script>

<template>
  <button
    ref="buttonRef"
    type="button"
    class="floating-share-button"
    :class="{ 'is-loading': loading, 'is-dragging': dragging }"
    :disabled="disabled || loading"
    :aria-label="loading ? '正在生成分享截图' : label"
    :title="loading ? '正在生成…' : label"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="finishPointer"
    @pointercancel="finishPointer"
    @click="onClick"
  >
    <el-icon :class="{ spinning: loading }">
      <Loading v-if="loading" />
      <Share v-else />
    </el-icon>
  </button>
</template>

<style scoped>
.floating-share-button {
  position: fixed;
  right: max(18px, calc((100vw - 1240px) / 2));
  bottom: max(22px, env(safe-area-inset-bottom));
  z-index: 850;
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  padding: 0;
  border: 1px solid rgba(0, 113, 227, 0.18);
  border-radius: 50%;
  background: var(--accent, #0071e3);
  color: #fff;
  box-shadow: 0 8px 24px rgba(0, 84, 173, 0.28);
  cursor: pointer;
  touch-action: none;
  user-select: none;
  transition: transform 160ms ease, box-shadow 160ms ease, opacity 160ms ease;
}

.floating-share-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 11px 28px rgba(0, 84, 173, 0.34);
}

.floating-share-button:active {
  transform: scale(0.96);
}

.floating-share-button.is-dragging,
.floating-share-button.is-dragging:hover,
.floating-share-button.is-dragging:active {
  cursor: grabbing;
  transform: none;
  transition: none;
}

.floating-share-button:focus-visible {
  outline: 3px solid rgba(0, 113, 227, 0.24);
  outline-offset: 3px;
}

.floating-share-button:disabled {
  opacity: 0.48;
  cursor: not-allowed;
  transform: none;
}

.floating-share-button .el-icon {
  width: 22px;
  height: 22px;
  font-size: 22px;
}

.spinning {
  animation: share-spin 0.9s linear infinite;
}

@keyframes share-spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 820px) {
  .floating-share-button {
    right: 16px;
    bottom: calc(16px + env(safe-area-inset-bottom));
    width: 46px;
    height: 46px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .floating-share-button {
    transition: none;
  }
}
</style>
