<script setup>
import { computed, useSlots } from 'vue'
import { boardTagTitle, resolveBoardTag } from '../utils/marketBoard.js'

const props = defineProps({
  /** 证券代码 */
  code: { type: [String, Number], default: '' },
  /** 交易所 SH/SZ/BJ/HK/US（可选） */
  market: { type: String, default: '' },
})

const slots = useSlots()
const label = computed(() => resolveBoardTag(props.code, props.market))
const title = computed(() => boardTagTitle(label.value))
const hasSlot = computed(() => !!slots.default)
const visible = computed(() => !!label.value || hasSlot.value)
</script>

<template>
  <span v-if="visible" class="stock-board-wrap">
    <i
      v-if="label"
      class="stock-board-tag"
      :class="'board-' + label"
      :title="title"
    >{{ label }}</i>
    <slot />
  </span>
</template>

<style scoped>
.stock-board-wrap {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  max-width: 100%;
  vertical-align: middle;
}

.stock-board-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  flex-shrink: 0;
  min-width: 16px;
  height: 16px;
  padding: 0 3px;
  border-radius: 3px;
  font-size: 10px;
  font-style: normal;
  font-weight: 700;
  line-height: 1;
  color: #fff;
  letter-spacing: 0;
}

.stock-board-tag.board-科 {
  background: #0071e3;
}

.stock-board-tag.board-创 {
  background: #ff9f0a;
}

.stock-board-tag.board-京 {
  background: #34c759;
}

.stock-board-tag.board-港 {
  background: #ff3b30;
}

.stock-board-tag.board-美 {
  background: #5856d6;
}
</style>
