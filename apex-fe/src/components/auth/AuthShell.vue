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

.apex-geometry { position: fixed; right: -64px; bottom: -50px; z-index: 0; width: 600px; aspect-ratio: 1.15; overflow: hidden; color: #0066cc; opacity: .18; pointer-events: none; }
.apex-letter { position: absolute; font-family: var(--auth-font); font-size: 180px; font-weight: 700; line-height: .8; letter-spacing: 0; opacity: .18; user-select: none; }
.apex-letter--a { left: 2%; bottom: 15%; }
.apex-letter--p { left: 27%; top: 5%; }
.apex-letter--e { left: 51%; bottom: 16%; }
.apex-letter--x { right: 0; top: 17%; }
.geometry-line { position: absolute; display: block; height: 1px; background: currentColor; opacity: .42; transform-origin: left center; }
.geometry-line--one { width: 78%; left: 10%; top: 52%; transform: rotate(-23deg); }
.geometry-line--two { width: 64%; left: 29%; top: 25%; transform: rotate(49deg); }
.geometry-point { position: absolute; display: block; width: 8px; height: 8px; border: 1px solid currentColor; border-radius: 50%; background: var(--page-bg); }
.geometry-point--one { left: 8%; top: 58%; }
.geometry-point--two { right: 7%; bottom: 17%; }

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
  .apex-geometry { right: -150px; bottom: -48px; width: 440px; opacity: .12; }
  .apex-letter { font-size: 132px; }
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

@media (prefers-color-scheme: dark) {
  .auth-page { --page-bg: #000000; --ink: #f5f5f7; --muted: #a1a1a6; --panel-bg: #1c1c1e; --panel-border: #3a3a3c; --field-bg: #2c2c2e; --field-border: #48484a; --field-text: #f5f5f7; --field-placeholder: #8e8e93; --field-icon: #a1a1a6; background: #000000; color-scheme: dark; }
  .auth-page::before {
    background-image:
      repeating-linear-gradient(90deg, transparent 0, transparent 47px, rgba(10, 132, 255, .075) 47px, rgba(10, 132, 255, .075) 48px),
      repeating-linear-gradient(0deg, transparent 0, transparent 47px, rgba(10, 132, 255, .075) 47px, rgba(10, 132, 255, .075) 48px);
  }
  .brand-lockup span,
  .brand-positioning { color: #a1a1a6; }
  .apex-geometry,
  .eyebrow { color: #0a84ff; }
  .auth-panel { box-shadow: 0 16px 42px rgba(0, 0, 0, .32); }
  :deep(.el-input__wrapper:hover) { background: #323234; box-shadow: 0 0 0 1px #636366 inset !important; }
  :deep(.el-input__wrapper.is-focus) { background: #2c2c2e; box-shadow: 0 0 0 3px rgba(10, 132, 255, .22), 0 0 0 1px #0a84ff inset !important; }
  :deep(.el-form-item__error) { color: #ff453a; }
  :deep(.el-form-item.is-error .el-input__wrapper) { box-shadow: 0 0 0 1px #ff453a inset !important; }
}

@media (prefers-reduced-motion: reduce) {
  :deep(.el-input__wrapper) { transition: none; }
}
</style>
