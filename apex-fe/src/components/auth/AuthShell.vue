<script setup>
import { BRAND } from '../../brand/identity.js'

defineProps({
  eyebrow: { type: String, required: true },
  title: { type: String, required: true },
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
        <div class="brand-copy">
          <p>{{ BRAND.product }}</p>
          <h2>{{ BRAND.slogan }}</h2>
          <span>PRIVATE RESEARCH TERMINAL</span>
        </div>
        <div class="signal-lines" aria-hidden="true"><i /><i /><i /></div>
      </section>

      <section class="auth-panel" :aria-labelledby="`${eyebrow}-title`">
        <div class="panel-brand">
          <img :src="BRAND.assets.mark" alt="" aria-hidden="true" />
          <span>{{ BRAND.nameZh }} · {{ BRAND.nameEn }}</span>
        </div>
        <p class="eyebrow">{{ eyebrow }}</p>
        <h1 :id="`${eyebrow}-title`">{{ title }}</h1>
        <p class="description">{{ description }}</p>
        <slot />
      </section>
    </div>
  </main>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px;
  overflow: hidden;
  position: relative;
  isolation: isolate;
  background: #07131f;
  color: #f4f8fb;
}

.auth-layout {
  width: min(100%, 1080px);
  min-height: 620px;
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(390px, .9fr);
  border: 1px solid rgba(199, 224, 238, .18);
  border-radius: 8px;
  overflow: hidden;
  position: relative;
  z-index: 1;
  background: rgba(14, 34, 49, .64);
  box-shadow: 0 28px 70px rgba(0, 0, 0, .34);
  backdrop-filter: blur(22px);
  -webkit-backdrop-filter: blur(22px);
}

.brand-stage {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px;
  border-right: 1px solid rgba(199, 224, 238, .14);
  background: rgba(7, 23, 35, .42);
}

.brand-lockup,
.panel-brand {
  display: flex;
  align-items: center;
  gap: 11px;
}

.brand-lockup img { width: 38px; height: 38px; object-fit: contain; }
.brand-lockup div { display: flex; align-items: baseline; gap: 8px; }
.brand-lockup strong { font-size: 22px; letter-spacing: 0; }
.brand-lockup span,
.panel-brand span { color: rgba(230, 241, 247, .64); font-size: 12px; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; }

.brand-copy { max-width: 390px; }
.brand-copy p,
.eyebrow { margin: 0 0 12px; color: #61d1d7; font-size: 12px; font-weight: 700; letter-spacing: .12em; }
.brand-copy h2 { margin: 0 0 16px; font-size: 38px; line-height: 1.25; letter-spacing: 0; }
.brand-copy > span { color: rgba(230, 241, 247, .5); font-size: 12px; letter-spacing: .12em; }

.signal-lines { display: grid; gap: 10px; width: 220px; }
.signal-lines i { display: block; height: 1px; background: rgba(97, 209, 215, .42); }
.signal-lines i:nth-child(2) { width: 72%; background: rgba(242, 184, 75, .72); }
.signal-lines i:nth-child(3) { width: 42%; }

.auth-panel {
  align-self: center;
  margin: 28px;
  padding: 42px;
  border: 1px solid rgba(225, 241, 248, .22);
  border-radius: 8px;
  background: rgba(18, 42, 58, .68);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .09), 0 18px 36px rgba(0, 0, 0, .16);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
}

.panel-brand { margin-bottom: 34px; }
.panel-brand img { width: 26px; height: 26px; object-fit: contain; }
.eyebrow { margin-bottom: 8px; color: #f2b84b; }
h1 { margin: 0; color: #fff; font-size: 30px; line-height: 1.25; letter-spacing: 0; }
.description { margin: 12px 0 28px; color: rgba(231, 241, 246, .7); font-size: 14px; line-height: 1.65; }

:deep(.el-form-item) { margin-bottom: 18px; }
:deep(.el-form-item__label) { color: rgba(239, 247, 250, .82); font-size: 13px; line-height: 1.3; padding-bottom: 8px; }
:deep(.el-input__wrapper) { min-height: 42px; border-radius: 6px; background: rgba(3, 15, 25, .38); box-shadow: 0 0 0 1px rgba(200, 226, 238, .18) inset !important; }
:deep(.el-input__inner) { color: #fff; }
:deep(.el-input__inner::placeholder) { color: rgba(230, 241, 247, .38); }
:deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1px #61d1d7 inset !important; }

@media (max-width: 760px) {
  .auth-page { align-items: start; padding: 16px; }
  .auth-layout { min-height: 0; grid-template-columns: 1fr; }
  .brand-stage { min-height: 176px; padding: 24px; border-right: 0; border-bottom: 1px solid rgba(199, 224, 238, .14); }
  .brand-copy { margin-top: 26px; }
  .brand-copy h2 { font-size: 24px; }
  .brand-copy > span, .signal-lines { display: none; }
  .auth-panel { margin: 0; padding: 30px 24px; border: 0; border-radius: 0; box-shadow: none; }
  .panel-brand { margin-bottom: 28px; }
  h1 { font-size: 27px; }
}

@media (max-width: 390px) {
  .auth-page { padding: 0; }
  .auth-layout { min-height: 100vh; border: 0; border-radius: 0; }
  .brand-stage { min-height: 158px; padding: 22px; }
  .brand-copy { margin-top: 20px; }
  .auth-panel { padding: 26px 22px 32px; }
}

@media (prefers-color-scheme: light) {
  .auth-page { background: #edf3f6; color: #172532; }
  .auth-page::before {
    content: '';
    position: absolute;
    z-index: 0;
    inset: 0;
    background-image: url('/brand/arc-mark.svg'), url('/brand/arc-mark.svg');
    background-repeat: no-repeat;
    background-position: 13% 76%, 84% 22%;
    background-size: min(45vw, 480px), min(32vw, 360px);
    opacity: .22;
    pointer-events: none;
  }
  .auth-layout {
    border-color: rgba(24, 62, 83, .16);
    background: rgba(255, 255, 255, .32);
    box-shadow: 0 28px 70px rgba(27, 56, 75, .16);
  }
  .brand-stage {
    border-color: rgba(24, 62, 83, .12);
    background: rgba(225, 239, 245, .24);
  }
  .brand-lockup span,
  .panel-brand span { color: rgba(30, 60, 78, .62); }
  .brand-copy > span { color: rgba(30, 60, 78, .52); }
  .signal-lines i { background: rgba(22, 185, 196, .46); }
  .auth-panel {
    border-color: rgba(24, 62, 83, .14);
    background: rgba(255, 255, 255, .34);
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, .9), 0 18px 36px rgba(27, 56, 75, .1);
  }
  h1 { color: #172532; }
  .description { color: rgba(28, 57, 75, .72); }
  :deep(.el-form-item__label) { color: rgba(25, 52, 69, .84); }
  :deep(.el-input__wrapper) {
    background: rgba(255, 255, 255, .54);
    box-shadow: 0 0 0 1px rgba(24, 62, 83, .18) inset !important;
  }
  :deep(.el-input__inner) { color: #172532; }
  :deep(.el-input__inner::placeholder) { color: rgba(28, 57, 75, .42); }
  :deep(.invite-link) { color: #1478d4; }
  :deep(.invite-link:hover) { color: #a56f12; }
}
</style>
