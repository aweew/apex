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
    <div class="auth-layout">
      <section class="brand-stage" :aria-label="`${brandName} ${BRAND.nameEn}`">
        <div class="brand-lockup">
          <img :src="BRAND.assets.mark" :alt="`${brandName} ${BRAND.nameEn}`" />
          <strong>{{ brandName }}</strong>
          <span>{{ BRAND.nameEn }}</span>
        </div>
        <small class="brand-positioning">QUANT RESEARCH</small>
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
  --page-bg: #edf3f8;
  --ink: #13263a;
  --muted: rgba(19, 38, 58, .62);
  --panel-bg: #ffffff;
  --panel-border: #d8e2ed;
  --field-bg: #ffffff;
  --field-border: #bfccda;
  --field-text: #13263a;
  --field-placeholder: rgba(19, 38, 58, .42);
  --field-icon: #0d58af;
  min-height: 100vh;
  min-height: 100dvh;
  display: grid;
  place-items: center;
  padding: 40px max(28px, env(safe-area-inset-right)) max(40px, env(safe-area-inset-bottom)) max(28px, env(safe-area-inset-left));
  overflow: hidden;
  position: relative;
  isolation: isolate;
  background: linear-gradient(135deg, #eef4f8 0%, #f9fbfd 50%, #e8f0f6 100%);
  color: var(--ink);
  color-scheme: light;
}

.auth-page::before,
.auth-page::after { content: ''; position: absolute; z-index: 0; pointer-events: none; }
.auth-page::before { inset: 0; background-image: linear-gradient(rgba(13, 88, 175, .035) 1px, transparent 1px), linear-gradient(90deg, rgba(13, 88, 175, .035) 1px, transparent 1px); background-size: 42px 42px; }
.auth-page::after { width: 46vw; height: 46vw; right: -18vw; bottom: -20vw; border: 1px solid rgba(13, 88, 175, .1); border-radius: 50%; box-shadow: 0 0 0 64px rgba(255, 255, 255, .25), 0 0 0 132px rgba(13, 88, 175, .025); }

.auth-layout { width: min(100%, 440px); position: relative; z-index: 1; }
.brand-stage { display: flex; flex-direction: column; align-items: center; padding: 0 0 28px; }
.brand-lockup { display: flex; align-items: center; gap: 11px; }
.brand-lockup img { width: 38px; height: 38px; object-fit: contain; }
.brand-lockup strong { font-size: 22px; letter-spacing: 0; }
.brand-lockup span { color: #31516d; font-size: 13px; font-weight: 800; letter-spacing: .16em; text-transform: uppercase; }
.brand-positioning { margin-top: 8px; color: var(--muted); font-size: 9px; font-weight: 600; letter-spacing: .15em; line-height: 1.4; }

.apex-geometry { position: absolute; right: -46vw; bottom: -42vh; width: min(56vw, 680px); aspect-ratio: 1.15; overflow: hidden; color: #0d58af; opacity: .32; pointer-events: none; }
.apex-letter { position: absolute; font-family: var(--font-display); font-size: clamp(118px, 16vw, 220px); font-weight: 800; line-height: .8; letter-spacing: 0; opacity: .12; user-select: none; }
.apex-letter--a { left: 2%; bottom: 15%; }
.apex-letter--p { left: 27%; top: 5%; }
.apex-letter--e { left: 51%; bottom: 16%; }
.apex-letter--x { right: 0; top: 17%; }
.geometry-line { position: absolute; display: block; height: 1px; background: rgba(13, 88, 175, .42); transform-origin: left center; }
.geometry-line--one { width: 78%; left: 10%; top: 52%; transform: rotate(-23deg); }
.geometry-line--two { width: 64%; left: 29%; top: 25%; transform: rotate(49deg); }
.geometry-point { position: absolute; display: block; width: 9px; height: 9px; border: 2px solid #0d58af; border-radius: 50%; background: var(--page-bg); }
.geometry-point--one { left: 8%; top: 58%; }
.geometry-point--two { right: 7%; bottom: 17%; }

.auth-panel { padding: 32px 30px 28px; border: 1px solid rgba(156, 181, 204, .48); border-radius: 14px; background: rgba(255, 255, 255, .82); box-shadow: 0 18px 44px rgba(30, 66, 98, .1); backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px); }
.auth-panel--compact { padding-top: 30px; padding-bottom: 26px; }
h1 { margin: 0; color: var(--ink); font-size: 27px; font-weight: 600; line-height: 1.35; letter-spacing: 0; }
.eyebrow { margin: 0 0 10px; color: #0d58af; font-size: 10px; font-weight: 800; letter-spacing: .16em; line-height: 1.4; }
.description { margin: 6px 0 22px; color: var(--muted); font-size: 14px; line-height: 1.65; }
.auth-panel--compact .description { margin-bottom: 26px; }
:deep(.el-form-item) { margin-bottom: 12px; }
.auth-panel--compact :deep(.el-form-item) { margin-bottom: 16px; }
:deep(.el-form-item__label) { color: var(--ink); font-size: 14px; font-weight: 600; line-height: 1.3; padding-bottom: 8px; }
:deep(.el-input__wrapper) { min-height: 54px; padding: 1px 15px; border-radius: 6px; background: var(--field-bg); box-shadow: 0 0 0 1px rgba(42, 78, 108, .2) inset !important; transition: box-shadow .18s ease, background .18s ease; }
:deep(.el-input__inner) { color: var(--field-text); font-size: 16px; }
:deep(.el-input__inner::placeholder) { color: var(--field-placeholder); }
:deep(.el-input__prefix-inner) { color: var(--field-icon); }
:deep(.el-input__prefix-inner .el-icon) { font-size: 17px; }
:deep(.el-input__wrapper:hover) { background: #fff; box-shadow: 0 0 0 1px rgba(22, 105, 201, .45) inset !important; }
:deep(.el-input__wrapper.is-focus) { background: #fff; box-shadow: 0 0 0 3px rgba(22, 105, 201, .1), 0 0 0 1px #1669c9 inset !important; }
:deep(.el-form-item.is-error .el-input__wrapper) { box-shadow: 0 0 0 1px var(--el-color-danger) inset !important; }
:deep(.el-input.is-disabled .el-input__wrapper) { background: rgba(230, 237, 244, .62); box-shadow: 0 0 0 1px rgba(42, 78, 108, .12) inset !important; }

@media (max-width: 820px) {
  .auth-page { padding-top: 28px; padding-bottom: max(28px, env(safe-area-inset-bottom)); }
  .auth-layout { width: min(100%, 440px); }
  .apex-geometry { right: -47vw; bottom: -26vh; width: 94vw; opacity: .28; }
  .auth-panel { padding: 30px 28px 26px; }
}

@media (max-width: 390px) {
  .auth-page { padding-right: 28px; padding-left: 28px; }
  .brand-lockup { gap: 9px; }
  .brand-lockup strong { font-size: 21px; }
  .brand-lockup span { font-size: 12px; }
  .auth-panel { padding: 26px 26px 22px; }
  h1 { font-size: 26px; }
}

@media (prefers-color-scheme: dark) {
  .auth-page { --page-bg: #0b1722; --ink: #f4f8fb; --muted: rgba(231, 241, 246, .66); --panel-bg: #142a39; --panel-border: rgba(118, 164, 184, .24); --field-bg: rgba(4, 16, 25, .38); --field-border: rgba(205, 231, 239, .18); --field-text: #fff; --field-placeholder: rgba(231, 241, 246, .42); --field-icon: #61d1d7; background: #0b1722; color-scheme: dark; }
  .auth-page::before { opacity: .16; }
  .auth-page::after { border-color: rgba(96, 209, 215, .22); box-shadow: 0 0 0 42px rgba(20, 120, 212, .05), 0 0 0 100px rgba(96, 209, 215, .035); }
  .brand-lockup span { color: #a7d3e8; }
  .apex-geometry { color: #61d1d7; }
  .geometry-line { background: rgba(97, 209, 215, .42); }
  .geometry-point { border-color: #61d1d7; background: var(--page-bg); }
  .auth-panel { box-shadow: 0 22px 50px rgba(0, 0, 0, .22); }
  :deep(.el-input__wrapper:hover) { background: rgba(4, 16, 25, .54); box-shadow: 0 0 0 1px rgba(97, 209, 215, .46) inset !important; }
  :deep(.el-input__wrapper.is-focus) { background: rgba(4, 16, 25, .6); box-shadow: 0 0 0 2px rgba(97, 209, 215, .18), 0 0 0 1px #61d1d7 inset !important; }
}
</style>
