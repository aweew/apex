<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { findTerm, GLOSSARY_EVENT } from '../glossary/lookup.js'

const props = defineProps({
  /** 词条 id 或别名 */
  term: { type: String, default: '' },
  /** 未收录词条时显示的指标名称 */
  title: { type: String, default: '' },
  /** 未收录词条时显示的简要解释 */
  description: { type: String, default: '' },
  /** 未收录词条时显示的补充说明 */
  detail: { type: String, default: '' },
  /** 未收录词条的分类标签 */
  category: { type: String, default: '指标说明' },
  /** 气泡放置 */
  placement: { type: String, default: 'top' },
})

const entry = computed(() => findTerm(props.term))
const displayTitle = computed(() => entry.value?.title || props.title || props.term)
const displayCategory = computed(() => entry.value?.category || props.category)
const displayDescription = computed(() => entry.value?.plain || entry.value?.short || props.description)
const displayDetail = computed(() => entry.value?.tip || props.detail)
const hasContent = computed(() => Boolean(displayTitle.value && displayDescription.value))
const isMobile = ref(false)
let mobileMediaQuery

function syncMobile() {
  isMobile.value = mobileMediaQuery?.matches ?? false
}

function openFull() {
  if (!entry.value) return
  window.dispatchEvent(
    new CustomEvent(GLOSSARY_EVENT, { detail: { termId: entry.value.id } }),
  )
}

function openReference(event) {
  if (!entry.value) return
  event.stopPropagation()
  event.preventDefault()
  openFull()
}

onMounted(() => {
  mobileMediaQuery = window.matchMedia('(max-width: 820px), (hover: none)')
  syncMobile()
  mobileMediaQuery.addEventListener?.('change', syncMobile)
})

onBeforeUnmount(() => {
  mobileMediaQuery?.removeEventListener?.('change', syncMobile)
})
</script>

<template>
  <button
    v-if="entry && isMobile"
    type="button"
    class="term-tip"
    :aria-label="`查看${displayTitle}的解释`"
    @click.stop.prevent="openFull"
  >
    <slot>{{ displayTitle }}</slot>
  </button>
  <el-popover
    v-else-if="hasContent"
    :placement="placement"
    :width="320"
    :trigger="isMobile ? 'click' : 'hover'"
    :show-after="isMobile ? 0 : 180"
    popper-class="term-tip-popper"
  >
    <template #reference>
      <button
        type="button"
        class="term-tip"
        :aria-label="`查看${displayTitle}的解释`"
        @click="openReference"
      >
        <slot>{{ displayTitle }}</slot>
      </button>
    </template>
    <div class="term-tip__body">
      <div class="term-tip__head">
        <strong>{{ displayTitle }}</strong>
        <span v-if="displayCategory" class="term-tip__cat">{{ displayCategory }}</span>
      </div>
      <p class="term-tip__short">{{ displayDescription }}</p>
      <p v-if="displayDetail" class="term-tip__hint">{{ displayDetail }}</p>
      <button v-if="entry" type="button" class="term-tip__more" @click="openFull">打开名词百科</button>
    </div>
  </el-popover>
  <span v-else class="term-tip term-tip--miss" :title="`未收录：${displayTitle}`">
    <slot>{{ displayTitle }}</slot>
  </span>
</template>

<style scoped>
.term-tip {
  display: inline;
  margin: 0;
  padding: 0;
  border: 0;
  background: none;
  color: inherit;
  font: inherit;
  cursor: help;
  border-bottom: 1px dashed rgba(0, 113, 227, 0.45);
  border-radius: 0;
  line-height: inherit;
  white-space: nowrap;
  word-break: keep-all;
}

.term-tip:hover {
  color: var(--accent);
  border-bottom-color: var(--accent);
}

.term-tip:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--accent) 58%, transparent);
  outline-offset: 2px;
  color: var(--accent);
  border-bottom-color: var(--accent);
}

.term-tip--miss {
  border-bottom: none;
  cursor: inherit;
}

@media (max-width: 820px), (hover: none) {
  .term-tip {
    display: inline-block;
    line-height: 1.2;
    vertical-align: baseline;
  }
}

.term-tip__body {
  display: grid;
  gap: 8px;
}

.term-tip__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.term-tip__head strong {
  font-size: 14px;
  letter-spacing: -0.02em;
}

.term-tip__cat {
  font-size: 11px;
  color: var(--muted);
  background: var(--paper-deep);
  padding: 1px 6px;
  border-radius: 999px;
}

.term-tip__short {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--ink-soft);
}

.term-tip__hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--slate);
  padding: 6px 8px;
  background: rgba(0, 113, 227, 0.06);
  border-radius: 8px;
}

.term-tip__more {
  justify-self: start;
  margin: 0;
  padding: 0;
  border: 0;
  background: none;
  color: var(--accent);
  font-size: 12px;
  cursor: pointer;
}

.term-tip__more:hover {
  text-decoration: underline;
}
</style>
