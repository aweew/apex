<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowUp } from '@element-plus/icons-vue'

const visible = ref(false)

function syncVisibility() {
  const scrollTop = window.scrollY || document.documentElement.scrollTop || 0
  visible.value = scrollTop > window.innerHeight
}

function backToTop() {
  const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  const behavior = reduceMotion ? 'auto' : 'smooth'
  window.scrollTo({ top: 0, behavior })
}

onMounted(() => {
  syncVisibility()
  window.addEventListener('scroll', syncVisibility, { passive: true })
  window.addEventListener('resize', syncVisibility)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', syncVisibility)
  window.removeEventListener('resize', syncVisibility)
})
</script>

<template>
  <Transition name="back-to-top">
    <button
      v-show="visible"
      type="button"
      class="back-to-top-button"
      aria-label="回到顶部"
      title="回到顶部"
      @click="backToTop"
    >
      <el-icon><ArrowUp /></el-icon>
    </button>
  </Transition>
</template>

<style scoped>
.back-to-top-button {
  position: fixed;
  right: max(16px, calc((100vw - 1240px) / 2));
  bottom: calc(68px + env(safe-area-inset-bottom));
  z-index: 90;
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  padding: 0;
  border: 1px solid rgba(18, 42, 66, 0.12);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.82);
  color: var(--el-color-primary);
  box-shadow: 0 4px 12px rgba(18, 42, 66, 0.1);
  opacity: 0.78;
  cursor: pointer;
  backdrop-filter: blur(10px) saturate(140%);
  -webkit-backdrop-filter: blur(10px) saturate(140%);
  -webkit-tap-highlight-color: transparent;
  transition: opacity 160ms ease, transform 160ms ease, box-shadow 160ms ease;
}

.back-to-top-button:hover {
  opacity: 1;
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(18, 42, 66, 0.15);
}

.back-to-top-button:active {
  transform: scale(0.96);
}

.back-to-top-button:focus-visible {
  outline: 3px solid rgba(0, 113, 227, 0.22);
  outline-offset: 3px;
  opacity: 1;
}

.back-to-top-button .el-icon {
  width: 18px;
  height: 18px;
  font-size: 18px;
}

.back-to-top-enter-active,
.back-to-top-leave-active {
  transition: opacity 160ms ease, transform 160ms ease;
}

.back-to-top-enter-from,
.back-to-top-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

@media (max-width: 820px) {
  .back-to-top-button {
    right: 16px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .back-to-top-button,
  .back-to-top-enter-active,
  .back-to-top-leave-active {
    transition: none;
  }
}
</style>
