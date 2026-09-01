<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, SortDown, SortUp } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getCurrentUser } from '../api/auth'
import { fetchCapitalFlowOverview, refreshCapitalFlow } from '../api/capitalFlow'
import StockIdentity from '../components/StockIdentity.vue'
import {
  formatCapitalAmount,
  formatCapitalPercent,
  formatCapitalPrice,
  resolveCapitalClass,
  sortDragonTigerItems,
  sortStockFlowItems,
} from '../utils/capitalFlow.js'
import { staleDataTime } from '../utils/dataFreshness.js'

const router = useRouter()
defineProps({
  embedded: {
    type: Boolean,
    default: false,
  },
})
const currentUser = getCurrentUser()
const isAdmin = computed(() => currentUser?.role === 'ADMIN')
const loading = ref(false)
const refreshingMode = ref('')
const overview = ref({})
const dragonTigerSort = ref({ prop: 'netBuyAmount', order: 'descending' })
const stockFlowSort = ref({ prop: 'mainNetInflow', order: 'descending' })
const dragonTigerSortOptions = [
  { value: 'netBuyAmount', label: '净买额' },
  { value: 'pctChg', label: '涨跌幅' },
  { value: 'turnoverRate', label: '换手率' },
  { value: 'amount', label: '成交额' },
  { value: 'buyAmount', label: '买入额' },
  { value: 'sellAmount', label: '卖出额' },
  { value: 'closePrice', label: '收盘价' },
  { value: 'name', label: '股票名称' },
]
const stockFlowSortOptions = [
  { value: 'mainNetInflow', label: '主力净流入' },
  { value: 'mainNetInflowPct', label: '主力占比' },
  { value: 'pctChg', label: '涨跌幅' },
  { value: 'superLargeNetInflow', label: '超大单' },
  { value: 'largeNetInflow', label: '大单' },
  { value: 'mediumNetInflow', label: '中单' },
  { value: 'smallNetInflow', label: '小单' },
  { value: 'name', label: '股票名称' },
]

const stockFlows = computed(() => overview.value?.stockFlows || [])
const industryFlows = computed(() => overview.value?.industryFlows?.items || [])
const conceptFlows = computed(() => overview.value?.conceptFlows?.items || [])
const dragonTigerItems = computed(() => overview.value?.dragonTigerItems || [])
const sortedDragonTigerItems = computed(() => sortDragonTigerItems(
  dragonTigerItems.value,
  dragonTigerSort.value.prop,
  dragonTigerSort.value.order,
))
const sortedStockFlows = computed(() => sortStockFlowItems(
  stockFlows.value,
  stockFlowSort.value.prop,
  stockFlowSort.value.order,
))
const dragonTigerSortIcon = computed(() => (
  dragonTigerSort.value.order === 'descending' ? SortDown : SortUp
))
const stockFlowSortIcon = computed(() => (
  stockFlowSort.value.order === 'descending' ? SortDown : SortUp
))

function snapshotMeta(tradeDate, syncedAt, intraday = false) {
  if (!tradeDate && !syncedAt) return '暂无快照'
  return staleDataTime({ tradeDate, updatedAt: syncedAt, intraday })
}

function openStock(stock) {
  if (stock?.code) router.push(`/stock/${stock.code}`)
}

function onDragonTigerSortChange({ prop, order }) {
  if (!prop || !order) return
  dragonTigerSort.value = { prop, order }
}

function toggleDragonTigerSortOrder() {
  dragonTigerSort.value = {
    ...dragonTigerSort.value,
    order: dragonTigerSort.value.order === 'descending' ? 'ascending' : 'descending',
  }
}

function onStockFlowSortChange({ prop, order }) {
  if (!prop || !order) return
  stockFlowSort.value = { prop, order }
}

function toggleStockFlowSortOrder() {
  stockFlowSort.value = {
    ...stockFlowSort.value,
    order: stockFlowSort.value.order === 'descending' ? 'ascending' : 'descending',
  }
}

async function loadOverview() {
  loading.value = true
  try {
    const response = await fetchCapitalFlowOverview(20)
    overview.value = response.data || {}
  } catch (error) {
    ElMessage.error(error.message || '资金面加载失败')
  } finally {
    loading.value = false
  }
}

async function onRefresh(mode) {
  if (!isAdmin.value || refreshingMode.value) return
  refreshingMode.value = mode
  try {
    const response = await refreshCapitalFlow(mode)
    overview.value = response.data || {}
    ElMessage.success('资金面已更新')
  } catch (error) {
    ElMessage.error(error.message || '资金面更新失败')
  } finally {
    refreshingMode.value = ''
  }
}

onMounted(loadOverview)
</script>

<template>
  <div :class="['capital-flow-page', { page: !embedded, embedded }]" v-loading="loading">
    <header v-if="!embedded" class="header capital-flow-header">
      <div>
        <p class="eyebrow">Capital Flow</p>
        <h1>资金面</h1>
        <p>主力净流入、板块资金与龙虎榜的本地快照</p>
      </div>
      <div v-if="isAdmin" class="actions">
        <el-dropdown :disabled="Boolean(refreshingMode)" @command="onRefresh">
          <el-button
            class="capital-refresh-button"
            type="primary"
            :icon="Refresh"
            :loading="Boolean(refreshingMode)"
            aria-label="刷新资金面"
          >
            <span class="refresh-label">刷新</span>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="flow">刷新资金流</el-dropdown-item>
              <el-dropdown-item command="lhb">刷新龙虎榜</el-dropdown-item>
              <el-dropdown-item command="all" divided>全部刷新</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>
    <section v-else class="capital-flow-embedded-header" aria-label="资金流">
      <div>
        <h2>资金流</h2>
        <p>主力净流入、板块资金与龙虎榜的本地快照</p>
      </div>
      <div v-if="isAdmin" class="actions">
        <el-dropdown :disabled="Boolean(refreshingMode)" @command="onRefresh">
          <el-button
            class="capital-refresh-button"
            type="primary"
            :icon="Refresh"
            :loading="Boolean(refreshingMode)"
            aria-label="刷新资金流"
          >
            刷新
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="flow">刷新资金流</el-dropdown-item>
              <el-dropdown-item command="lhb">刷新龙虎榜</el-dropdown-item>
              <el-dropdown-item command="all" divided>全部刷新</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </section>

    <section id="dragon-tiger" class="flow-section dragon-tiger-section" aria-labelledby="dragon-tiger-title">
      <div class="section-heading">
        <div>
          <h2 id="dragon-tiger-title">龙虎榜</h2>
          <p v-if="snapshotMeta(overview.dragonTigerTradeDate, overview.dragonTigerSyncedAt)">
            {{ snapshotMeta(overview.dragonTigerTradeDate, overview.dragonTigerSyncedAt) }}
          </p>
        </div>
        <div class="dragon-heading-actions">
          <div class="dragon-sort-mobile" aria-label="龙虎榜排序">
            <el-select v-model="dragonTigerSort.prop" size="small" aria-label="排序字段">
              <el-option
                v-for="option in dragonTigerSortOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-tooltip :content="dragonTigerSort.order === 'descending' ? '当前降序，点击切换升序' : '当前升序，点击切换降序'">
              <el-button
                :icon="dragonTigerSortIcon"
                circle
                size="small"
                :aria-label="dragonTigerSort.order === 'descending' ? '切换为升序' : '切换为降序'"
                @click="toggleDragonTigerSortOrder"
              />
            </el-tooltip>
          </div>
          <span class="row-count">{{ dragonTigerItems.length }} 只</span>
        </div>
      </div>
      <el-table
        v-if="dragonTigerItems.length"
        class="desktop-flow-table"
        :data="sortedDragonTigerItems"
        :default-sort="dragonTigerSort"
        stripe
        @sort-change="onDragonTigerSortChange"
      >
        <el-table-column prop="name" label="股票" min-width="150" sortable="custom" :sort-orders="['descending', 'ascending']">
          <template #default="{ row }"><StockIdentity :security="row" :interactive="true" @select="openStock" /></template>
        </el-table-column>
        <el-table-column prop="closePrice" label="收盘价" width="84" align="right" sortable="custom" :sort-orders="['descending', 'ascending']"><template #default="{ row }">{{ formatCapitalPrice(row.closePrice) }}</template></el-table-column>
        <el-table-column prop="pctChg" label="涨跌幅" width="92" align="right" sortable="custom" :sort-orders="['descending', 'ascending']"><template #default="{ row }"><span :class="resolveCapitalClass(row.pctChg)">{{ formatCapitalPercent(row.pctChg) }}</span></template></el-table-column>
        <el-table-column prop="turnoverRate" label="换手率" width="88" align="right" sortable="custom" :sort-orders="['descending', 'ascending']"><template #default="{ row }">{{ formatCapitalPercent(row.turnoverRate) }}</template></el-table-column>
        <el-table-column prop="netBuyAmount" label="净买额" min-width="108" align="right" sortable="custom" :sort-orders="['descending', 'ascending']"><template #default="{ row }"><span :class="resolveCapitalClass(row.netBuyAmount)">{{ formatCapitalAmount(row.netBuyAmount) }}</span></template></el-table-column>
        <el-table-column prop="buyAmount" label="买入额" min-width="104" align="right" sortable="custom" :sort-orders="['descending', 'ascending']"><template #default="{ row }">{{ formatCapitalAmount(row.buyAmount) }}</template></el-table-column>
        <el-table-column prop="sellAmount" label="卖出额" min-width="104" align="right" sortable="custom" :sort-orders="['descending', 'ascending']"><template #default="{ row }">{{ formatCapitalAmount(row.sellAmount) }}</template></el-table-column>
        <el-table-column prop="amount" label="成交额" min-width="104" align="right" sortable="custom" :sort-orders="['descending', 'ascending']"><template #default="{ row }">{{ formatCapitalAmount(row.amount) }}</template></el-table-column>
        <el-table-column prop="reason" label="上榜原因" min-width="220" show-overflow-tooltip />
      </el-table>
      <div v-if="dragonTigerItems.length" class="mobile-flow-list">
        <article
          v-for="row in sortedDragonTigerItems"
          :key="`${row.code}-${row.tradeDate}-${row.reason}`"
          class="flow-card dragon-tiger-card"
          :class="`dragon-${resolveCapitalClass(row.netBuyAmount)}`"
        >
          <div class="flow-card-head dragon-card-head">
            <StockIdentity :security="row" :interactive="true" @select="openStock" />
            <span :class="resolveCapitalClass(row.pctChg)">{{ formatCapitalPercent(row.pctChg) }}</span>
          </div>
          <div class="dragon-card-primary">
            <span>龙虎榜净买额</span>
            <strong :class="resolveCapitalClass(row.netBuyAmount)">{{ formatCapitalAmount(row.netBuyAmount) }}</strong>
          </div>
          <div class="dragon-card-market">
            <span><small>收盘价</small><b>{{ formatCapitalPrice(row.closePrice) }}</b></span>
            <span><small>换手率</small><b>{{ formatCapitalPercent(row.turnoverRate) }}</b></span>
            <span><small>成交额</small><b>{{ formatCapitalAmount(row.amount) }}</b></span>
          </div>
          <div class="dragon-card-flow">
            <span><small>买入额</small><b>{{ formatCapitalAmount(row.buyAmount) }}</b></span>
            <span><small>卖出额</small><b>{{ formatCapitalAmount(row.sellAmount) }}</b></span>
          </div>
          <div class="dragon-reason">
            <span class="dragon-reason-label">上榜原因</span>
            <p>{{ row.reason || '暂无上榜原因' }}</p>
          </div>
        </article>
      </div>
      <el-empty v-if="!dragonTigerItems.length" :image-size="64" description="暂无龙虎榜快照" />
    </section>

    <section class="flow-section sector-flow-section" aria-labelledby="sector-flow-title">
      <div class="section-heading">
        <div>
          <h2 id="sector-flow-title">板块净流入</h2>
          <p>行业与概念排名分开展示</p>
        </div>
      </div>
      <div class="sector-flow-grid">
        <div class="sector-column">
          <div class="subsection-heading">
            <h3>行业</h3>
            <span v-if="snapshotMeta(overview.industryFlows?.tradeDate, overview.industryFlows?.syncedAt, true)">
              {{ snapshotMeta(overview.industryFlows?.tradeDate, overview.industryFlows?.syncedAt, true) }}
            </span>
          </div>
          <el-table v-if="industryFlows.length" class="desktop-flow-table" :data="industryFlows" stripe>
            <el-table-column type="index" label="#" width="52" />
            <el-table-column prop="name" label="板块" min-width="124" show-overflow-tooltip />
            <el-table-column label="涨跌幅" width="92" align="right">
              <template #default="{ row }"><span :class="resolveCapitalClass(row.pctChg)">{{ formatCapitalPercent(row.pctChg) }}</span></template>
            </el-table-column>
            <el-table-column label="净流入" min-width="106" align="right">
              <template #default="{ row }"><span :class="resolveCapitalClass(row.netInflow)">{{ formatCapitalAmount(row.netInflow) }}</span></template>
            </el-table-column>
          </el-table>
          <div v-if="industryFlows.length" class="mobile-flow-list">
            <article v-for="(row, index) in industryFlows" :key="row.code || row.name" class="flow-card sector-flow-card">
              <div class="flow-card-head"><strong>{{ index + 1 }}. {{ row.name }}</strong><span :class="resolveCapitalClass(row.pctChg)">{{ formatCapitalPercent(row.pctChg) }}</span></div>
              <div class="sector-net"><small>净流入</small><b class="flow-card-value" :class="resolveCapitalClass(row.netInflow)">{{ formatCapitalAmount(row.netInflow) }}</b></div>
            </article>
          </div>
          <el-empty v-if="!industryFlows.length" :image-size="56" description="暂无行业资金流" />
        </div>

        <div class="sector-column">
          <div class="subsection-heading">
            <h3>概念</h3>
            <span v-if="snapshotMeta(overview.conceptFlows?.tradeDate, overview.conceptFlows?.syncedAt, true)">
              {{ snapshotMeta(overview.conceptFlows?.tradeDate, overview.conceptFlows?.syncedAt, true) }}
            </span>
          </div>
          <el-table v-if="conceptFlows.length" class="desktop-flow-table" :data="conceptFlows" stripe>
            <el-table-column type="index" label="#" width="52" />
            <el-table-column prop="name" label="板块" min-width="124" show-overflow-tooltip />
            <el-table-column label="涨跌幅" width="92" align="right">
              <template #default="{ row }"><span :class="resolveCapitalClass(row.pctChg)">{{ formatCapitalPercent(row.pctChg) }}</span></template>
            </el-table-column>
            <el-table-column label="净流入" min-width="106" align="right">
              <template #default="{ row }"><span :class="resolveCapitalClass(row.netInflow)">{{ formatCapitalAmount(row.netInflow) }}</span></template>
            </el-table-column>
          </el-table>
          <div v-if="conceptFlows.length" class="mobile-flow-list">
            <article v-for="(row, index) in conceptFlows" :key="row.code || row.name" class="flow-card sector-flow-card">
              <div class="flow-card-head"><strong>{{ index + 1 }}. {{ row.name }}</strong><span :class="resolveCapitalClass(row.pctChg)">{{ formatCapitalPercent(row.pctChg) }}</span></div>
              <div class="sector-net"><small>净流入</small><b class="flow-card-value" :class="resolveCapitalClass(row.netInflow)">{{ formatCapitalAmount(row.netInflow) }}</b></div>
            </article>
          </div>
          <el-empty v-if="!conceptFlows.length" :image-size="56" description="暂无概念资金流" />
        </div>
      </div>
    </section>

    <section class="flow-section stock-flow-section" aria-labelledby="stock-flow-title">
      <div class="section-heading">
        <div>
          <h2 id="stock-flow-title">个股主力流入</h2>
          <p v-if="snapshotMeta(overview.stockTradeDate, overview.stockSyncedAt, true)">
            {{ snapshotMeta(overview.stockTradeDate, overview.stockSyncedAt, true) }}
          </p>
        </div>
        <div class="stock-heading-actions">
          <div class="stock-sort-mobile" aria-label="个股主力流入排序">
            <el-select v-model="stockFlowSort.prop" size="small" aria-label="排序字段">
              <el-option
                v-for="option in stockFlowSortOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-tooltip :content="stockFlowSort.order === 'descending' ? '当前降序，点击切换升序' : '当前升序，点击切换降序'">
              <el-button
                :icon="stockFlowSortIcon"
                circle
                size="small"
                :aria-label="stockFlowSort.order === 'descending' ? '切换为升序' : '切换为降序'"
                @click="toggleStockFlowSortOrder"
              />
            </el-tooltip>
          </div>
          <span class="row-count">{{ stockFlows.length }} 只</span>
        </div>
      </div>
      <el-table
        v-if="stockFlows.length"
        class="desktop-flow-table"
        :data="sortedStockFlows"
        :default-sort="stockFlowSort"
        stripe
        @sort-change="onStockFlowSortChange"
      >
        <el-table-column prop="name" label="股票" min-width="150" sortable="custom" :sort-orders="['descending', 'ascending']">
          <template #default="{ row }">
            <StockIdentity :security="row" :interactive="true" @select="openStock" />
          </template>
        </el-table-column>
        <el-table-column prop="pctChg" label="涨跌幅" width="92" align="right" sortable="custom" :sort-orders="['descending', 'ascending']">
          <template #default="{ row }"><span :class="resolveCapitalClass(row.pctChg)">{{ formatCapitalPercent(row.pctChg) }}</span></template>
        </el-table-column>
        <el-table-column prop="mainNetInflow" label="主力净流入" min-width="112" align="right" sortable="custom" :sort-orders="['descending', 'ascending']">
          <template #default="{ row }"><span :class="resolveCapitalClass(row.mainNetInflow)">{{ formatCapitalAmount(row.mainNetInflow) }}</span></template>
        </el-table-column>
        <el-table-column prop="mainNetInflowPct" label="主力占比" width="98" align="right" sortable="custom" :sort-orders="['descending', 'ascending']">
          <template #default="{ row }">{{ formatCapitalPercent(row.mainNetInflowPct) }}</template>
        </el-table-column>
        <el-table-column prop="superLargeNetInflow" label="超大单" min-width="104" align="right" sortable="custom" :sort-orders="['descending', 'ascending']">
          <template #default="{ row }">{{ formatCapitalAmount(row.superLargeNetInflow) }}</template>
        </el-table-column>
        <el-table-column prop="largeNetInflow" label="大单" min-width="104" align="right" sortable="custom" :sort-orders="['descending', 'ascending']">
          <template #default="{ row }">{{ formatCapitalAmount(row.largeNetInflow) }}</template>
        </el-table-column>
        <el-table-column prop="mediumNetInflow" label="中单" min-width="104" align="right" sortable="custom" :sort-orders="['descending', 'ascending']">
          <template #default="{ row }">{{ formatCapitalAmount(row.mediumNetInflow) }}</template>
        </el-table-column>
        <el-table-column prop="smallNetInflow" label="小单" min-width="104" align="right" sortable="custom" :sort-orders="['descending', 'ascending']">
          <template #default="{ row }">{{ formatCapitalAmount(row.smallNetInflow) }}</template>
        </el-table-column>
      </el-table>
      <div v-if="stockFlows.length" class="mobile-flow-list">
        <article v-for="row in sortedStockFlows" :key="row.code" class="flow-card">
          <div class="flow-card-head">
            <StockIdentity :security="row" :interactive="true" @select="openStock" />
            <span :class="resolveCapitalClass(row.pctChg)">{{ formatCapitalPercent(row.pctChg) }}</span>
          </div>
          <div class="flow-card-metrics">
            <span><small>主力</small><b class="flow-card-value" :class="resolveCapitalClass(row.mainNetInflow)">{{ formatCapitalAmount(row.mainNetInflow) }}</b></span>
            <span><small>主力占比</small><b class="flow-card-value">{{ formatCapitalPercent(row.mainNetInflowPct) }}</b></span>
            <span><small>超大单</small><b class="flow-card-value">{{ formatCapitalAmount(row.superLargeNetInflow) }}</b></span>
            <span><small>大单</small><b class="flow-card-value">{{ formatCapitalAmount(row.largeNetInflow) }}</b></span>
            <span><small>中单</small><b class="flow-card-value">{{ formatCapitalAmount(row.mediumNetInflow) }}</b></span>
            <span><small>小单</small><b class="flow-card-value">{{ formatCapitalAmount(row.smallNetInflow) }}</b></span>
          </div>
        </article>
      </div>
      <el-empty v-if="!stockFlows.length" :image-size="64" description="暂无个股资金流快照" />
    </section>
  </div>
</template>

<style scoped>
.capital-flow-page {
  max-width: 1480px;
  margin: 0 auto;
}

.capital-flow-page.embedded {
  max-width: none;
}

.capital-flow-embedded-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.capital-flow-embedded-header h2 {
  margin: 0;
  color: var(--ink);
  font-size: 18px;
  font-weight: 650;
}

.capital-flow-embedded-header p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12px;
}

.section-heading,
.subsection-heading,
.flow-card-head {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-heading {
  margin-bottom: 12px;
}

.dragon-tiger-section {
  scroll-margin-top: 80px;
}

.dragon-heading-actions,
.stock-heading-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dragon-sort-mobile,
.stock-sort-mobile {
  display: none;
}

.section-heading h2,
.subsection-heading h3 {
  margin: 0;
  color: var(--ink);
  font-size: 17px;
  font-weight: 650;
  letter-spacing: 0;
}

.section-heading p,
.subsection-heading span {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.row-count {
  color: var(--slate);
  font-size: 12px;
}

.flow-section {
  width: 100%;
  margin: 0 0 28px;
}

.sector-flow-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}

.sector-column {
  min-width: 0;
  padding-top: 12px;
  border-top: 2px solid var(--line-strong);
}

.subsection-heading {
  align-items: baseline;
  margin-bottom: 10px;
}

.mobile-flow-list {
  display: none;
}

.flat {
  color: var(--slate);
}

@media (max-width: 720px) {
  .dragon-tiger-section .section-heading,
  .stock-flow-section .section-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .dragon-heading-actions,
  .stock-heading-actions {
    width: 100%;
    justify-content: space-between;
  }

  .dragon-sort-mobile,
  .stock-sort-mobile {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .dragon-sort-mobile :deep(.el-select),
  .stock-sort-mobile :deep(.el-select) {
    width: 112px;
  }

  .dragon-sort-mobile :deep(.el-button),
  .stock-sort-mobile :deep(.el-button) {
    width: 32px;
    height: 32px;
  }

  .capital-flow-embedded-header {
    align-items: flex-start;
  }

  .capital-flow-embedded-header .actions {
    flex: 0 0 auto;
  }

  .capital-flow-header {
    align-items: center;
  }

  .capital-refresh-button {
    width: 44px;
    height: 44px;
    min-width: 44px;
    padding: 0;
  }

  .refresh-label {
    display: none;
  }

  .sector-flow-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 22px;
  }

  .desktop-flow-table {
    display: none;
  }

  .mobile-flow-list {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    gap: 8px;
  }

  .sector-column .mobile-flow-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .flow-card {
    min-width: 0;
    padding: 12px;
    border: 1px solid var(--glass-border);
    border-radius: var(--radius-sm);
    background: #fff;
    box-shadow: var(--shadow-soft);
  }

  .sector-flow-card {
    padding: 10px;
  }

  .sector-flow-card .flow-card-head {
    gap: 6px;
  }

  .dragon-tiger-section .mobile-flow-list {
    gap: 12px;
  }

  .dragon-tiger-card {
    overflow: hidden;
    padding: 0;
    border-top: 3px solid #94a3b8;
    border-radius: 8px;
    box-shadow: 0 6px 18px rgba(15, 23, 42, 0.08);
  }

  .dragon-tiger-card.dragon-up {
    border-top-color: var(--up, #c45656);
  }

  .dragon-tiger-card.dragon-down {
    border-top-color: var(--down, #1f7a4d);
  }

  .dragon-card-head {
    padding: 12px 14px 10px;
  }

  .dragon-card-primary {
    display: flex;
    min-width: 0;
    align-items: baseline;
    justify-content: space-between;
    gap: 12px;
    margin: 0 14px;
    padding: 12px 0;
    border-top: 1px solid var(--line);
    border-bottom: 1px solid var(--line);
  }

  .dragon-card-primary > span {
    color: var(--muted);
    font-size: 11px;
  }

  .dragon-card-primary > strong {
    min-width: 0;
    font-size: 21px;
    font-variant-numeric: tabular-nums;
    font-weight: 700;
    overflow-wrap: anywhere;
  }

  .dragon-card-market {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    margin: 0 14px;
    padding: 11px 0;
  }

  .dragon-card-market > span,
  .dragon-card-flow > span {
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 4px;
  }

  .dragon-card-market > span + span {
    padding-left: 10px;
    border-left: 1px solid var(--line);
  }

  .dragon-card-market small,
  .dragon-card-flow small {
    color: var(--muted);
    font-size: 10px;
  }

  .dragon-card-market b,
  .dragon-card-flow b {
    min-width: 0;
    color: var(--ink);
    font-size: 13px;
    font-variant-numeric: tabular-nums;
    font-weight: 600;
    overflow-wrap: anywhere;
  }

  .dragon-card-flow {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
    margin: 0 14px 12px;
  }

  .dragon-card-flow > span {
    padding: 9px 10px;
    border-radius: 6px;
    background: #f3f6f8;
  }

  .flow-card-head > span:last-child {
    flex: 0 0 auto;
    font-size: 13px;
    font-weight: 650;
    font-variant-numeric: tabular-nums;
  }

  .flow-card-metrics {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px 14px;
    margin-top: 10px;
    padding-top: 10px;
    border-top: 1px solid var(--line);
  }

  .flow-card-metrics > span,
  .sector-net {
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 3px;
  }

  .flow-card small,
  .sector-net small {
    color: var(--muted);
    font-size: 10px;
  }

  .flow-card-value {
    min-width: 0;
    overflow-wrap: anywhere;
    font-size: 13px;
    font-variant-numeric: tabular-nums;
    font-weight: 600;
  }

  .sector-flow-card .flow-card-head strong {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .sector-net {
    margin-top: 9px;
    padding-top: 9px;
    border-top: 1px solid var(--line);
  }

  .dragon-reason {
    margin: 0;
    padding: 10px 14px 12px;
    border-top: 1px solid var(--line);
    background: #f8fafc;
  }

  .dragon-reason-label {
    display: block;
    color: var(--muted);
    font-size: 10px;
    font-weight: 600;
  }

  .dragon-reason p {
    margin: 4px 0 0;
    color: var(--slate);
    font-size: 12px;
    line-height: 1.5;
    overflow-wrap: anywhere;
  }
}

</style>
