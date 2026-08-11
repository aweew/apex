<script setup>
import { Loading, Share } from '@element-plus/icons-vue'

defineProps({
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  label: { type: String, default: '分享截图' },
})

defineEmits(['click'])
</script>

<template>
  <button
    type="button"
    class="floating-share-button"
    :class="{ 'is-loading': loading }"
    :disabled="disabled || loading"
    :aria-label="loading ? '正在生成分享截图' : label"
    :title="loading ? '正在生成…' : label"
    @click="$emit('click')"
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
  transition: transform 160ms ease, box-shadow 160ms ease, opacity 160ms ease;
}

.floating-share-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 11px 28px rgba(0, 84, 173, 0.34);
}

.floating-share-button:active {
  transform: scale(0.96);
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
