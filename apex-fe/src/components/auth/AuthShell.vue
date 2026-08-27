<script setup>
import { BRAND } from '../../brand/identity.js'

defineProps({
  eyebrow: { type: String, default: '' },
  brandName: { type: String, default: BRAND.nameZh },
  title: { type: String, required: true },
  compact: { type: Boolean, default: false },
  description: { type: String, required: true },
})
</script>

<template>
  <main class="auth-page">
    <div class="apex-geometry" aria-hidden="true">
      <span class="apex-letter apex-letter--a">A</span>
      <span class="apex-letter apex-letter--p">P</span>
      <span class="apex-letter apex-letter--e">E</span>
      <span class="apex-letter apex-letter--x">X</span>
      <i class="geometry-line geometry-line--one"></i>
      <i class="geometry-line geometry-line--two"></i>
      <i class="geometry-point geometry-point--one"></i>
      <i class="geometry-point geometry-point--two"></i>
    </div>
    <div class="auth-layout">
      <section class="brand-stage" :aria-label="`${brandName} ${BRAND.nameEn}`">
        <div class="brand-lockup">
          <img :src="BRAND.assets.mark" :alt="`${brandName} ${BRAND.nameEn}`" />
          <strong>{{ brandName }}</strong>
          <span>{{ BRAND.nameEn }}</span>
        </div>
        <small class="brand-positioning">QUANT RESEARCH</small>
      </section>

      <section class="auth-panel" :class="{ 'auth-panel--compact': compact }" aria-labelledby="auth-title">
        <p v-if="eyebrow" class="eyebrow">{{ eyebrow }}</p>
        <h1 id="auth-title">{{ title }}</h1>
        <p class="description">{{ description }}</p>
        <slot />
      </section>
    </div>
  </main>
</template>

<style scoped>
.auth-page {
  --auth-font: -apple-system, BlinkMacSystemFont, "SF Pro Text", "PingFang SC", "Helvetica Neue", Arial, sans-serif;
  --page-bg: #f5f5f7;
  --ink: #1d1d1f;
  --muted: #6e6e73;
  --panel-bg: #ffffff;
  --panel-border: #d2d2d7;
  --field-bg: #ffffff;
  --field-border: #d2d2d7;
  --field-text: #1d1d1f;
  --field-placeholder: #86868b;
  --field-icon: #6e6e73;
  min-height: 100vh;
  min-height: 100dvh;
  display: grid;
  place-items: center;
  padding: 36px max(24px, env(safe-area-inset-right)) max(36px, env(safe-area-inset-bottom)) max(24px, env(safe-area-inset-left));
  overflow-x: hidden;
  overflow-y: auto;
  position: relative;
  isolation: isolate;
  background: #f5f5f7;
  color: var(--ink);
  color-scheme: light;
  font-family: var(--auth-font);
}

.auth-page::before {
  content: '';
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background-image:
    repeating-linear-gradient(90deg, transparent 0, transparent 47px, rgba(0, 102, 204, .045) 47px, rgba(0, 102, 204, .045) 48px),
    repeating-linear-gradient(0deg, transparent 0, transparent 47px, rgba(0, 102, 204, .045) 47px, rgba(0, 102, 204, .045) 48px);
}

.auth-layout { width: min(100%, 440px); position: relative; z-index: 1; }
.brand-stage { display: flex; flex-direction: column; align-items: center; padding: 0 0 24px; }
.brand-lockup { display: flex; align-items: center; gap: 10px; }
.brand-lockup img { width: 36px; height: 36px; object-fit: contain; }
.brand-lockup strong { font-size: 21px; font-weight: 600; letter-spacing: 0; }
.brand-lockup span { color: #6e6e73; font-size: 12px; font-weight: 600; letter-spacing: 0; text-transform: uppercase; }
.brand-positioning { margin-top: 7px; color: #86868b; font-size: 10px; font-weight: 500; letter-spacing: 0; line-height: 1.4; }

.apex-geometry { position: fixed; right: clamp(24px, 5vw, 72px); bottom: clamp(20px, 4vh, 48px); z-index: 0; display: grid; grid-template-columns: repeat(4, 1fr); align-items: end; width: clamp(360px, 40vw, 560px); height: clamp(110px, 10vw, 150px); padding: 0 16px 18px; color: #0066cc; opacity: .12; pointer-events: none; }
.apex-letter { position: static; font-family: var(--auth-font); font-size: clamp(78px, 7vw, 112px); font-weight: 700; line-height: .8; letter-spacing: 0; text-align: center; opacity: .28; user-select: none; }
.geometry-line { position: absolute; display: block; height: 1px; background: currentColor; opacity: .42; transform-origin: left center; }
.geometry-line--one { right: 16px; bottom: 16px; left: 16px; }
.geometry-line--two { width: calc(100% - 80px); left: 40px; bottom: 38%; opacity: .2; transform: rotate(-8deg); }
.geometry-point { position: absolute; display: block; width: 8px; height: 8px; border: 1px solid currentColor; border-radius: 50%; background: var(--page-bg); }
.geometry-point--one { left: 12px; bottom: 12px; }
.geometry-point--two { right: 12px; bottom: 12px; }

.auth-panel { padding: 34px 32px 30px; border: 1px solid var(--panel-border); border-radius: 8px; background: var(--panel-bg); box-shadow: 0 12px 36px rgba(0, 0, 0, .065); }
.auth-panel--compact { padding-top: 32px; padding-bottom: 28px; }
h1 { margin: 0; color: var(--ink); font-size: 28px; font-weight: 600; line-height: 1.3; letter-spacing: 0; }
.eyebrow { margin: 0 0 10px; color: #0066cc; font-size: 10px; font-weight: 700; letter-spacing: 0; line-height: 1.4; }
.description { margin: 7px 0 24px; color: var(--muted); font-size: 14px; line-height: 1.55; }
.auth-panel--compact .description { margin-bottom: 24px; }
:deep(.el-form-item) { position: relative; margin-bottom: 2px; padding-bottom: 20px; }
.auth-panel--compact :deep(.el-form-item) { margin-bottom: 4px; }
:deep(.el-form-item__label) { color: var(--ink); font-family: var(--auth-font); font-size: 14px; font-weight: 600; line-height: 1.3; padding-bottom: 8px; }
:deep(.el-form-item__error) { position: absolute; top: auto; bottom: -20px; left: 0; width: 100%; height: 20px; padding-top: 4px; overflow: hidden; color: #d70015; font-size: 12px; line-height: 16px; white-space: nowrap; text-overflow: ellipsis; }
:deep(.el-input__wrapper) { min-height: 52px; padding: 1px 15px; border-radius: 8px; background: var(--field-bg); box-shadow: 0 0 0 1px var(--field-border) inset !important; transition: box-shadow .18s ease, background .18s ease; }
:deep(.el-input__inner) { color: var(--field-text); font-size: 16px; }
:deep(.el-input__inner::placeholder) { color: var(--field-placeholder); }
:deep(.el-input__prefix-inner) { color: var(--field-icon); }
:deep(.el-input__prefix-inner .el-icon) { font-size: 17px; }
:deep(.el-input__wrapper:hover) { background: #ffffff; box-shadow: 0 0 0 1px #a1a1a6 inset !important; }
:deep(.el-input__wrapper.is-focus) { background: #ffffff; box-shadow: 0 0 0 3px rgba(0, 113, 227, .16), 0 0 0 1px #0071e3 inset !important; }
:deep(.el-form-item.is-error .el-input__wrapper) { box-shadow: 0 0 0 1px #d70015 inset !important; }
:deep(.el-input.is-disabled .el-input__wrapper) { background: #eeeeef; box-shadow: 0 0 0 1px #dfdfe1 inset !important; }

@media (max-width: 820px) {
  .auth-page { padding-top: 28px; padding-bottom: max(28px, env(safe-area-inset-bottom)); }
  .auth-layout { width: min(100%, 440px); }
  .apex-geometry { display: none; }
  .auth-panel { padding: 32px 28px 28px; }
}

@media (max-width: 390px) {
  .auth-page { padding-right: 22px; padding-left: 22px; }
  .brand-lockup { gap: 9px; }
  .brand-lockup strong { font-size: 20px; }
  .brand-lockup span { font-size: 12px; }
  .auth-panel { padding: 28px 24px 24px; }
  h1 { font-size: 25px; }
}

@media (prefers-reduced-motion: reduce) {
  :deep(.el-input__wrapper) { transition: none; }
}
</style>
