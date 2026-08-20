<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DataAnalysis, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchFactorCenter } from '../api/factor'
import { searchStock } from '../api/stock'
import StockIdentity from '../components/StockIdentity.vue'

const route = useRoute()
const router = useRouter()
const code = ref(String(route.query.code || '600519'))
const loading = ref(false)
const detail = ref(null)
let requestSeq = 0

const scoreTone = computed(() => {
  if (detail.value?.alphaScore == null || detail.value?.alphaScore === '') return 'is-missing'
  const score = Number(detail.value?.alphaScore)
  if (Number.isNaN(score)) return 'is-missing'
  if (score >= 75) return 'is-strong'
  if (score >= 60) return 'is-positive'
  if (score >= 40) return 'is-neutral'
  return 'is-weak'
})

async function loadDetail(nextCode = code.value) {
  const query = String(nextCode || '').trim()
  if (!query) {
    ElMessage.warning('请输入证券代码或名称')
    return
  }
  const currentRequest = ++requestSeq
  loading.value = true
  try {
    const securityCode = await resolveSecurityCode(query)
    const response = await fetchFactorCenter(securityCode)
    if (currentRequest !== requestSeq) return
    detail.value = response.data || null
    code.value = response.data?.code || securityCode
    await router.replace({ query: { ...route.query, code: code.value } })
  } catch (error) {
    if (currentRequest !== requestSeq) return
    detail.value = null
    ElMessage.error(error.message || '因子数据加载失败')
  } finally {
    if (currentRequest === requestSeq) loading.value = false
  }
}

async function resolveSecurityCode(query) {
  const codeMatch = query.match(/\d{6}/)
  if (codeMatch) return codeMatch[0]
  const response = await searchStock(query, 10)
  const candidates = response.data || []
  const exactStock = candidates.find((stock) => String(stock.name || '').trim() === query)
  if (!exactStock?.code) throw new Error(`未找到证券: ${query}`)
  return exactStock.code
}

async function querySuggestions(keyword, callback) {
  const query = String(keyword || '').trim()
  if (!query) {
    callback([])
    return
  }
  try {
    const response = await searchStock(query, 10)
    callback((response.data || []).map((stock) => ({
      ...stock,
      value: `${stock.name || stock.code} ${stock.code}`,
    })))
  } catch {
    callback([])
  }
}

function selectStock(stock) {
  if (!stock?.code) return
  code.value = stock.code
  loadDetail(stock.code)
}

function formatNumber(value, digits = 2) {
  if (value == null || value === '') return '-'
  const number = Number(value)
  return Number.isNaN(number) ? String(value) : number.toFixed(digits)
}

function formatFactor(factor) {
  if (factor?.status === 'MISSING') return '暂无数据'
  if (factor?.displayValue) return factor.displayValue
  return `${formatNumber(factor?.value)}${factor?.unit || ''}`
}

function formatRawValue(component) {
  if (!component?.available || component.rawValue == null) return '暂无数据'
  if (component.key === 'VOLUME') return `${formatNumber(component.rawValue)}倍`
  return `${formatNumber(component.rawValue)}%`
}

function goStockDetail() {
  if (detail.value?.code) router.push(`/stock/${detail.value.code}`)
}

watch(
  () => route.query.code,
  (value) => {
    if (value && String(value) !== detail.value?.code) {
      code.value = String(value)
      loadDetail(value)
    }
  },
)

onMounted(() => loadDetail())
</script>

<template>
  <main class="page factor-center-page" v-loading="loading">
    <header class="header factor-header">
      <div>
        <p class="eyebrow">Factor</p>
        <h1>因子中心</h1>
        <p>统一观察估值、成长、动量、技术、资金与市场状态，评分只使用本地可追溯数据。</p>
      </div>
      <div class="factor-query">
        <el-autocomplete
          v-model="code"
          :fetch-suggestions="querySuggestions"
          placeholder="代码或股票名称"
          clearable
          value-key="value"
          @select="selectStock"
          @keyup.enter="loadDetail()"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
          <template #default="{ item }">
            <span class="suggestion-name">{{ item.name }}</span>
            <span class="suggestion-code">{{ item.code }}</span>
          </template>
        </el-autocomplete>
        <el-button type="primary" :icon="DataAnalysis" @click="loadDetail()">分析</el-button>
      </div>
    </header>

    <template v-if="detail">
      <section class="security-strip">
        <div>
          <StockIdentity :security="detail" prominent include-main />
          <span v-if="detail.industry" class="industry">{{ detail.industry }}</span>
        </div>
        <div class="security-meta">
          <span>现价 {{ formatNumber(detail.latestPrice) }}</span>
          <span>日线截至 {{ detail.asOf || '-' }}</span>
          <el-button text @click="goStockDetail">个股详情</el-button>
        </div>
      </section>

      <div class="factor-layout">
        <aside class="alpha-panel" :class="scoreTone">
          <div class="alpha-heading">
            <div>
              <span>Alpha Score</span>
              <strong>{{ detail.alphaLabel }}</strong>
              <small>{{ detail.scoreModel }}</small>
            </div>
            <div class="alpha-score">{{ formatNumber(detail.alphaScore, 1) }}</div>
          </div>
          <div class="coverage-row">
            <span>数据覆盖</span>
            <strong>{{ formatNumber(detail.coverage, 0) }}%</strong>
          </div>
          <el-progress
            :percentage="Number(detail.coverage || 0)"
            :show-text="false"
            :stroke-width="6"
          />

          <div class="component-list">
            <div
              v-for="component in detail.alphaComponents"
              :key="component.key"
              class="component-row"
              :class="{ 'is-missing': !component.available }"
            >
              <div class="component-name">
                <strong>{{ component.label }}</strong>
                <span>{{ formatRawValue(component) }}</span>
              </div>
              <div class="component-score">
                <b>{{ component.available ? formatNumber(component.score, 0) : '-' }}</b>
                <span>{{ formatNumber(component.weight, 0) }}%</span>
              </div>
              <div class="component-track">
                <i :style="{ width: `${component.available ? component.score : 0}%` }"></i>
              </div>
              <small class="component-date">
                {{ component.asOf ? `截至 ${component.asOf}` : '时点缺失' }}
              </small>
            </div>
          </div>

          <div class="formula" title="规则综合分权重">
            <span>Momentum 30%</span>
            <span>ROE 20%</span>
            <span>Earnings Growth 20%</span>
            <span>Volume 15%</span>
            <span>Market Strength 15%</span>
          </div>
          <p class="alpha-note">{{ detail.message }}</p>
        </aside>

        <section class="factor-categories" aria-label="因子分类">
          <article
            v-for="category in detail.categories"
            :key="category.key"
            class="factor-category"
          >
            <header>
              <h2>{{ category.label }}</h2>
              <p>{{ category.description }}</p>
            </header>
            <div class="factor-items">
              <div
                v-for="factor in category.factors"
                :key="factor.key"
                class="factor-item"
                :class="{ 'is-missing': factor.status === 'MISSING' }"
                :title="factor.description"
              >
                <span>{{ factor.label }}</span>
                <strong>{{ formatFactor(factor) }}</strong>
                <small>{{ factor.asOf ? `截至 ${factor.asOf}` : '时点缺失' }}</small>
              </div>
            </div>
          </article>
        </section>
      </div>
    </template>

    <el-empty v-else-if="!loading" description="输入股票代码开始因子分析" />
  </main>
</template>

<style scoped>
.factor-center-page {
  max-width: 1500px;
  margin: 0 auto;
}

.factor-header {
  align-items: flex-end;
}

.factor-query {
  display: grid;
  grid-template-columns: minmax(220px, 300px) auto;
  gap: 8px;
  width: min(100%, 400px);
}

.suggestion-name {
  margin-right: 10px;
  color: var(--ink);
}

.suggestion-code {
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}

.security-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 62px;
  padding: 10px 0 14px;
  border-bottom: 1px solid var(--line-strong);
}

.security-strip > div,
.security-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.industry {
  padding-left: 12px;
  border-left: 1px solid var(--line);
  color: var(--slate);
  font-size: 13px;
}

.security-meta {
  color: var(--slate);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.factor-layout {
  display: grid;
  grid-template-columns: minmax(260px, 340px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
  margin-top: 16px;
}

.alpha-panel,
.factor-category {
  background: var(--glass);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-soft);
}

.alpha-panel {
  position: sticky;
  top: 72px;
  padding: 18px;
  border-top: 3px solid var(--accent);
}

.alpha-panel.is-strong,
.alpha-panel.is-positive {
  border-top-color: var(--down);
}

.alpha-panel.is-weak {
  border-top-color: var(--up);
}

.alpha-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.alpha-heading > div:first-child {
  display: grid;
  gap: 4px;
}

.alpha-heading span {
  color: var(--slate);
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
}

.alpha-heading strong {
  font-size: 16px;
}

.alpha-heading small {
  color: var(--muted);
  font-size: 10px;
}

.alpha-score {
  min-width: 92px;
  text-align: right;
  color: var(--ink);
  font-size: 42px;
  font-weight: 680;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.coverage-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 7px;
  color: var(--slate);
  font-size: 12px;
}

.component-list {
  display: grid;
  gap: 14px;
  margin: 22px 0;
}

.component-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 6px 10px;
}

.component-name,
.component-score {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.component-name strong {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.component-name span,
.component-score span {
  color: var(--muted);
  font-size: 11px;
}

.component-score b {
  min-width: 24px;
  text-align: right;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.component-track {
  grid-column: 1 / -1;
  height: 4px;
  overflow: hidden;
  background: var(--paper-deep);
  border-radius: 2px;
}

.component-date {
  grid-column: 1 / -1;
  color: var(--muted);
  font-size: 10px;
}

.component-track i {
  display: block;
  height: 100%;
  background: var(--accent);
}

.component-row.is-missing {
  opacity: 0.55;
}

.formula {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
  color: var(--slate);
  font-size: 11px;
}

.alpha-note {
  margin: 12px 0 0;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.6;
}

.factor-categories {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.factor-category {
  min-width: 0;
  padding: 16px;
}

.factor-category > header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.factor-category h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 650;
}

.factor-category p {
  margin: 0;
  overflow: hidden;
  color: var(--muted);
  font-size: 11px;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.factor-items {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border-top: 1px solid var(--line);
  border-left: 1px solid var(--line);
}

.factor-item {
  display: grid;
  min-height: 84px;
  align-content: center;
  gap: 7px;
  min-width: 0;
  padding: 10px 12px;
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.factor-item span {
  color: var(--slate);
  font-size: 12px;
}

.factor-item strong {
  overflow: hidden;
  font-size: 18px;
  font-weight: 620;
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.factor-item small {
  overflow: hidden;
  color: var(--muted);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.factor-item.is-missing strong {
  color: var(--muted);
  font-size: 13px;
  font-weight: 500;
}

@media (max-width: 1020px) {
  .factor-layout {
    grid-template-columns: minmax(240px, 300px) minmax(0, 1fr);
  }

  .factor-categories {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 820px) {
  .factor-center-page {
    --page-title-size: 24px;
    padding: 16px 14px 36px;
  }

  .factor-header {
    display: grid;
  }

  .factor-query {
    grid-template-columns: minmax(0, 1fr) auto;
    width: 100%;
  }

  .factor-query :deep(.el-input__wrapper),
  .factor-query :deep(.el-button) {
    min-height: 44px;
  }

  .security-strip,
  .security-meta {
    align-items: flex-start;
  }

  .security-strip {
    display: grid;
  }

  .security-meta {
    flex-wrap: wrap;
  }

  .factor-layout,
  .factor-categories {
    grid-template-columns: minmax(0, 1fr);
  }

  .alpha-panel {
    position: static;
  }

  .factor-category > header {
    display: block;
  }

  .factor-category p {
    margin-top: 4px;
    text-align: left;
  }
}

@media (max-width: 420px) {
  .factor-items {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
