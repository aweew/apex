<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  GLOSSARY_EVENT,
  allCategories,
  findTerm,
  searchTerms,
} from '../glossary/lookup.js'

const visible = ref(false)
const query = ref('')
const category = ref('')
const activeId = ref('')
const inputRef = ref(null)

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
  if (e.key === 'Escape') {
    e.preventDefault()
    close()
  }
}

onMounted(() => {
  window.addEventListener(GLOSSARY_EVENT, onEvent)
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener(GLOSSARY_EVENT, onEvent)
  window.removeEventListener('keydown', onKeydown)
})

defineExpose({ openGlossary, close })
</script>

<template>
  <div v-if="visible" class="glossary-layer" @click.self="close">
    <div class="glossary-panel" role="dialog" aria-label="名词百科">
      <div class="glossary-head">
        <div class="glossary-title">
          <strong>名词百科</strong>
          <span>指标 · 策略 · 行情释义</span>
        </div>
        <button type="button" class="glossary-esc" @click="close">esc</button>
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
            <h2>{{ active.title }}</h2>
            <span>{{ active.category }}</span>
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
  padding: 14px 16px 8px;
}

.glossary-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.glossary-title strong {
  font-family: var(--font-display);
  font-size: 16px;
  letter-spacing: -0.02em;
}

.glossary-title span {
  font-size: 12px;
  color: var(--muted);
}

.glossary-esc {
  border: 0;
  background: var(--paper-deep);
  color: var(--slate);
  font-size: 11px;
  padding: 4px 8px;
  border-radius: 8px;
  cursor: pointer;
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
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.glossary-detail h2 {
  margin: 0;
  font-size: 20px;
  letter-spacing: -0.03em;
}

.glossary-detail header span {
  font-size: 12px;
  color: var(--muted);
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
}
</style>
