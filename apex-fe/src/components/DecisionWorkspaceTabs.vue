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
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.78);
  border-radius: 12px;
  background: rgba(220, 232, 247, 0.58);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.92), 0 5px 16px rgba(42, 65, 94, 0.09);
  backdrop-filter: blur(18px) saturate(175%);
  -webkit-backdrop-filter: blur(18px) saturate(175%);
}

.decision-workspace-tabs a {
  min-width: 96px;
  min-height: 36px;
  display: grid;
  place-items: center;
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: 9px;
  color: var(--slate);
  font-size: 13px;
  font-weight: 650;
  text-decoration: none;
  transition: color 180ms ease, background 220ms ease, box-shadow 220ms ease, transform 160ms ease;
}

.decision-workspace-tabs a[aria-current='page'] {
  border-color: rgba(255, 255, 255, 0.9);
  background: rgba(255, 255, 255, 0.74);
  color: var(--accent);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.96), 0 3px 10px rgba(45, 73, 108, 0.13);
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
