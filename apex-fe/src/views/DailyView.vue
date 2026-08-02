<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { journalFromAction, latestJournal, listDaily, runDaily } from '../api/daily'
import { getTradingCalendar } from '../api/market'
import { getAccount, placeOrder } from '../api/paper'

const router = useRouter()

const loading = ref(false)
const date = ref(new Date().toISOString().slice(0, 10))
const rows = ref([])
const journals = ref([])
const calendar = ref(null)
let dateBootstrapped = false

async function load() {
  loading.value = true
  try {
    const calRes = await getTradingCalendar(date.value, 8)
    calendar.value = calRes.data
    if (!dateBootstrapped && calendar.value && !calendar.value.tradingDay && calendar.value.latestTradingDay) {
      date.value = calendar.value.latestTradingDay
      dateBootstrapped = true
    }
    const [daily, journal] = await Promise.all([listDaily(date.value), latestJournal(30)])
    rows.value = daily.data || []
    journals.value = journal.data || []
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function onRun() {
  loading.value = true
  try {
    const res = await runDaily(date.value)
    rows.value = res.data || []
    ElMessage.success(`生成 ${rows.value.length} 条清单`)
  } catch (e) {
    ElMessage.error(e.message || '生成失败')
  } finally {
    loading.value = false
  }
}

async function fillJournal(row) {
  try {
    const { value } = await ElMessageBox.prompt('输入成交数量(股)', `${row.code} ${row.action}`, {
      inputValue: '100',
      confirmButtonText: '录入',
    })
    const quantity = Number(value)
    const priceRes = await ElMessageBox.prompt('输入成交价格', '成交价', {
      inputValue: '10',
      confirmButtonText: '确认',
    })
    const price = Number(priceRes.value)
    await journalFromAction(row.id, price, quantity)
    ElMessage.success('已写入 journal')
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '录入失败')
  }
}

async function paperFromAction(row) {
  if (row.action === 'HOLD') return
  try {
    const { value } = await ElMessageBox.prompt('模拟下单数量(股)', `${row.code} ${row.action}`, {
      inputValue: '100',
      confirmButtonText: '模拟成交',
    })
    const quantity = Number(value)
    const acc = await getAccount()
    await placeOrder({
      accountId: acc.data.id,
      code: row.code,
      side: row.action === 'SELL' ? 'SELL' : 'BUY',
      quantity,
    })
    ElMessage.success('已模拟成交')
    router.push('/paper')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '下单失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="header">
      <div>
        <p class="eyebrow">Apex · Daily</p>
        <h1>日终清单</h1>
        <p>
          建议买卖 + 持有离场条件 · 可一键录入真实成交
          <template v-if="calendar">
            · {{ calendar.date }}
            {{ calendar.tradingDay ? '交易日' : '非交易日' }}
            · 最近交易日 {{ calendar.latestTradingDay }}
          </template>
        </p>
      </div>
      <div class="actions">
        <el-input v-model="date" style="width: 140px" />
        <el-button type="primary" :loading="loading" @click="onRun">生成清单</el-button>
        <el-button plain @click="router.push('/decision')">智能决策</el-button>
        <el-button text :loading="loading" @click="load">刷新</el-button>
      </div>
    </header>

    <div v-if="!loading && !rows.length" class="page-empty">
      <h3>今日尚无日终清单</h3>
      <p>生成后可模拟下单或录入真实成交；也可先看智能决策清单</p>
      <el-button type="primary" :loading="loading" @click="onRun">生成清单</el-button>
      <el-button plain @click="router.push('/decision')">去智能决策</el-button>
    </div>
    <el-table v-else v-loading="loading" :data="rows" height="360" stripe class="apex-table">
      <el-table-column prop="action" label="动作" width="80">
        <template #default="{ row }">
          <span :class="{ up: row.action === 'BUY', down: row.action === 'SELL' }">{{ row.action }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="code" label="代码" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" width="120" />
      <el-table-column prop="strategyId" label="策略" width="80" />
      <el-table-column label="评分" width="100">
        <template #default="{ row }">
          <ScoreBar :score="row.score" />
        </template>
      </el-table-column>
      <el-table-column prop="suggestedWeight" label="建议仓位" width="100" />
      <el-table-column prop="exitRule" label="离场条件" min-width="160" />
      <el-table-column prop="reason" label="理由" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.action !== 'HOLD'"
            link
            type="primary"
            @click="paperFromAction(row)"
          >模拟下单</el-button>
          <el-button
            v-if="row.action !== 'HOLD'"
            link
            @click="fillJournal(row)"
          >录入成交</el-button>
        </template>
      </el-table-column>
    </el-table>

    <h3>最近人工成交</h3>
    <div v-if="!journals.length" class="page-empty" style="padding: 20px">
      <h3>尚无成交记录</h3>
      <p>从日终清单或决策页录入真实成交后，会出现在这里</p>
    </div>
    <el-table v-else :data="journals" size="small" stripe>
      <el-table-column prop="tradeDate" label="日期" width="120" />
      <el-table-column prop="code" label="代码" width="100" />
      <el-table-column prop="side" label="方向" width="80" />
      <el-table-column prop="price" label="价格" width="100" />
      <el-table-column prop="quantity" label="数量" width="90" />
      <el-table-column prop="relatedActionId" label="清单ID" width="90" />
      <el-table-column prop="note" label="备注" min-width="160" />
    </el-table>
  </div>
</template>

<style scoped>
/* 共用样式见 style.css */
</style>
