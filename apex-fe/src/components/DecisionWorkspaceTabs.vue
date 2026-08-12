<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <nav class="decision-workspace-tabs" aria-label="决策工作区">
    <RouterLink to="/decision" :aria-current="route.path === '/decision' ? 'page' : undefined">
      决策清单
    </RouterLink>
    <RouterLink to="/signals" :aria-current="route.path === '/signals' ? 'page' : undefined">
      策略信号
    </RouterLink>
  </nav>
</template>

<style scoped>
.decision-workspace-tabs {
  width: fit-content;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 3px;
  margin: 0 0 14px;
  padding: 3px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--paper-deep);
}

.decision-workspace-tabs a {
  min-width: 96px;
  min-height: 36px;
  display: grid;
  place-items: center;
  padding: 0 14px;
  border-radius: 6px;
  color: var(--slate);
  font-size: 13px;
  font-weight: 650;
  text-decoration: none;
}

.decision-workspace-tabs a[aria-current='page'] {
  background: #fff;
  color: var(--ink);
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.1);
}

.decision-workspace-tabs a:focus-visible {
  outline: 3px solid rgba(0, 113, 227, 0.2);
  outline-offset: 1px;
}

@media (max-width: 900px) {
  .decision-workspace-tabs {
    width: 100%;
    margin-bottom: 12px;
  }

  .decision-workspace-tabs a {
    min-width: 0;
    min-height: 40px;
  }
}
</style>
