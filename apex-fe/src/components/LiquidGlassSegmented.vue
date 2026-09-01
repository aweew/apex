<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: {
    type: [String, Number],
    required: true,
  },
  options: {
    type: Array,
    required: true,
  },
  ariaLabel: {
    type: String,
    default: '切换视图',
  },
  busy: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['update:modelValue', 'change'])

const activeIndex = computed(() => {
  const index = props.options.findIndex((option) => option.value === props.modelValue)
  return Math.max(index, 0)
})

const liquidStyle = computed(() => ({
  '--liquid-index': activeIndex.value,
  '--liquid-count': Math.max(props.options.length, 1),
}))

function selectOption(option) {
  if (option.disabled || option.value === props.modelValue) return
  emit('update:modelValue', option.value)
  emit('change', option.value)
}

function onKeydown(event, index) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const enabledOptions = props.options
    .map((option, optionIndex) => ({ option, optionIndex }))
    .filter(({ option }) => !option.disabled)
  if (!enabledOptions.length) return

  const currentPosition = enabledOptions.findIndex(({ optionIndex }) => optionIndex === index)
  let nextPosition = currentPosition
  if (event.key === 'Home') nextPosition = 0
  if (event.key === 'End') nextPosition = enabledOptions.length - 1
  if (event.key === 'ArrowLeft') nextPosition = (currentPosition - 1 + enabledOptions.length) % enabledOptions.length
  if (event.key === 'ArrowRight') nextPosition = (currentPosition + 1) % enabledOptions.length

  const next = enabledOptions[nextPosition]
  selectOption(next.option)
  event.currentTarget.parentElement?.querySelectorAll('[role="tab"]')?.[next.optionIndex]?.focus()
}
</script>

<template>
  <nav
    class="liquid-glass-segmented"
    :class="{ 'is-busy': busy }"
    :style="liquidStyle"
    role="tablist"
    :aria-label="ariaLabel"
    :aria-busy="busy"
  >
    <span class="liquid-glass-indicator" aria-hidden="true" />
    <button
      v-for="(option, index) in options"
      :key="option.value"
      type="button"
      role="tab"
      class="liquid-glass-option"
      :class="{ 'is-active': option.value === modelValue }"
      :aria-selected="option.value === modelValue"
      :tabindex="option.value === modelValue ? 0 : -1"
      :disabled="option.disabled"
      @click="selectOption(option)"
      @keydown="onKeydown($event, index)"
    >
      <span>{{ option.label }}</span>
    </button>
  </nav>
</template>

<style scoped>
.liquid-glass-segmented {
  position: relative;
  isolation: isolate;
  display: grid;
  grid-template-columns: repeat(var(--liquid-count), minmax(0, 1fr));
  width: fit-content;
  min-width: min(100%, 252px);
  min-height: 40px;
  padding: 3px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.68);
  border-radius: 14px;
  background: rgba(220, 232, 247, 0.58);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.78),
    inset 0 -1px 0 rgba(61, 79, 103, 0.08),
    0 5px 18px rgba(36, 51, 72, 0.1);
  backdrop-filter: blur(18px) saturate(170%);
  -webkit-backdrop-filter: blur(18px) saturate(170%);
}

.liquid-glass-segmented::before {
  position: absolute;
  inset: 0;
  z-index: -2;
  background:
    linear-gradient(110deg, rgba(255, 255, 255, 0.62), rgba(255, 255, 255, 0.08) 42%, rgba(89, 145, 219, 0.1));
  content: '';
  pointer-events: none;
}

.liquid-glass-indicator {
  position: absolute;
  top: 3px;
  bottom: 3px;
  left: 3px;
  z-index: -1;
  width: calc((100% - 6px) / var(--liquid-count));
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.88);
  border-radius: 11px;
  background: rgba(255, 255, 255, 0.66);
  box-shadow:
    inset 0 1px 1px rgba(255, 255, 255, 0.92),
    inset 0 -1px 1px rgba(63, 83, 111, 0.08),
    0 4px 12px rgba(31, 48, 71, 0.12);
  transform: translateX(calc(var(--liquid-index) * 100%));
  transition:
    transform 520ms cubic-bezier(0.22, 1.35, 0.36, 1),
    border-radius 260ms ease,
    box-shadow 260ms ease;
  will-change: transform;
}

.liquid-glass-indicator::after {
  position: absolute;
  inset: 1px 12% auto;
  height: 42%;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.78), transparent);
  content: '';
  opacity: 0.78;
}

.liquid-glass-option {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: 34px;
  padding: 0 18px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--slate, #64748b);
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0;
  cursor: pointer;
  transition: color 220ms ease, transform 180ms ease;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
}

.liquid-glass-option span {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.liquid-glass-option:hover:not(:disabled) {
  color: var(--ink, #18212f);
}

.liquid-glass-option:active:not(:disabled) {
  transform: scale(0.94, 1.06);
}

.liquid-glass-option.is-active {
  color: var(--accent, #1669c9);
  text-shadow: 0 1px 0 rgba(255, 255, 255, 0.72);
}

.liquid-glass-option:focus-visible {
  outline: 2px solid rgba(0, 113, 227, 0.45);
  outline-offset: -3px;
}

.liquid-glass-option:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.liquid-glass-segmented.is-busy .liquid-glass-indicator::before {
  position: absolute;
  inset: 0;
  background: linear-gradient(100deg, transparent 18%, rgba(255, 255, 255, 0.72) 48%, transparent 78%);
  content: '';
  transform: translateX(-120%);
  animation: liquidGlassSheen 1.15s ease-in-out infinite;
}

@keyframes liquidGlassSheen {
  to { transform: translateX(120%); }
}

@media (max-width: 560px) {
  .liquid-glass-segmented {
    width: 100%;
    min-width: 0;
    min-height: 42px;
    border-radius: 13px;
  }

  .liquid-glass-option {
    min-height: 36px;
    padding: 0 8px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .liquid-glass-indicator,
  .liquid-glass-option {
    transition-duration: 0.01ms;
  }

  .liquid-glass-segmented.is-busy .liquid-glass-indicator::before {
    animation: none;
  }
}
</style>
