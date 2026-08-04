<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  GLOSSARY_EVENT,
  allCategories,
  findTerm,
  searchTerms,
} from '../glossary/lookup.js'
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

const visible = ref(false)
const query = ref('')
const category = ref('')
const activeId = ref('')
const inputRef = ref(null)

const sharing = ref(false)
const shareOpen = ref(false)
const sharePreviewUrl = ref('')
const copying = ref(false)
const downloading = ref(false)
let sharePreviewObjectUrl = ''

const categories = allCategories()

const list = computed(() => {
  let rows = searchTerms(query.value, 80)
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

watch(list, (rows) => {
  if (!rows.length) {
    activeId.value = ''
    return
  }
  if (!rows.some((term) => term.id === activeId.value)) {
    activeId.value = rows[0].id
  }
})

async function openGlossary(termKey) {
  visible.value = true
  const hit = termKey ? findTerm(termKey) : null
  if (hit) {
    activeId.value = hit.id
    category.value = ''
    query.value = ''
  }
  await nextTick()
  inputRef.value?.focus?.()
}

function close() {
  visible.value = false
  query.value = ''
  closeShare()
}

function select(term) {
  if (!term) return
  activeId.value = term.id
}

function onEvent(e) {
  openGlossary(e?.detail?.termId)
}

function onKeydown(e) {
  if (!visible.value) return
  if (shareOpen.value) return
  if (e.key === 'Escape') {
    e.preventDefault()
    close()
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
    console.error(e)
    ElMessage.error(e.message || '截图失败')
  } finally {
    sharing.value = false
  }
}

async function onCopyShare() {
  copying.value = true
  try {
    const blob = await captureGlossaryShare()
    await copyImageBlob(blob)
    ElMessage.success('已复制到剪贴板，可直接粘贴到微信/文档')
  } catch (e) {
    console.error(e)
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
    console.error(e)
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
})

defineExpose({ openGlossary, close })
</script>

<template>
  <div v-if="visible" class="glossary-layer" @click.self="close">
    <div class="glossary-panel" role="dialog" aria-label="名词百科">
      <div class="glossary-head">
        <div class="glossary-title">
          <strong>名词百科</strong>
          <span>Apex · 指标 · 策略 · 行情释义</span>
        </div>
        <div class="glossary-actions">
          <button
            type="button"
            class="glossary-share"
            :disabled="!active || sharing"
            @click="openShare"
          >
            {{ sharing ? '生成中…' : '分享' }}
          </button>
          <button type="button" class="glossary-esc" @click="close">esc</button>
        </div>
      </div>

      <div class="glossary-search">
        <input
          ref="inputRef"
          v-model="query"
          class="glossary-input"
          placeholder="搜索：夏普、回撤、MACD、共振…"
          autocomplete="off"
          @keydown.esc.prevent="close"
        />
      </div>

      <div class="glossary-cats">
        <button
          type="button"
          class="cat"
          :class="{ on: !category }"
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
          @click="category = cat"
        >
          {{ cat }}
        </button>
      </div>

      <div class="glossary-body">
        <ul class="glossary-list">
          <li v-for="term in list" :key="term.id">
            <button
              type="button"
              class="glossary-item"
              :class="{ on: active?.id === term.id }"
              @click="select(term)"
            >
              <span class="name">{{ term.title }}</span>
              <span class="cat-tag">{{ term.category }}</span>
            </button>
          </li>
          <li v-if="!list.length" class="glossary-empty">无匹配词条</li>
        </ul>

        <article v-if="active" class="glossary-detail">
          <header>
            <div class="detail-title">
              <h2>{{ active.title }}</h2>
              <span>{{ active.category }}</span>
            </div>
            <button
              type="button"
              class="detail-share"
              :disabled="sharing"
              @click="openShare"
            >
              {{ sharing ? '生成中…' : '分享截图' }}
            </button>
          </header>
          <p class="lead">{{ active.short }}</p>
          <p class="detail">{{ active.detail }}</p>
          <p v-if="active.tip" class="tip">{{ active.tip }}</p>
          <p v-if="active.aliases?.length" class="aliases">
            也叫：{{ active.aliases.join(' · ') }}
          </p>
        </article>
        <div v-else class="glossary-detail glossary-detail--empty">选择左侧词条查看解释</div>
      </div>
    </div>
  </div>

  <el-dialog
    v-model="shareOpen"
    title="分享名词百科"
    width="740px"
    append-to-body
    destroy-on-close
    align-center
    class="glossary-share-dialog"
    @closed="revokeSharePreview"
  >
    <p class="share-tip">预览含 Apex 品牌信息；可复制或下载 PNG 后发微信/社群。</p>
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
  width: min(860px, 100%);
  max-height: min(78vh, 720px);
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid var(--glass-border);
  border-radius: 18px;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.16);
  overflow: hidden;
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

.glossary-share,
.glossary-esc,
.detail-share {
  border: 0;
  background: var(--paper-deep);
  color: var(--slate);
  font-size: 11px;
  padding: 4px 8px;
  border-radius: 8px;
  cursor: pointer;
}

.glossary-share,
.detail-share {
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 650;
}

.glossary-share:disabled,
.detail-share:disabled {
  opacity: 0.55;
  cursor: not-allowed;
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
  max-height: min(52vh, 480px);
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
  max-height: min(52vh, 480px);
}

.glossary-detail header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.detail-title {
  min-width: 0;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  flex: 1;
}

.glossary-detail h2 {
  margin: 0;
  font-size: 20px;
  letter-spacing: -0.03em;
}

.glossary-detail header span {
  font-size: 12px;
  color: var(--muted);
  flex: 0 0 auto;
}

.glossary-detail .lead {
  margin: 0 0 10px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--ink);
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

.glossary-detail--empty {
  color: var(--muted);
  display: grid;
  place-items: center;
}

.share-tip {
  margin: 0 0 12px;
  font-size: 13px;
  color: #86868b;
}

.share-stage {
  /* 勿用 flex + 默认 stretch，会把预览图纵向压扁 */
  display: block;
  width: 100%;
  max-height: min(62vh, 680px);
  overflow: auto;
  padding: 10px;
  background: #ececec;
  border-radius: 12px;
  text-align: center;
  box-sizing: border-box;
}

.share-stage img {
  width: min(100%, 680px);
  max-width: none;
  height: auto;
  display: inline-block;
  vertical-align: top;
  object-fit: contain;
  box-shadow: 0 10px 32px rgba(0, 0, 0, 0.12);
  border-radius: 4px;
}

@media (max-width: 720px) {
  .glossary-body {
    grid-template-columns: 1fr;
  }

  .glossary-list {
    max-height: 160px;
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }

  .glossary-detail {
    max-height: none;
  }

  .glossary-title span {
    display: none;
  }
}
</style>
