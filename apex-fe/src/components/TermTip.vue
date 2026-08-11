<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { findTerm, GLOSSARY_EVENT } from '../glossary/lookup.js'

const props = defineProps({
  /** 词条 id 或别名 */
  term: { type: String, required: true },
  /** 气泡放置 */
  placement: { type: String, default: 'top' },
})

const entry = computed(() => findTerm(props.term))
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
    @click.stop.prevent="openFull"
  >
    <slot>{{ entry.title }}</slot>
  </button>
  <el-popover
    v-else-if="entry"
    :placement="placement"
    :width="320"
    trigger="hover"
    :show-after="180"
    popper-class="term-tip-popper"
  >
    <template #reference>
      <button type="button" class="term-tip" @click.stop.prevent="openFull">
        <slot>{{ entry.title }}</slot>
      </button>
    </template>
    <div class="term-tip__body">
      <div class="term-tip__head">
        <strong>{{ entry.title }}</strong>
        <span class="term-tip__cat">{{ entry.category }}</span>
      </div>
      <p class="term-tip__short">{{ entry.plain || entry.short }}</p>
      <p v-if="entry.tip" class="term-tip__hint">{{ entry.tip }}</p>
      <button type="button" class="term-tip__more" @click="openFull">打开名词百科</button>
    </div>
  </el-popover>
  <span v-else class="term-tip term-tip--miss" :title="`未收录：${term}`">
    <slot>{{ term }}</slot>
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

.term-tip--miss {
  border-bottom: none;
  cursor: inherit;
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
