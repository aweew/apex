<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  applyRiskPreset,
  listConfig,
  listRiskRules,
  localLogin,
  updateConfig,
  updateRiskRule,
} from '../api/dashboard'
import http from '../api/http'
import { buildApiUrl } from '../api/baseUrl'

const router = useRouter()
const loading = ref(false)
const rows = ref([])
const riskRules = ref([])
const quality = ref(null)
const loginForm = ref({ username: 'admin', password: 'admin123' })
const token = ref(localStorage.getItem('satoken') || '')

function slaLevelLabel(level) {
  if (level === 'GREEN') return '正常'
  if (level === 'YELLOW') return '预警'
  if (level === 'RED') return '异常'
  return level || '-'
}

const autoSync = computed(() => rows.value.find((r) => r.configKey === 'auto_sync_enabled'))
const autoGroup = computed(() => rows.value.find((r) => r.configKey === 'auto_sync_group'))

/** 决策/策略参数置顶，方便终端调参 */
const sortedRows = computed(() => {
  const rank = (key) => {
    const k = String(key || '')
    if (k.startsWith('decision.')) return 0
    if (k.startsWith('strategy.')) return 1
    if (k.startsWith('auto_sync')) return 2
    return 3
  }
  return [...rows.value].sort((a, b) => {
    const ra = rank(a.configKey)
    const rb = rank(b.configKey)
    if (ra !== rb) return ra - rb
    return String(a.configKey || '').localeCompare(String(b.configKey || ''))
  })
})

async function load() {
  loading.value = true
  try {
    const [res, q, rr] = await Promise.all([
      listConfig(),
      http.get('/api/data/quality', { params: { groupName: '我的自选' } }),
      listRiskRules(),
    ])
    rows.value = res.data || []
    quality.value = q.data
    riskRules.value = rr.data || []
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function ensureAutoSyncKeys() {
  const keys = [
    { configKey: 'auto_sync_enabled', configValue: 'false' },
    { configKey: 'auto_sync_group', configValue: '我的自选' },
    { configKey: 'strategy.s1.fast_ma', configValue: '20' },
    { configKey: 'strategy.s1.slow_ma', configValue: '60' },
    { configKey: 'strategy.s1.vol_ma', configValue: '20' },
    { configKey: 'strategy.s2.ma', configValue: '60' },
    { configKey: 'strategy.s2.rsi_period', configValue: '14' },
    { configKey: 'strategy.s2.rsi_oversold', configValue: '30' },
    { configKey: 'strategy.s2.rsi_rebound', configValue: '35' },
    { configKey: 'strategy.s2.rsi_overbought', configValue: '70' },
    { configKey: 'strategy.s3.lookback', configValue: '20' },
    { configKey: 'strategy.s3.volume_ratio', configValue: '1.5' },
    { configKey: 'decision.score.confluence', configValue: '12' },
    { configKey: 'decision.score.hot', configValue: '8' },
    { configKey: 'decision.score.hot_triple', configValue: '4' },
    { configKey: 'decision.score.mainline', configValue: '10' },
    { configKey: 'decision.score.off_mainline', configValue: '5' },
    { configKey: 'decision.score.fund_penalty', configValue: '8' },
    { configKey: 'decision.score.defense', configValue: '6' },
    { configKey: 'decision.score.offense', configValue: '3' },
    { configKey: 'decision.link.undervalued_s2', configValue: '6' },
    { configKey: 'decision.link.overvalued_s3', configValue: '8' },
    { configKey: 'decision.executable.score', configValue: '88' },
    { configKey: 'decision.confluence.window', configValue: '5' },
    { configKey: 'decision.confluence.min_strategies', configValue: '2' },
    { configKey: 'decision.gate.minimum_breadth_up', configValue: '2000' },
    { configKey: 'decision.gate.minimum_hot_sources', configValue: '2' },
  ]
  for (const item of keys) {
    if (!rows.value.find((r) => r.configKey === item.configKey)) {
      await updateConfig(item)
    }
  }
  await load()
  ElMessage.success('已确保定时同步与策略参数配置项存在')
}

async function save(row) {
  try {
    await updateConfig({ configKey: row.configKey, configValue: row.configValue })
    ElMessage.success('已保存')
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

async function saveRisk(row) {
  try {
    await updateRiskRule({ ruleKey: row.ruleKey, ruleValue: row.ruleValue })
    ElMessage.success('风控已保存')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

async function onPreset(preset) {
  try {
    await applyRiskPreset(preset)
    ElMessage.success(`已应用${preset}预设`)
    await load()
  } catch (e) {
    ElMessage.error(e.message || '预设失败')
  }
}

async function onLogin() {
  try {
    const res = await localLogin(loginForm.value)
    token.value = res.data.accessToken
    localStorage.setItem('satoken', token.value)
    ElMessage.success('登录成功')
  } catch (e) {
    ElMessage.error(e.message || '登录失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="header">
      <div>
        <p class="eyebrow">Config</p>
        <h1>参数与登录</h1>
        <p>成本假设 / 撮合模式 · 本地单用户登录 · 定时同步 · 决策评分阈值</p>
      </div>
      <div class="actions">
        <el-button @click="ensureAutoSyncKeys">补齐同步/策略参数</el-button>
        <el-button plain @click="router.push('/pipeline')">流水线</el-button>
        <el-button plain @click="router.push('/decision')">决策</el-button>
        <el-button text @click="router.push('/dashboard')">看板</el-button>
      </div>
    </header>

    <el-alert
      v-if="quality"
      :title="`数据健康：行情 ${quality.quotedCount}/${quality.watchlistCount} · K线 ${quality.barsReadyCount} · 股票池 ${quality.universeCount} · ${quality.suggestion}`"
      type="success"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <el-table
      v-if="quality?.marketSources?.length"
      :data="quality.marketSources"
      size="small"
      style="margin-bottom: 12px"
    >
      <el-table-column prop="name" label="数据源" min-width="100" />
      <el-table-column label="等级" width="80">
        <template #default="{ row }">
          {{ slaLevelLabel(row.level) }}
        </template>
      </el-table-column>
      <el-table-column prop="dataAsOf" label="数据日" width="110" />
      <el-table-column prop="note" label="说明" min-width="160" show-overflow-tooltip />
    </el-table>
    <el-alert
      :title="`定时同步：${autoSync?.configValue === 'true' ? '已开启' : '关闭'} · 分组 ${autoGroup?.configValue || '我的自选'} · 工作日 16:10 行情 / 18:30 过期日线`"
      type="info"
      :closable="false"
      style="margin-bottom: 12px"
    />

    <el-form :inline="true" style="margin-bottom: 16px">
      <el-form-item label="用户"><el-input v-model="loginForm.username" style="width: 120px" /></el-form-item>
      <el-form-item label="密码"><el-input v-model="loginForm.password" type="password" style="width: 140px" /></el-form-item>
      <el-form-item><el-button type="primary" @click="onLogin">登录</el-button></el-form-item>
      <el-form-item v-if="token"><span class="token">token 已保存</span></el-form-item>
    </el-form>

    <h3>风控规则</h3>
    <div class="actions" style="margin-bottom: 10px">
      <el-button size="small" @click="onPreset('conservative')">保守</el-button>
      <el-button size="small" type="primary" @click="onPreset('balanced')">均衡</el-button>
      <el-button size="small" type="warning" @click="onPreset('aggressive')">激进</el-button>
    </div>
    <el-table v-loading="loading" :data="riskRules" size="small" style="margin-bottom: 18px">
      <el-table-column prop="ruleKey" label="键" width="200" />
      <el-table-column label="值" min-width="160">
        <template #default="{ row }">
          <el-input v-model="row.ruleValue" />
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="说明" min-width="160" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="saveRisk(row)">保存</el-button>
        </template>
      </el-table-column>
    </el-table>

    <h3>系统参数</h3>
    <el-table v-loading="loading" :data="sortedRows" stripe>
      <el-table-column prop="configKey" label="键" width="180" />
      <el-table-column label="值" min-width="180">
        <template #default="{ row }">
          <el-input v-model="row.configValue" />
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="说明" min-width="160" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="save(row)">保存</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-alert
      class="decision-hint"
      type="info"
      :closable="false"
      show-icon
      title="决策评分与估值联动参数"
      description="系统参数中含 decision.score.* / decision.link.* / decision.executable.score / decision.gate.*：低估+S2 提权，高估+S3 降权；市场广度与主线/热点确认未通过时仅观察。改完后重新「一键生成决策」生效。"
      style="margin: 16px 0 12px"
    />

    <div class="exports">
      <a :href="buildApiUrl('/api/export/decision')" target="_blank">导出决策 CSV</a>
      <a :href="buildApiUrl('/api/export/observe')" target="_blank">导出观察池 CSV</a>
      <a :href="buildApiUrl('/api/export/signals')" target="_blank">导出信号 CSV</a>
      <a :href="buildApiUrl('/api/export/journal')" target="_blank">导出 journal CSV</a>
      <a :href="buildApiUrl('/api/export/paper/orders')" target="_blank">导出模拟盘订单</a>
      <a :href="buildApiUrl('/api/export/universe')" target="_blank">导出股票池 CSV</a>
      <a :href="buildApiUrl('/api/export/watchlist')" target="_blank">导出自选 CSV</a>
      <span>回测导出：/api/export/backtest/{jobId}</span>
    </div>
  </div>
</template>

<style scoped>
.exports {
  margin-top: 18px;
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  padding: 14px 16px;
  background: var(--glass);
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.exports a,
.token {
  color: var(--jade);
  font-size: 13px;
  font-weight: 500;
}
</style>
