<script setup>
import { BRAND } from '../../brand/identity.js'

defineProps({
  eyebrow: { type: String, required: true },
  title: { type: String, required: true },
  accessibleTitle: { type: String, default: '' },
  titleTone: { type: String, default: 'heading' },
  compact: { type: Boolean, default: false },
  description: { type: String, required: true },
})
</script>

<template>
  <main class="auth-page">
    <div class="auth-layout">
      <section class="brand-stage" aria-label="灵枢 Apex">
        <div class="brand-lockup">
          <img :src="BRAND.assets.mark" :alt="`${BRAND.nameZh} ${BRAND.nameEn}`" />
          <div>
            <strong>{{ BRAND.nameZh }}</strong>
            <span>{{ BRAND.nameEn }}</span>
          </div>
        </div>
      </section>

      <section class="auth-panel" :class="{ 'auth-panel--compact': compact }" :aria-labelledby="`${eyebrow}-title`">
        <template v-if="titleTone === 'access'">
          <h1 :id="`${eyebrow}-title`" class="sr-only">{{ accessibleTitle || title }}</h1>
          <p class="access-title" aria-hidden="true">{{ title }}</p>
        </template>
        <h1 v-else :id="`${eyebrow}-title`">{{ title }}</h1>
        <p class="description">{{ description }}</p>
        <slot />
      </section>
    </div>
  </main>
</template>

<style scoped>
.auth-page {
  --page-bg: #e3f0f5;
  --ink: #172532;
  --muted: rgba(28, 57, 75, .66);
  --panel-bg: rgba(251, 254, 255, .74);
  --panel-border: rgba(79, 122, 145, .2);
  --field-bg: rgba(255, 255, 255, .56);
  --field-border: rgba(24, 62, 83, .16);
  --field-text: #172532;
  --field-placeholder: rgba(28, 57, 75, .42);
  --field-icon: #1478d4;
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 40px 24px;
  overflow: hidden;
  position: relative;
  isolation: isolate;
  background: var(--page-bg);
  color: var(--ink);
  color-scheme: light;
}

.auth-page::before,
.auth-page::after {
  content: '';
  position: absolute;
  z-index: 0;
  pointer-events: none;
}

.auth-page::before {
  width: min(64vw, 640px);
  aspect-ratio: 1;
  top: -9%;
  right: -10%;
  background: url('/brand/arc-mark.svg') center / contain no-repeat;
  opacity: .14;
  transform: rotate(14deg);
}

.auth-page::after {
  inset: auto -11% 2% auto;
  width: min(46vw, 560px);
  height: min(46vw, 560px);
  border: 1px solid rgba(20, 120, 212, .16);
  border-radius: 50%;
  box-shadow: 0 0 0 42px rgba(255, 255, 255, .24), 0 0 0 100px rgba(97, 209, 215, .06);
}

.auth-layout {
  width: min(100%, 410px);
  position: relative;
  z-index: 1;
}

.brand-stage {
  padding: 0 4px 26px;
}

.brand-lockup {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 11px;
}

.brand-lockup img { width: 34px; height: 34px; object-fit: contain; }
.brand-lockup div { display: flex; align-items: baseline; gap: 8px; }
.brand-lockup strong { font-size: 21px; letter-spacing: 0; }
.brand-lockup span { color: var(--muted); font-size: 11px; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; }

.auth-panel {
  padding: 38px 30px 32px;
  border: 1px solid var(--panel-border);
  border-radius: 8px;
  background: var(--panel-bg);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .72), 0 22px 50px rgba(27, 56, 75, .14);
  backdrop-filter: blur(30px) saturate(150%);
  -webkit-backdrop-filter: blur(30px) saturate(150%);
}

h1 { margin: 0; color: var(--ink); font-size: 32px; line-height: 1.35; letter-spacing: 0; }
.access-title { margin: 0; color: #f2b84b; font-size: 12px; font-weight: 700; line-height: 1.4; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
.description { margin: 14px 0 34px; color: var(--muted); font-size: 14px; line-height: 1.65; }

:deep(.el-form-item) { margin-bottom: 20px; }
.auth-panel--compact { padding-top: 30px; padding-bottom: 26px; }
.auth-panel--compact .description { margin-bottom: 26px; }
.auth-panel--compact :deep(.el-form-item) { margin-bottom: 16px; }
:deep(.el-form-item__label) { color: var(--ink); font-size: 13px; font-weight: 600; line-height: 1.3; padding-bottom: 9px; }
:deep(.el-input__wrapper) { min-height: 50px; padding: 1px 14px; border-radius: 6px; background: var(--field-bg); box-shadow: 0 0 0 1px var(--field-border) inset, 0 5px 14px rgba(39, 79, 99, .035) !important; transition: box-shadow .2s ease, background .2s ease; }
:deep(.el-input__inner) { color: var(--field-text); }
:deep(.el-input__inner::placeholder) { color: var(--field-placeholder); }
:deep(.el-input__prefix-inner) { color: var(--field-icon); }
:deep(.el-input__prefix-inner .el-icon) { font-size: 17px; }
:deep(.el-input__wrapper:hover) { background: rgba(255, 255, 255, .92); box-shadow: 0 0 0 1px rgba(20, 120, 212, .3) inset, 0 5px 14px rgba(39, 79, 99, .05) !important; }
:deep(.el-input__wrapper.is-focus) { background: rgba(255, 255, 255, .94); box-shadow: 0 0 0 2px rgba(20, 120, 212, .18), 0 0 0 1px #1478d4 inset !important; }

@media (max-width: 760px) {
  .auth-page { padding: 28px 20px; }
  .auth-panel { padding: 36px 26px 28px; }
}

@media (max-width: 390px) {
  .auth-page { padding: 24px 16px; }
  .auth-panel { padding: 34px 22px 26px; }
}

@media (prefers-color-scheme: dark) {
  .auth-page {
    --page-bg: #0b1722;
    --ink: #f4f8fb;
    --muted: rgba(231, 241, 246, .66);
    --panel-bg: rgba(20, 42, 57, .72);
    --panel-border: rgba(118, 164, 184, .24);
    --field-bg: rgba(4, 16, 25, .38);
    --field-border: rgba(205, 231, 239, .18);
    --field-text: #fff;
    --field-placeholder: rgba(231, 241, 246, .42);
    --field-icon: #61d1d7;
    color-scheme: dark;
  }

  .auth-page::before { opacity: .16; }
  .auth-page::after {
    border-color: rgba(96, 209, 215, .22);
    box-shadow: 0 0 0 42px rgba(20, 120, 212, .05), 0 0 0 100px rgba(96, 209, 215, .035);
  }

  .auth-panel {
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, .12), 0 22px 50px rgba(0, 0, 0, .22);
  }
  :deep(.el-input__wrapper:hover) {
    background: rgba(4, 16, 25, .54);
    box-shadow: 0 0 0 1px rgba(97, 209, 215, .46) inset, 0 5px 14px rgba(0, 0, 0, .12) !important;
  }
  :deep(.el-input__wrapper.is-focus) {
    background: rgba(4, 16, 25, .6);
    box-shadow: 0 0 0 2px rgba(97, 209, 215, .18), 0 0 0 1px #61d1d7 inset !important;
  }
}
</style>
