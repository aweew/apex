<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fillWatchlistBars } from '../api/bars'
import { runPipeline } from '../api/pipeline'
import { latestUniverse } from '../api/signal'

const router = useRouter()
const loading = ref(false)
const filling = ref(false)
const groupName = ref('我的自选')
const form = ref({
  refreshQuotes: true,
  syncStaleBars: true,
  refreshUniverse: true,
  runSignals: true,
  runDaily: true,
})
const result = ref(null)
const fillResult = ref(null)
const universe = ref([])

async function loadUniverse() {
  try {
    const res = await latestUniverse()
    universe.value = res.data || []
  } catch {
    universe.value = []
  }
}

async function onRun() {
  loading.value = true
  try {
    const res = await runPipeline({
      groupName: groupName.value,
      ...form.value,
    })
    result.value = res.data
    ElMessage.success((res.data.steps || []).join(' → '))
    await loadUniverse()
  } catch (e) {
    ElMessage.error(e.message || '流水线失败')
  } finally {
    loading.value = false
  }
}

async function onFillBars() {
  filling.value = true
  try {
    const res = await fillWatchlistBars(groupName.value, 3, 40)
    fillResult.value = res.data
    ElMessage.success(
      `补齐 ${res.data.rounds} 轮 · 成功 ${res.data.totalSuccess} · K线 ${res.data.totalBars} · ${res.data.message}`,
    )
  } catch (e) {
    ElMessage.error(e.message || '补齐失败')
  } finally {
    filling.value = false
  }
}

onMounted(loadUniverse)
</script>

<template>
  <div class="page">
    <header class="header">
      <div>
        <h1>研究流水线</h1>
        <p>对齐市面量化终端：一键完成 行情 → 日线 → 股票池 → 信号 → 日终清单</p>
      </div>
      <div class="header-actions">
        <el-button :loading="filling" @click="onFillBars">多轮补齐K线</el-button>
        <el-button type="primary" size="large" :loading="loading" @click="onRun">一键运行</el-button>
      </div>
    </header>

    <el-form label-width="120px" class="form">
      <el-form-item label="自选分组">
        <el-input v-model="groupName" style="width: 200px" />
      </el-form-item>
      <el-form-item label="步骤">
        <el-checkbox v-model="form.refreshQuotes">刷新行情</el-checkbox>
        <el-checkbox v-model="form.syncStaleBars">同步过期日线</el-checkbox>
        <el-checkbox v-model="form.refreshUniverse">刷新股票池</el-checkbox>
        <el-checkbox v-model="form.runSignals">运行信号</el-checkbox>
        <el-checkbox v-model="form.runDaily">生成日终清单</el-checkbox>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="fillResult"
      :title="`K线补齐：${fillResult.rounds}轮 · 成功${fillResult.totalSuccess} · 失败${fillResult.totalFail} · 写入${fillResult.totalBars} · ${fillResult.message}`"
      type="info"
      :closable="false"
      style="margin-bottom: 12px"
    />

    <el-alert
      v-if="result"
      :title="(result.steps || []).join(' → ')"
      type="success"
      :closable="false"
      style="margin-bottom: 12px"
    />

    <div class="actions" v-if="result">
      <el-button @click="router.push('/signals')">看信号</el-button>
      <el-button @click="router.push('/daily')">看日终</el-button>
      <el-button @click="router.push('/watchlist')">看自选</el-button>
      <el-button @click="router.push('/dashboard')">看板</el-button>
    </div>

    <h3>当前股票池（质量过滤后） {{ universe.length }}</h3>
    <el-table :data="universe" height="420" size="small">
      <el-table-column prop="code" label="代码" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" width="120" />
      <el-table-column prop="reasonTags" label="标签/评分" min-width="280" show-overflow-tooltip />
      <el-table-column prop="batchNo" label="批次" width="150" />
    </el-table>
  </div>
</template>

<style scoped>
.actions {
  margin-bottom: 12px;
}
</style>
