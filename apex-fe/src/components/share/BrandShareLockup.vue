<script setup>
import { BRAND } from '../../brand/identity.js'

defineProps({
  /** 模块副标题，如「个股研判」 */
  subtitle: { type: String, default: '' },
  /** 图标边长 px */
  size: { type: Number, default: 44 },
  /** light | dark */
  theme: { type: String, default: 'light' },
})
</script>

<template>
  <div class="brand-share-lockup" :data-theme="theme">
    <img
      class="brand-share-logo"
      :src="BRAND.assets.markShare"
      :alt="BRAND.nameZh"
      :width="size"
      :height="size"
    />
    <div class="brand-share-text">
      <span class="brand-share-zh">{{ BRAND.nameZh }}</span>
      <span class="brand-share-en">{{ BRAND.nameEn }}</span>
      <template v-if="subtitle">
        <span class="brand-share-rule" aria-hidden="true" />
        <span class="brand-share-sub">{{ subtitle }}</span>
      </template>
    </div>
  </div>
</template>

<style scoped>
.brand-share-lockup {
  display: flex;
  align-items: center;
  gap: 12px;
  white-space: nowrap;
  min-width: 0;
}

.brand-share-logo {
  object-fit: contain;
  flex: 0 0 auto;
  border-radius: 50%;
  display: block;
}

.brand-share-text {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.brand-share-zh {
  font-size: 18px;
  font-weight: 750;
  letter-spacing: 0.06em;
  line-height: 1;
  color: #1d1d1f;
}

.brand-share-en {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  line-height: 1;
  color: #6e6e73;
  text-transform: uppercase;
  font-family: Arial, 'Plus Jakarta Sans', sans-serif;
}

.brand-share-rule {
  width: 1px;
  height: 14px;
  background: rgba(0, 0, 0, 0.14);
  flex: 0 0 auto;
  align-self: center;
}

.brand-share-sub {
  font-size: 13px;
  font-weight: 700;
  color: #515154;
}

.brand-share-lockup[data-theme='dark'] .brand-share-zh {
  color: #f5f5f7;
}

.brand-share-lockup[data-theme='dark'] .brand-share-en,
.brand-share-lockup[data-theme='dark'] .brand-share-sub {
  color: rgba(245, 245, 247, 0.62);
}

.brand-share-lockup[data-theme='dark'] .brand-share-rule {
  background: rgba(245, 245, 247, 0.22);
}
</style>
