<script setup>
import { computed } from 'vue'
import { securityMarketBadge } from '../utils/securityMarket.js'

const props = defineProps({
  security: {
    type: Object,
    required: true,
  },
  includeMain: {
    type: Boolean,
    default: false,
  },
})

const badge = computed(() => securityMarketBadge(props.security, { includeMain: props.includeMain }))
</script>

<template>
  <span
    v-if="badge"
    class="security-market-badge"
    :class="`is-${badge.tone}`"
    :title="badge.title"
  >{{ badge.label }}</span>
</template>

<style scoped>
.security-market-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  border: 1px solid #e3bb8d;
  border-radius: 3px;
  box-sizing: border-box;
  color: #a85d1e;
  background: #fffaf5;
  font-size: 9px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0;
}
</style>
