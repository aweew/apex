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
  flex: 0 0 18px;
  width: 18px;
  height: 18px;
  border: 1px solid transparent;
  border-radius: 4px;
  box-sizing: border-box;
  font-size: 10px;
  font-weight: 750;
  line-height: 1;
}

.security-market-badge.is-star {
  color: #0a66c2;
  background: rgba(0, 113, 227, 0.09);
  border-color: rgba(0, 113, 227, 0.18);
}

.security-market-badge.is-chinext {
  color: #16775d;
  background: rgba(42, 157, 143, 0.1);
  border-color: rgba(42, 157, 143, 0.2);
}

.security-market-badge.is-bj {
  color: #a86400;
  background: rgba(255, 159, 10, 0.11);
  border-color: rgba(255, 159, 10, 0.22);
}

.security-market-badge.is-hk {
  color: #6b4fbb;
  background: rgba(107, 79, 187, 0.1);
  border-color: rgba(107, 79, 187, 0.2);
}

.security-market-badge.is-us {
  color: #515154;
  background: rgba(0, 0, 0, 0.05);
  border-color: rgba(0, 0, 0, 0.1);
}

.security-market-badge.is-sh {
  color: #4d6074;
  background: rgba(71, 85, 105, 0.08);
  border-color: rgba(71, 85, 105, 0.18);
}

.security-market-badge.is-sz {
  color: #176b72;
  background: rgba(13, 148, 136, 0.08);
  border-color: rgba(13, 148, 136, 0.18);
}
</style>
