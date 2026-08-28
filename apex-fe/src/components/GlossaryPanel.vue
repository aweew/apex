<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import FloatingShareButton from './FloatingShareButton.vue'
import {
  GLOSSARY_EVENT,
  allTerms,
  allCategories,
  findTerm,
  getRelatedTerms,
  searchTerms,
  splitHighlightedText,
} from '../glossary/lookup.js'
import { getDiagramSvg } from '../glossary/diagrams.js'
import {
  buildGlossaryShareSheet,
  mountGlossaryShareSheet,
} from '../utils/glossaryShareSheet.js'
import {
  captureElementBlob,
  copyImageBlob,
  downloadBlob,
  shareFilename,
} from '../utils/shareCapture.js'
import { useBottomSheetSwipe } from '../utils/bottomSheetSwipe.js'

const visible = ref(false)
const query = ref('')
const category = ref('')
const activeId = ref('')
const inputRef = ref(null)
const detailRef = ref(null)
const mobileDetailOpen = ref(false)

const sharing = ref(false)
const shareOpen = ref(false)
const sharePreviewUrl = ref('')
const copying = ref(false)
const downloading = ref(false)
let sharePreviewObjectUrl = ''
let returnFocus = null

const sheetSwipe = useBottomSheetSwipe({
  enabled: () => visible.value && isMobileViewport() && !shareOpen.value,
  onDismiss: close,
})
const sheetSwipeStyle = sheetSwipe.panelStyle
const sheetBackdropStyle = sheetSwipe.backdropStyle
const sheetSwipeDragging = sheetSwipe.dragging

const categories = allCategories()
const totalTerms = allTerms().length

const list = computed(() => {
  let rows = searchTerms(query.value, totalTerms)
  if (category.value) {
    rows = rows.filter((term) => term.category === category.value)
  }
  return rows
})

const active = computed(() => {
  if (activeId.value) {
    const hit = findTerm(activeId.value)
    if (hit) return hit
  }
  return list.value[0] || null
})

const activeDiagram = computed(() => getDiagramSvg(active.value?.diagram))
const activePlainSegments = computed(() => (
  splitHighlightedText(active.value?.plain, active.value?.highlights)
))
const activeDetailSegments = computed(() => (
  splitHighlightedText(active.value?.detail, active.value?.highlights)
))
const activeRelated = computed(() => getRelatedTerms(active.value))

watch(list, (rows) => {
  if (!rows.length) {
    activeId.value = ''
    return
  }
  if (!rows.some((term) => term.id === activeId.value)) {
    activeId.value = rows[0].id
  }
})

watch([query, category], () => {
  if (isMobileViewport()) {
    mobileDetailOpen.value = false
  }
})

watch(
  visible,
  (isVisible) => {
    document.documentElement.classList.toggle('glossary-open', isVisible)
  },
  { immediate: true },
)

function isMobileViewport() {
  return window.matchMedia('(max-width: 820px)').matches
}

async function openGlossary(termKey) {
  if (!visible.value) returnFocus = document.activeElement
  visible.value = true
  const hit = termKey ? findTerm(termKey) : null
  if (hit) {
    activeId.value = hit.id
    category.value = ''
    query.value = ''
  }
  await nextTick()
  mobileDetailOpen.value = Boolean(hit && isMobileViewport())
  if (!isMobileViewport()) {
    inputRef.value?.focus?.()
  }
}

function close() {
  const focusTarget = returnFocus
  returnFocus = null
  visible.value = false
  query.value = ''
  mobileDetailOpen.value = false
  closeShare()
  nextTick(() => focusTarget?.focus?.())
}

function select(term) {
  if (!term) return
  activeId.value = term.id
  if (isMobileViewport()) {
    mobileDetailOpen.value = true
  }
}

async function selectRelated(term) {
  query.value = ''
  category.value = ''
  select(term)
  await nextTick()
  detailRef.value?.scrollTo?.({ top: 0, behavior: 'smooth' })
}

function backToList() {
  mobileDetailOpen.value = false
}

function handleEscape() {
  if (mobileDetailOpen.value && isMobileViewport()) {
    backToList()
    return
  }
  close()
}

function onEvent(e) {
  openGlossary(e?.detail?.termId)
}

function onKeydown(e) {
  if (!visible.value) return
  if (shareOpen.value) return
  if (e.key === 'Escape') {
    e.preventDefault()
    handleEscape()
  }
}

function revokeSharePreview() {
  if (sharePreviewObjectUrl) {
    URL.revokeObjectURL(sharePreviewObjectUrl)
    sharePreviewObjectUrl = ''
  }
  sharePreviewUrl.value = ''
}

async function captureGlossaryShare() {
  const term = active.value
  if (!term) throw new Error('请先选择词条')
  const titleDate = new Date().toISOString().slice(0, 10)
  const sheet = buildGlossaryShareSheet({ term, titleDate })
  const mounted = mountGlossaryShareSheet(sheet)
  try {
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))
    const width = 680
    const height = Math.max(sheet.scrollHeight, sheet.offsetHeight, 1)
    sheet.style.width = `${width}px`
    sheet.style.height = `${height}px`
    const dpr = Math.max(window.devicePixelRatio || 1, 2)
    return await captureElementBlob(sheet, {
      scale: Math.min(dpr, 2.5),
      width,
      height,
      backgroundColor: '#f7f4ee',
      style: {
        width: `${width}px`,
        height: `${height}px`,
        overflow: 'visible',
        transform: 'none',
        margin: '0',
        opacity: '1',
      },
    })
  } finally {
    mounted.dispose()
  }
}

async function openShare() {
  if (!active.value) {
    ElMessage.warning('请先选择词条')
    return
  }
  sharing.value = true
  try {
    const blob = await captureGlossaryShare()
    revokeSharePreview()
    sharePreviewObjectUrl = URL.createObjectURL(blob)
    sharePreviewUrl.value = sharePreviewObjectUrl
    shareOpen.value = true
  } catch (e) {
    console.error('生成术语分享图失败', e)
    ElMessage.error(e.message || '截图失败')
  } finally {
    sharing.value = false
  }
}

async function onCopyShare() {
  copying.value = true
  try {
    await copyImageBlob(captureGlossaryShare())
    ElMessage.success('已复制到剪贴板，可直接粘贴到微信/文档')
  } catch (e) {
    console.error('复制术语分享图失败', e)
    ElMessage.error(e.message || '复制失败，请改用下载')
  } finally {
    copying.value = false
  }
}

async function onDownloadShare() {
  downloading.value = true
  try {
    const blob = await captureGlossaryShare()
    downloadBlob(blob, shareFilename('apex_glossary', active.value?.title))
    ElMessage.success('已下载分享图')
  } catch (e) {
    console.error('下载术语分享图失败', e)
    ElMessage.error(e.message || '下载失败')
  } finally {
    downloading.value = false
  }
}

function closeShare() {
  shareOpen.value = false
  revokeSharePreview()
  copying.value = false
  downloading.value = false
}

onMounted(() => {
  window.addEventListener(GLOSSARY_EVENT, onEvent)
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener(GLOSSARY_EVENT, onEvent)
  window.removeEventListener('keydown', onKeydown)
  revokeSharePreview()
  document.documentElement.classList.remove('glossary-open')
})

defineExpose({ openGlossary, close })
</script>

<template>
  <div v-if="visible" class="glossary-layer" :style="sheetBackdropStyle" @click.self="close">
    <div
      class="glossary-panel"
      :class="{ 'is-mobile-detail': mobileDetailOpen, 'is-sheet-dragging': sheetSwipeDragging }"
      :style="sheetSwipeStyle"
      data-bottom-sheet
      role="dialog"
      aria-modal="true"
      aria-label="名词百科"
    >
      <div
        class="glossary-drag-zone"
        @touchstart="sheetSwipe.onTouchStart"
        @touchmove="sheetSwipe.onTouchMove"
        @touchend="sheetSwipe.onTouchEnd"
        @touchcancel="sheetSwipe.onTouchCancel"
      >
        <div class="glossary-handle" aria-hidden="true" />
        <div class="glossary-head">
          <button
            v-if="mobileDetailOpen"
            type="button"
            class="glossary-head-back"
            aria-label="返回词条列表"
            @click="backToList"
          >
            <el-icon><ArrowLeft /></el-icon>
          </button>
          <div class="glossary-title">
            <strong>名词百科</strong>
            <span>灵极 · 金融 · 宏观 · 投资释义</span>
          </div>
          <div class="glossary-actions">
            <button type="button" class="glossary-close" aria-label="关闭名词百科" @click="close">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M6 6l12 12M18 6 6 18" />
              </svg>
            </button>
          </div>
        </div>
      </div>

      <div class="glossary-search">
        <input
          ref="inputRef"
          v-model="query"
          class="glossary-input"
          placeholder="搜索：股票、GDP、DCF、夏普、MACD…"
          autocomplete="off"
          aria-controls="glossary-results"
          @keydown.esc.prevent="handleEscape"
        />
      </div>

      <div class="glossary-cats">
        <button
          type="button"
          class="cat"
          :class="{ on: !category }"
          :aria-pressed="!category"
          @click="category = ''"
        >
          全部
        </button>
        <button
          v-for="cat in categories"
          :key="cat"
          type="button"
          class="cat"
          :class="{ on: category === cat }"
          :aria-pressed="category === cat"
          @click="category = cat"
        >
          {{ cat }}
        </button>
      </div>

      <div class="glossary-result-count" role="status" aria-live="polite" aria-atomic="true">共 {{ list.length }} 个词条</div>

      <div class="glossary-body">
        <ul id="glossary-results" class="glossary-list">
          <li v-for="term in list" :key="term.id">
            <button
              type="button"
              class="glossary-item"
              :class="{ on: active?.id === term.id }"
              :aria-pressed="active?.id === term.id"
              @click="select(term)"
            >
              <span class="item-copy">
                <span class="name">{{ term.title }}</span>
                <span class="cat-tag">{{ term.category }}</span>
              </span>
              <svg class="item-chevron" viewBox="0 0 24 24" aria-hidden="true">
                <path d="m9 18 6-6-6-6" />
              </svg>
            </button>
          </li>
          <li v-if="!list.length" class="glossary-empty">无匹配词条</li>
        </ul>

        <article v-if="active" ref="detailRef" class="glossary-detail">
          <header>
            <h2>{{ active.title }}</h2>
            <div class="detail-actions">
              <span>{{ active.category }}</span>
            </div>
          </header>
          <p class="lead">{{ active.short }}</p>
          <div v-if="active.plain" class="plain-language">
            <span class="plain-label">通俗理解</span>
            <p>
              <template v-for="(segment, index) in activePlainSegments" :key="index">
                <mark v-if="segment.highlighted" class="key-point">{{ segment.text }}</mark>
                <span v-else>{{ segment.text }}</span>
              </template>
            </p>
          </div>
          <div
            v-if="activeDiagram"
            class="diagram"
            v-html="activeDiagram"
          />
          <p class="detail">
            <template v-for="(segment, index) in activeDetailSegments" :key="index">
              <mark v-if="segment.highlighted" class="key-point">{{ segment.text }}</mark>
              <span v-else>{{ segment.text }}</span>
            </template>
          </p>
          <p v-if="active.tip" class="tip">{{ active.tip }}</p>
          <section v-if="activeRelated.length" class="related-terms" aria-label="相关词条">
            <span>继续了解</span>
            <div>
              <button
                v-for="term in activeRelated"
                :key="term.id"
                type="button"
                @click="selectRelated(term)"
              >
                {{ term.title }}
              </button>
            </div>
          </section>
          <p v-if="active.aliases?.length" class="aliases">
            也叫：{{ active.aliases.join(' · ') }}
          </p>
        </article>
        <div v-else class="glossary-detail glossary-detail--empty">选择左侧词条查看解释</div>
      </div>
      <FloatingShareButton
        v-if="active && !shareOpen"
        class="glossary-floating-share"
        :loading="sharing"
        label="分享当前词条"
        @click="openShare"
      />
    </div>
  </div>

  <el-dialog
    v-model="shareOpen"
    title="分享名词百科"
    width="740px"
    append-to-body
    destroy-on-close
    align-center
    :z-index="2100"
    modal-class="glossary-share-overlay"
    class="glossary-share-dialog"
    @closed="revokeSharePreview"
  >
    <p class="share-tip">预览含灵极 Apex 品牌与口号；可复制或下载 PNG 后发微信/社群。</p>
    <div class="share-stage">
      <img v-if="sharePreviewUrl" :src="sharePreviewUrl" alt="名词百科分享预览" />
    </div>
    <template #footer>
      <el-button @click="closeShare">关闭</el-button>
      <el-button type="primary" plain :loading="copying" @click="onCopyShare">复制图片</el-button>
      <el-button type="primary" :loading="downloading" @click="onDownloadShare">下载 PNG</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.glossary-layer {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: grid;
  place-items: start center;
  padding: 72px 16px 24px;
  background: rgba(29, 29, 31, 0.28);
  backdrop-filter: blur(8px);
}

.glossary-panel {
  position: relative;
  width: min(860px, 100%);
  max-height: min(84vh, 780px);
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid var(--glass-border);
  border-radius: 18px;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.16);
  overflow: hidden;
}

.glossary-handle,
.glossary-head-back {
  display: none;
}

.glossary-drag-zone {
  flex: 0 0 auto;
}

.glossary-floating-share {
  position: absolute;
  right: 18px;
  bottom: 18px;
  z-index: 3;
}

.glossary-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px 8px;
}

.glossary-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.glossary-title strong {
  font-family: var(--font-display);
  font-size: 16px;
  letter-spacing: -0.02em;
}

.glossary-title span {
  font-size: 12px;
  color: var(--muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.glossary-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.glossary-close,
.glossary-head-back {
  border: 0;
  background: var(--paper-deep);
  color: var(--slate);
  font-size: 11px;
  padding: 4px 8px;
  border-radius: 8px;
  cursor: pointer;
}

.glossary-close {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  padding: 0;
}

.glossary-close svg,
.item-chevron {
  width: 18px;
  height: 18px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.glossary-search {
  padding: 0 16px 10px;
}

.glossary-input {
  width: 100%;
  height: 40px;
  border: 1px solid var(--line-strong);
  border-radius: 12px;
  padding: 0 12px;
  font: inherit;
  background: #fff;
  outline: none;
}

.glossary-input:focus {
  border-color: rgba(0, 113, 227, 0.55);
  box-shadow: 0 0 0 3px rgba(0, 113, 227, 0.12);
}

.glossary-cats {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 0 16px 12px;
}

.cat {
  border: 0;
  background: var(--paper-deep);
  color: var(--slate);
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  cursor: pointer;
}

.cat.on {
  background: var(--accent-soft);
  color: var(--accent);
}

.glossary-result-count {
  display: none;
}

.glossary-body {
  display: grid;
  grid-template-columns: minmax(200px, 240px) 1fr;
  gap: 0;
  min-height: 0;
  flex: 1;
  border-top: 1px solid var(--line);
}

.glossary-list {
  list-style: none;
  margin: 0;
  padding: 8px;
  overflow: auto;
  border-right: 1px solid var(--line);
  max-height: min(56vh, 520px);
}

.glossary-item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border: 0;
  background: transparent;
  text-align: left;
  padding: 9px 10px;
  border-radius: 10px;
  cursor: pointer;
  font: inherit;
  color: var(--ink);
}

.glossary-item:hover,
.glossary-item.on {
  background: rgba(0, 113, 227, 0.08);
}

.glossary-item .name {
  font-size: 13px;
}

.item-copy {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex: 1;
}

.item-chevron {
  display: none;
}

.glossary-item .cat-tag {
  font-size: 11px;
  color: var(--muted);
}

.glossary-empty {
  padding: 16px 10px;
  color: var(--muted);
  font-size: 13px;
}

.glossary-detail {
  padding: 18px 20px 22px;
  overflow: auto;
  max-height: min(56vh, 520px);
}

.glossary-detail header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.detail-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.glossary-detail h2 {
  margin: 0;
  min-width: 0;
  font-size: 20px;
  letter-spacing: -0.03em;
  line-height: 1.25;
}

.glossary-detail header span {
  font-size: 12px;
  color: var(--muted);
  line-height: 1;
  white-space: nowrap;
}

.glossary-detail .lead {
  margin: 0 0 10px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--ink);
}

.plain-language {
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: start;
  gap: 10px;
  margin: 0 0 14px;
  padding: 10px 12px;
  border-left: 3px solid var(--accent);
  background: rgba(0, 113, 227, 0.055);
}

.plain-label {
  padding-top: 2px;
  color: var(--accent);
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

.plain-language p {
  margin: 0;
  color: var(--ink);
  font-size: 13px;
  line-height: 1.65;
}

.key-point {
  padding: 0;
  background: transparent;
  color: #0066cc;
  font-weight: 700;
}

.glossary-detail .diagram {
  margin: 0 0 14px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--line);
  background: #f5f7fa;
}

.glossary-detail .diagram :deep(svg) {
  display: block;
  width: 100%;
  height: auto;
  vertical-align: top;
}

.glossary-detail .detail {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.65;
  color: var(--ink-soft);
}

.glossary-detail .tip {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--slate);
  padding: 8px 10px;
  background: rgba(0, 113, 227, 0.06);
  border-radius: 10px;
}

.glossary-detail .aliases {
  margin: 0;
  font-size: 12px;
  color: var(--muted);
}

.related-terms {
  display: grid;
  gap: 7px;
  margin: 0 0 12px;
}

.related-terms > span {
  color: var(--muted);
  font-size: 11px;
  font-weight: 650;
}

.related-terms > div {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.related-terms button {
  min-height: 30px;
  padding: 4px 9px;
  border: 1px solid rgba(0, 113, 227, 0.18);
  border-radius: 6px;
  background: #fff;
  color: #0066cc;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}

.related-terms button:hover,
.related-terms button:focus-visible {
  border-color: rgba(0, 113, 227, 0.42);
  background: rgba(0, 113, 227, 0.06);
  outline: none;
}

.glossary-detail--empty {
  color: var(--muted);
  display: grid;
  place-items: center;
}

@media (max-width: 820px) {
  .glossary-layer {
    display: flex;
    align-items: flex-end;
    padding: 0;
    background: rgba(15, 23, 42, 0.38);
    backdrop-filter: blur(3px);
    overscroll-behavior: none;
  }

  .glossary-panel {
    width: 100%;
    height: min(88dvh, 760px);
    max-height: calc(100dvh - max(18px, env(safe-area-inset-top)));
    border: 0;
    border-radius: 18px 18px 0 0;
    box-shadow: 0 -12px 36px rgba(15, 23, 42, 0.2);
    background: #fff;
    animation: glossary-sheet-in 220ms ease-out;
  }

  .glossary-drag-zone {
    touch-action: none;
    user-select: none;
  }

  .glossary-panel.is-sheet-dragging {
    box-shadow: 0 -8px 24px rgba(15, 23, 42, 0.16);
  }

  .glossary-handle {
    display: block;
    width: 36px;
    height: 4px;
    flex: 0 0 auto;
    margin: 8px auto 1px;
    border-radius: 999px;
    background: #c7cbd1;
  }

  .glossary-head {
    min-height: 52px;
    padding: 3px 10px 5px;
    border-bottom: 1px solid var(--line);
  }

  .glossary-head-back {
    display: grid;
    place-items: center;
    width: 44px;
    height: 44px;
    flex: 0 0 44px;
    padding: 0;
    border: 0;
    border-radius: 10px;
    background: transparent;
    color: var(--accent);
    font-size: 21px;
  }

  .glossary-title {
    flex: 1;
    padding-left: 6px;
  }

  .is-mobile-detail .glossary-title {
    padding-left: 0;
  }

  .glossary-title strong {
    font-size: 18px;
    letter-spacing: 0;
  }

  .glossary-actions {
    gap: 4px;
  }

  .glossary-close {
    width: 44px;
    height: 44px;
    padding: 0;
    background: transparent;
  }

  .glossary-search {
    padding: 10px 16px 8px;
  }

  .glossary-input {
    height: 44px;
    border-radius: 10px;
    font-size: 16px;
  }

  .glossary-cats {
    flex-wrap: nowrap;
    gap: 8px;
    padding: 0 16px 8px;
    overflow-x: auto;
    overscroll-behavior-x: contain;
    scrollbar-width: none;
  }

  .glossary-cats::-webkit-scrollbar {
    display: none;
  }

  .cat {
    min-height: 44px;
    flex: 0 0 auto;
    padding: 0 14px;
    font-size: 13px;
    border: 1px solid transparent;
  }

  .cat.on {
    border-color: rgba(0, 113, 227, 0.18);
  }

  .glossary-result-count {
    display: block;
    padding: 3px 16px 8px;
    color: var(--muted);
    font-size: 12px;
  }

  .glossary-body {
    display: flex;
    min-height: 0;
    overflow: hidden;
  }

  .glossary-list {
    width: 100%;
    max-height: none;
    padding: 6px 10px max(16px, env(safe-area-inset-bottom));
    border-right: 0;
    border-bottom: 0;
    overflow-y: auto;
    overscroll-behavior-y: contain;
    touch-action: pan-y;
    -webkit-overflow-scrolling: touch;
  }

  .glossary-item {
    min-height: 54px;
    padding: 8px 10px 8px 12px;
    border-radius: 8px;
  }

  .glossary-item:hover {
    background: transparent;
  }

  .glossary-item:active,
  .glossary-item.on {
    background: rgba(0, 113, 227, 0.09);
  }

  .item-copy {
    display: block;
  }

  .glossary-item .name,
  .glossary-item .cat-tag {
    display: block;
  }

  .glossary-item .name {
    font-size: 15px;
    line-height: 1.35;
  }

  .glossary-item .cat-tag {
    margin-top: 3px;
    font-size: 11px;
  }

  .item-chevron {
    display: block;
    flex: 0 0 auto;
    color: var(--muted);
  }

  .glossary-detail {
    display: none;
    width: 100%;
    max-height: none;
    padding: 18px 16px calc(78px + env(safe-area-inset-bottom));
    overflow-y: auto;
    overscroll-behavior-y: contain;
    touch-action: pan-y;
    -webkit-overflow-scrolling: touch;
  }

  .is-mobile-detail .glossary-search,
  .is-mobile-detail .glossary-cats,
  .is-mobile-detail .glossary-result-count,
  .is-mobile-detail .glossary-list {
    display: none;
  }

  .is-mobile-detail .glossary-detail {
    display: block;
  }

  .glossary-detail header {
    align-items: flex-start;
    margin-bottom: 14px;
  }

  .glossary-detail h2 {
    font-size: 22px;
    letter-spacing: 0;
  }

  .detail-actions {
    align-items: flex-end;
  }

  .glossary-detail .lead {
    font-size: 15px;
    line-height: 1.7;
  }

  .glossary-detail .detail {
    font-size: 14px;
    line-height: 1.75;
  }

  .plain-language {
    grid-template-columns: 1fr;
    gap: 4px;
    padding: 11px 12px;
  }

  .plain-language p {
    font-size: 14px;
    line-height: 1.7;
  }

  .related-terms button {
    min-height: 40px;
    padding: 7px 10px;
    font-size: 13px;
  }

  .glossary-title span {
    display: none;
  }

  .glossary-floating-share {
    right: 16px;
    bottom: calc(16px + env(safe-area-inset-bottom));
    z-index: 3;
  }
}

@keyframes glossary-sheet-in {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

@media (prefers-reduced-motion: reduce) {
  .glossary-panel { animation: none; }
}
</style>

<!-- append-to-body 弹窗需全局样式；勿给 .el-overlay 写 display:!important，会盖住关闭后的 display:none 导致蒙层残留 -->
<style>
html.glossary-open,
html.glossary-open body {
  overflow: hidden;
  overscroll-behavior: none;
}

.glossary-share-overlay .el-overlay-dialog {
  align-items: center;
  justify-content: center;
  overflow: auto;
  padding: 24px 16px;
  box-sizing: border-box;
}

.glossary-share-dialog.el-dialog {
  margin: 0 auto !important;
  max-width: min(740px, calc(100vw - 32px));
  max-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.glossary-share-dialog .el-dialog__body {
  padding-top: 8px;
  min-height: 0;
  overflow: hidden;
  flex: 1 1 auto;
}

.glossary-share-dialog .share-tip {
  margin: 0 0 12px;
  font-size: 13px;
  color: #86868b;
}

.glossary-share-dialog .share-stage {
  display: block;
  width: 100%;
  max-height: min(58vh, 640px);
  overflow: auto;
  -webkit-overflow-scrolling: touch;
  padding: 10px;
  background: #ececec;
  border-radius: 12px;
  text-align: center;
  box-sizing: border-box;
}

.glossary-share-dialog .share-stage img {
  width: min(100%, 680px);
  max-width: none;
  height: auto;
  display: inline-block;
  vertical-align: top;
  object-fit: contain;
  box-shadow: 0 10px 32px rgba(0, 0, 0, 0.12);
  border-radius: 4px;
}

@media (max-width: 820px) {
  .glossary-share-overlay .el-overlay-dialog {
    align-items: flex-end;
    padding: 0;
  }

  .glossary-share-dialog.el-dialog {
    width: 100% !important;
    max-width: none;
    max-height: 90dvh;
    margin: 0 !important;
    border-radius: 14px 14px 0 0;
  }

  .glossary-share-dialog .el-dialog__header,
  .glossary-share-dialog .el-dialog__body,
  .glossary-share-dialog .el-dialog__footer {
    padding-left: 16px;
    padding-right: 16px;
  }

  .glossary-share-dialog .el-dialog__footer {
    padding-bottom: max(16px, env(safe-area-inset-bottom));
  }

  .glossary-share-dialog .el-dialog__footer .el-button {
    min-height: 44px;
  }
}
</style>
