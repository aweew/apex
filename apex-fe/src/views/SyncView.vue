<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchSyncJob,
  fetchSyncOverview,
  startSyncJob,
  stopSyncJob,
} from '../api/sync'

const router = useRouter()
const loading = ref(false)
const overview = ref(null)
const activeJob = ref(null)
const pollTimer = ref(null)

const startForm = ref({
  taskType: '',
  limit: undefined,
  start: '',
  sources: '',
  types: '',
  codes: '',
  sleep: undefined,
  mode: '',
})

const tasks = computed(() => overview.value?.tasks || [])
const recentJobs = computed(() => overview.value?.recentJobs || [])
const groups = computed(() => {
  const map = new Map()
  for (const t of tasks.value) {
    const g = t.groupName || '其它'
    if (!map.has(g)) map.set(g, [])
    map.get(g).push(t)
  }
  return [...map.entries()]
})

const runningJobs = computed(() =>
  recentJobs.value.filter((j) => j.status === 'RUNNING' || j.status === 'PENDING'),
)

function statusType(s) {
  if (s === 'SUCCESS') return 'success'
  if (s === 'FAILED') return 'danger'
  if (s === 'CANCELLED') return 'info'
  if (s === 'RUNNING' || s === 'PENDING') return 'warning'
  return ''
}

function healthClass(level) {
  if (level === 'GREEN') return 'health-green'
  if (level === 'YELLOW') return 'health-yellow'
  if (level === 'RED') return 'health-red'
  if (level === 'RUNNING') return 'health-yellow'
  return 'health-unknown'
}

function healthLabel(level) {
  if (level === 'GREEN') return '正常'
  if (level === 'YELLOW') return '预警'
  if (level === 'RED') return '异常'
  if (level === 'RUNNING') return '运行中'
  return level || '未知'
}

function statusLabel(s) {
  if (s === 'SUCCESS') return '成功'
  if (s === 'FAILED') return '失败'
  if (s === 'PENDING') return '等待'
  if (s === 'CANCELLED') return '已取消'
  if (s === 'RUNNING') return '运行中'
  return s || '-'
}

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

function defaultLimit(taskType) {
  if (taskType === 'A_SHARE_BARS') return 20
  if (taskType === 'FUNDAMENTALS') return 20
  if (taskType === 'COMPANY_PROFILE') return 50
  if (taskType === 'TURNOVER') return 50
  if (taskType === 'SECTOR_CONS') return 10
  if (taskType === 'HOT' || taskType === 'NEWS') return 50
  return undefined
}

async function load() {
  loading.value = true
  try {
    const res = await fetchSyncOverview()
    overview.value = res.data
    const running = (res.data?.recentJobs || []).find((j) => j.status === 'RUNNING')
    if (running) {
      activeJob.value = running
      ensurePoll()
    } else if (!runningJobs.value.length) {
      stopPoll()
    }
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function ensurePoll() {
  if (pollTimer.value) return
  pollTimer.value = setInterval(async () => {
    try {
      const res = await fetchSyncOverview()
      overview.value = res.data
      const running = (res.data?.recentJobs || []).find(
        (j) => j.status === 'RUNNING' || j.status === 'PENDING',
      )
      if (running) {
        const detail = await fetchSyncJob(running.id)
        activeJob.value = detail.data
      } else {
        activeJob.value = res.data?.recentJobs?.[0] || null
        stopPoll()
      }
    } catch {
      // 轮询失败不打断
    }
  }, 2000)
}

function stopPoll() {
  if (pollTimer.value) {
    clearInterval(pollTimer.value)
    pollTimer.value = null
  }
}

async function onStart(task) {
  const limit = defaultLimit(task.taskType)
  const tip = limit
    ? `将启动「${task.name}」（默认 limit=${limit}，可在下方高级参数改）。长任务可随时停止。`
    : `将启动「${task.name}」。${task.defaultParamsHint || ''}`
  try {
    await ElMessageBox.confirm(tip, '启动同步', { type: 'info', confirmButtonText: '启动' })
  } catch {
    return
  }
  try {
    const body = { taskType: task.taskType }
    if (limit) body.limit = limit
    if (task.taskType === 'HOT') body.sources = 'eastmoney,baidu'
    if (task.taskType === 'SECTOR_QUOTE') body.types = 'INDUSTRY,CONCEPT,THEME'
    if (task.taskType === 'A_SHARE_BARS') body.start = '20240101'
    const res = await startSyncJob(body)
    activeJob.value = res.data
    ElMessage.success(`已启动 #${res.data.id}`)
    ensurePoll()
    await load()
  } catch (e) {
    ElMessage.error(e.message || '启动失败')
  }
}

async function onStartCustom() {
  if (!startForm.value.taskType) {
    ElMessage.warning('请选择任务类型')
    return
  }
  const body = { taskType: startForm.value.taskType }
  if (startForm.value.limit) body.limit = Number(startForm.value.limit)
  if (startForm.value.start) body.start = startForm.value.start
  if (startForm.value.sources) body.sources = startForm.value.sources
  if (startForm.value.types) body.types = startForm.value.types
  if (startForm.value.codes) body.codes = startForm.value.codes
  if (startForm.value.sleep) body.sleep = Number(startForm.value.sleep)
  if (startForm.value.mode) body.mode = startForm.value.mode
  try {
    const res = await startSyncJob(body)
    activeJob.value = res.data
    ElMessage.success(`已启动 #${res.data.id}`)
    ensurePoll()
    await load()
  } catch (e) {
    ElMessage.error(e.message || '启动失败')
  }
}

async function onStop(job) {
  if (!job?.id) return
  try {
    await ElMessageBox.confirm(`确认停止任务 #${job.id}「${job.taskName}」？`, '停止同步', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    const res = await stopSyncJob(job.id)
    activeJob.value = res.data
    ElMessage.success('已发送停止')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '停止失败')
  }
}

async function selectJob(job) {
  try {
    const res = await fetchSyncJob(job.id)
    activeJob.value = res.data
    if (res.data.status === 'RUNNING' || res.data.status === 'PENDING') ensurePoll()
  } catch (e) {
    ElMessage.error(e.message || '加载任务失败')
  }
}

onMounted(load)
onUnmounted(stopPoll)
</script>

<template>
  <div class="page" v-loading="loading">
    <header class="header">
      <div>
        <p class="eyebrow">Apex · Sync</p>
        <h1>数据同步</h1>
        <p>
          {{ overview?.message || '统一管理行情 / 板块 / 热点 / 资讯 / 基本面等同步任务，可看进度、可启停' }}
        </p>
      </div>
      <div class="actions">
        <el-tag v-if="runningJobs.length" type="warning">运行中 {{ runningJobs.length }}</el-tag>
        <el-button plain @click="router.push('/pipeline')">流水线</el-button>
        <el-button text @click="load">刷新状态</el-button>
      </div>
    </header>

    <div class="layout">
      <section class="tasks-panel">
        <div v-for="[group, list] in groups" :key="group" class="group">
          <h3>{{ group }}</h3>
          <div class="task-grid">
            <div
              v-for="task in list"
              :key="task.taskType"
              class="task-card"
              :class="{ running: task.running }"
            >
              <div class="task-top">
                <strong>{{ task.name }}</strong>
                <el-tag v-if="task.running" size="small" type="warning" effect="dark">运行中</el-tag>
                <el-tag
                  v-else-if="task.latestJob"
                  size="small"
                  :type="statusType(task.latestJob.status)"
                >
                  {{ statusLabel(task.latestJob.status) }}
                </el-tag>
              </div>
              <p class="desc">{{ task.description }}</p>
              <p class="hint">{{ task.defaultParamsHint }}</p>
              <div class="task-health">
                <span class="health-dot" :class="healthClass(task.healthLevel)" />
                <span :class="healthClass(task.healthLevel)">{{ healthLabel(task.healthLevel) }}</span>
                <span class="health-time">最近成功 {{ fmtTime(task.lastSuccessAt) }}</span>
              </div>
              <div class="task-actions">
                <el-button
                  type="primary"
                  size="small"
                  :disabled="task.running"
                  @click="onStart(task)"
                >
                  启动
                </el-button>
                <el-button
                  v-if="task.running && task.latestJob"
                  type="danger"
                  size="small"
                  plain
                  @click="onStop(task.latestJob)"
                >
                  停止
                </el-button>
                <el-button
                  v-if="task.latestJob"
                  size="small"
                  link
                  @click="selectJob(task.latestJob)"
                >
                  看日志
                </el-button>
              </div>
              <el-progress
                v-if="task.running && task.latestJob"
                :percentage="Number(task.latestJob.progressPct || 0)"
                :stroke-width="8"
                style="margin-top: 8px"
              />
            </div>
          </div>
        </div>

        <div class="custom-box">
          <h3>高级启动</h3>
          <div class="custom-form">
            <el-select v-model="startForm.taskType" placeholder="任务类型" style="width: 200px">
              <el-option
                v-for="t in tasks"
                :key="t.taskType"
                :label="`${t.name} (${t.taskType})`"
                :value="t.taskType"
              />
            </el-select>
            <el-input v-model="startForm.start" placeholder="start=20240101" style="width: 140px" />
            <el-input v-model="startForm.limit" placeholder="limit" style="width: 90px" />
            <el-input v-model="startForm.sources" placeholder="sources" style="width: 180px" />
            <el-input v-model="startForm.types" placeholder="types" style="width: 220px" />
            <el-input v-model="startForm.codes" placeholder="codes" style="width: 160px" />
            <el-button type="primary" @click="onStartCustom">启动</el-button>
          </div>
        </div>
      </section>

      <aside class="side">
        <div class="panel">
          <div class="panel-head">
            <h3>当前任务</h3>
            <el-button
              v-if="activeJob && (activeJob.status === 'RUNNING' || activeJob.status === 'PENDING')"
              type="danger"
              size="small"
              @click="onStop(activeJob)"
            >
              停止
            </el-button>
          </div>
          <template v-if="activeJob">
            <div class="meta">
              <div><label>任务</label><b>#{{ activeJob.id }} {{ activeJob.taskName }}</b></div>
              <div>
                <label>状态</label>
                <el-tag size="small" :type="statusType(activeJob.status)">{{ statusLabel(activeJob.status) }}</el-tag>
              </div>
              <div><label>进度</label>{{ activeJob.progressPct ?? 0 }}%</div>
              <div v-if="activeJob.doneItems != null">
                <label>条目</label>{{ activeJob.doneItems }} / {{ activeJob.totalItems ?? '?' }}
              </div>
              <div><label>开始</label>{{ fmtTime(activeJob.startedAt) }}</div>
              <div><label>结束</label>{{ fmtTime(activeJob.finishedAt) }}</div>
              <div><label>说明</label>{{ activeJob.message || '-' }}</div>
            </div>
            <el-progress :percentage="Number(activeJob.progressPct || 0)" :stroke-width="10" />
            <pre class="log">{{ activeJob.logTail || '暂无日志' }}</pre>
          </template>
          <el-empty v-else description="选择任务或启动同步后在此查看进度" :image-size="64" />
        </div>

        <div class="panel">
          <h3>最近运行</h3>
          <el-table :data="recentJobs" size="small" max-height="280" @row-click="selectJob">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="taskName" label="任务" min-width="100" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="progressPct" label="%" width="50" />
            <el-table-column label="开始" width="140">
              <template #default="{ row }">{{ fmtTime(row.startedAt) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(320px, 0.9fr);
  gap: 16px;
  align-items: start;
}

.group {
  margin-bottom: 18px;
}

.group h3,
.panel h3,
.custom-box h3 {
  margin: 0 0 10px;
  font-size: 15px;
}

.task-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 10px;
}

.task-card,
.panel,
.custom-box {
  background: var(--glass);
  backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  -webkit-backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  padding: 12px 14px;
  box-shadow: var(--shadow-soft);
}

.task-card.running {
  border-color: rgba(255, 159, 10, 0.45);
  box-shadow: 0 0 0 1px rgba(255, 159, 10, 0.18), var(--shadow-soft);
  background: rgba(255, 159, 10, 0.08);
}

.task-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.desc {
  margin: 0;
  font-size: 12px;
  color: var(--text);
  line-height: 1.4;
}

.hint {
  margin: 6px 0 10px;
  font-size: 11px;
  color: var(--muted);
}

.task-health {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 11px;
}

.health-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.health-dot.health-green {
  background: #34c759;
}

.health-dot.health-yellow {
  background: #e6a23c;
}

.health-dot.health-red {
  background: #ff3b30;
}

.health-dot.health-unknown {
  background: #c7c7cc;
}

span.health-green {
  color: #248a3d;
  font-weight: 600;
}

span.health-yellow {
  color: #b8860b;
  font-weight: 600;
}

span.health-red {
  color: #d70015;
  font-weight: 600;
}

span.health-unknown {
  color: var(--muted);
}

.health-time {
  color: var(--muted);
  margin-left: auto;
}

.task-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.custom-form {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.side {
  display: flex;
  flex-direction: column;
  gap: 12px;
  position: sticky;
  top: 12px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.panel-head h3 {
  margin: 0;
}

.meta {
  display: grid;
  gap: 6px;
  margin-bottom: 10px;
  font-size: 13px;
}

.meta label {
  display: inline-block;
  width: 48px;
  color: var(--muted);
  font-size: 11px;
}

.log {
  margin-top: 10px;
  max-height: 320px;
  overflow: auto;
  padding: 10px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.35);
  color: #d7e2f0;
  font-size: 11px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-all;
}

@media (max-width: 1100px) {
  .layout {
    grid-template-columns: 1fr;
  }

  .side {
    position: static;
  }
}
</style>
