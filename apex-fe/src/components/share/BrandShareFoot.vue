<script setup>
import { BRAND } from '../../brand/identity.js'

defineProps({
  /** 右下角说明，如「2026-08-05 · 仅供研究参考 · 不构成投资建议」 */
  note: { type: String, default: '仅供研究参考 · 不构成投资建议' },
  /** light | dark */
  theme: { type: String, default: 'light' },
})
</script>

<template>
  <div class="brand-share-foot" :data-theme="theme">
    <span class="slogan">{{ BRAND.slogan }}</span>
    <span class="note">{{ note }}</span>
  </div>
</template>

<style scoped>
.brand-share-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px solid rgba(0, 0, 0, 0.08);
  white-space: nowrap;
}

.slogan {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #3a3a3c;
}

.note {
  font-size: 11px;
  color: #8e8e93;
  flex: 0 0 auto;
}

.brand-share-foot[data-theme='dark'] {
  border-top-color: rgba(255, 255, 255, 0.08);
}

.brand-share-foot[data-theme='dark'] .slogan {
  color: rgba(245, 245, 247, 0.72);
}

.brand-share-foot[data-theme='dark'] .note {
  color: rgba(245, 245, 247, 0.38);
}
</style>
