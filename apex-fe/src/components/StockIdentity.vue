<script setup>
import { computed } from 'vue'
import SecurityMarketBadge from './SecurityMarketBadge.vue'

const props = defineProps({
  security: {
    type: Object,
    default: () => ({}),
  },
  code: {
    type: [String, Number],
    default: '',
  },
  name: {
    type: String,
    default: '',
  },
  interactive: {
    type: Boolean,
    default: false,
  },
  includeMain: {
    type: Boolean,
    default: false,
  },
  compact: {
    type: Boolean,
    default: false,
  },
  prominent: {
    type: Boolean,
    default: false,
  },
  showCode: {
    type: Boolean,
    default: true,
  },
})

const emit = defineEmits(['select'])

const displayCode = computed(() => String(
  props.code || props.security?.code || props.security?.stockCode || '',
).trim())
const displayName = computed(() => String(
  props.name || props.security?.name || props.security?.stockName || displayCode.value || '-',
).trim())
const hasSecondaryCode = computed(() => (
  props.showCode
  && displayCode.value
  && displayName.value !== displayCode.value
))
const normalizedSecurity = computed(() => ({
  ...props.security,
  code: displayCode.value,
  name: displayName.value,
}))
const accessibleLabel = computed(() => {
  if (!props.interactive) return undefined
  const identity = hasSecondaryCode.value
    ? `${displayName.value} ${displayCode.value}`
    : displayName.value
  return `打开${identity}股票详情`
})

function selectSecurity() {
  if (!props.interactive) return
  emit('select', {
    code: displayCode.value,
    name: displayName.value,
    security: normalizedSecurity.value,
  })
}
</script>

<template>
  <component
    :is="interactive ? 'button' : 'span'"
    class="stock-identity"
    :class="{ 'is-interactive': interactive, 'is-compact': compact, 'is-prominent': prominent }"
    :type="interactive ? 'button' : undefined"
    :aria-label="accessibleLabel"
    @click="selectSecurity"
  >
    <span class="stock-identity__name-line">
      <span class="stock-identity__name" :title="displayName">
        <slot name="name" :name="displayName">{{ displayName }}</slot>
      </span>
      <SecurityMarketBadge :security="normalizedSecurity" :include-main="includeMain" />
    </span>
    <span v-if="hasSecondaryCode" class="stock-identity__code" :title="displayCode">
      <slot name="code" :code="displayCode">{{ displayCode }}</slot>
    </span>
  </component>
</template>

<style scoped>
.stock-identity {
  display: inline-flex;
  min-width: 0;
  max-width: 100%;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 2px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: inherit;
  font: inherit;
  letter-spacing: 0;
  line-height: 1.2;
  text-align: left;
  vertical-align: middle;
}

.stock-identity__name-line {
  display: flex;
  min-width: 0;
  max-width: 100%;
  align-items: center;
  gap: 5px;
}

.stock-identity__name {
  min-width: 0;
  overflow: hidden;
  color: var(--ink);
  font-size: 14px;
  font-weight: 650;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stock-identity__code {
  max-width: 100%;
  overflow: hidden;
  color: var(--accent);
  font-size: 12px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stock-identity.is-compact .stock-identity__name {
  font-size: 13px;
}

.stock-identity.is-compact .stock-identity__code {
  font-size: 11px;
}

.stock-identity.is-prominent .stock-identity__name {
  font-size: 22px;
  font-weight: 700;
}

.stock-identity.is-prominent .stock-identity__code {
  font-size: 13px;
}

.stock-identity.is-interactive {
  min-height: 36px;
  margin: -4px -6px;
  padding: 4px 6px;
  cursor: pointer;
  transition: background-color 0.16s ease, color 0.16s ease;
}

.stock-identity.is-interactive:hover {
  background: var(--accent-soft);
}

.stock-identity.is-interactive:hover .stock-identity__name,
.stock-identity.is-interactive:focus-visible .stock-identity__name {
  color: var(--accent-hover);
}

.stock-identity.is-interactive:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

@media (max-width: 900px) {
  .stock-identity.is-interactive {
    min-height: 44px;
  }
}
</style>
