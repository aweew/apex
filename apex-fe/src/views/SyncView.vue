<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCurrentUser } from '../api/auth'
import {
  fetchSyncJob,
  fetchSyncOverview,
  startSyncJob,
  stopSyncJob,
} from '../api/sync'
import {
  createLatestLoader,
  createSerialPoller,
  findRunningSyncJob,
  shouldSwitchToRunningJob,
} from './syncPolling.mjs'

const router = useRouter()
const currentUser = getCurrentUser()
const isAdmin = computed(() => currentUser?.role === 'ADMIN')
const loading = ref(false)
const overview = ref(null)
const activeJob = ref(null)
const detailPanelRef = ref(null)
const detailLoadingJobId = ref(null)
/** 用户当前盯着的任务，避免轮询用列表摘要冲掉详情日志 */
const pinnedJobId = ref(null)
let detailRequestId = 0

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
const closeBundleTask = computed(() => tasks.value.find((t) => t.taskType === 'CLOSE_BUNDLE') || null)
const groups = computed(() => {
  const map = new Map()
  for (const t of tasks.value) {
    // 顶部已有一键入口，列表里仍保留卡片便于看健康与日志
    const g = t.groupName || '其它'
    if (!map.has(g)) map.set(g, [])
    map.get(g).push(t)
  }
  return [...map.entries()]
})

const runningCount = computed(
  () => overview.value?.runningCount ?? tasks.value.filter((task) => task.running).length,
)
const loadLatestOverview = createLatestLoader(fetchSyncOverview)
const poller = createSerialPoller(pollSyncStatus)

/** 详情日志；库内为空时用 message/exit/params 兜底，避免「闪一下暂无」 */
const activeLogText = computed(() => {
  const job = activeJob.value
  if (!job) return ''
  if (job.logTail) return job.logTail
  const lines = []
  if (job.message) lines.push(`[message] ${job.message}`)
  if (job.exitCode != null && job.exitCode !== '') lines.push(`[exit] ${job.exitCode}`)
  if (job.paramsJson) lines.push(`[params] ${job.paramsJson}`)
  if (job.status) lines.push(`[status] ${job.status}`)
  return lines.join('\n')
})

function statusType(s) {
  if (s === 'SUCCESS') return 'success'
  if (s === 'PARTIAL') return 'warning'
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
  if (level === 'GREEN') return '数据正常'
  if (level === 'YELLOW') return '数据待更新'
  if (level === 'RED') return '数据异常'
  if (level === 'RUNNING') return '运行中'
  return level || '未知'
}

function statusLabel(s) {
  if (s === 'SUCCESS') return '成功'
  if (s === 'PARTIAL') return '部分完成'
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
    const snapshot = await refreshOverview()
    if (!snapshot) return
    const running = findRunningSyncJob(snapshot)
    if (running) {
      if (shouldSwitchToRunningJob(activeJob.value, pinnedJobId.value)) {
        pinnedJobId.value = running.id
      }
      await refreshPinnedJob()
      ensurePoll()
    } else {
      stopPoll()
      if (pinnedJobId.value) {
        await refreshPinnedJob()
      }
    }
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function refreshOverview() {
  const response = await loadLatestOverview()
  if (!response) return null
  overview.value = response.data
  return response.data
}

/** 按钉住的 jobId 拉完整详情（含 logTail） */
async function refreshPinnedJob() {
  if (!pinnedJobId.value) return
  const jobId = pinnedJobId.value
  const requestId = ++detailRequestId
  try {
    const detail = await fetchSyncJob(jobId)
    if (requestId !== detailRequestId || pinnedJobId.value !== jobId) return
    activeJob.value = detail.data
  } catch {
    // 详情失败时保留现有面板
  }
}

async function pollSyncStatus() {
  try {
    const snapshot = await refreshOverview()
    if (!snapshot) return true
    const running = findRunningSyncJob(snapshot)
    if (running) {
      if (shouldSwitchToRunningJob(activeJob.value, pinnedJobId.value)) {
        pinnedJobId.value = running.id
      }
      await refreshPinnedJob()
      return true
    }

    if (pinnedJobId.value) {
      await refreshPinnedJob()
    } else if (snapshot.recentJobs?.[0]?.id) {
      pinnedJobId.value = snapshot.recentJobs[0].id
      await refreshPinnedJob()
    }
    return false
  } catch {
    return true
  }
}

function ensurePoll() {
  poller.start()
}

function stopPoll() {
  poller.stop()
}

async function onStart(task) {
  if (!isAdmin.value) {
    ElMessage.warning('共享同步任务由管理员或系统调度启动')
    return
  }
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
    if (task.taskType === 'CLOSE_BUNDLE') body.types = 'INDUSTRY,CONCEPT,THEME'
    const res = await startSyncJob(body)
    pinnedJobId.value = res.data.id
    activeJob.value = res.data
    ElMessage.success(`已启动 #${res.data.id}`)
    ensurePoll()
    await load()
  } catch (e) {
    ElMessage.error(e.message || '启动失败')
  }
}

/** 收盘后一键：指数→板块→涨停→热点→资讯 */
async function onCloseBundle() {
  if (!isAdmin.value) {
    ElMessage.warning('共享同步任务由管理员启动')
    return
  }
  const task = closeBundleTask.value
  if (!task) {
    ElMessage.warning('未注册一键收盘同步任务，请重启后端')
    return
  }
  if (task.running) {
    ElMessage.info('收盘同步正在运行，请稍候')
    if (task.latestJob) selectJob(task.latestJob)
    return
  }
  try {
    await ElMessageBox.confirm(
      '将顺序同步：大盘指数、板块行情、涨停池、热点、资讯。\n适合收盘后一次跑完（不含全A日线，过重请单独启动）。',
      '一键收盘同步',
      { type: 'info', confirmButtonText: '开始同步' },
    )
  } catch {
    return
  }
  try {
    const res = await startSyncJob({
      taskType: 'CLOSE_BUNDLE',
      types: 'INDUSTRY,CONCEPT,THEME',
    })
    pinnedJobId.value = res.data.id
    activeJob.value = res.data
    ElMessage.success(`已启动一键收盘同步 #${res.data.id}`)
    ensurePoll()
    await load()
  } catch (e) {
    ElMessage.error(e.message || '启动失败')
  }
}

async function onStartCustom() {
  if (!isAdmin.value) {
    ElMessage.warning('共享同步任务由管理员启动')
    return
  }
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
    pinnedJobId.value = res.data.id
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
    pinnedJobId.value = res.data.id
    activeJob.value = res.data
    ElMessage.success('已发送停止')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '停止失败')
  }
}

async function selectJob(job) {
  if (!job?.id) return
  pinnedJobId.value = job.id
  detailLoadingJobId.value = job.id
  try {
    const res = await fetchSyncJob(job.id)
    activeJob.value = res.data
    if (res.data.status === 'RUNNING' || res.data.status === 'PENDING') ensurePoll()
    await nextTick()
    detailPanelRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (e) {
    ElMessage.error(e.message || '加载任务失败')
  } finally {
    if (detailLoadingJobId.value === job.id) detailLoadingJobId.value = null
  }
}

onMounted(load)
onUnmounted(stopPoll)
</script>

<template>
  <div class="page" v-loading="loading">
    <header class="header">
      <div>
        <p class="eyebrow">Sync</p>
        <h1>数据同步</h1>
        <p>
          {{ isAdmin
            ? (overview?.message || '统一管理数据同步与智能决策任务，可看进度、可启停')
            : '查看系统共享同步与智能决策的进度和运行日志' }}
        </p>
      </div>
      <div class="actions">
        <el-tag v-if="runningCount" type="warning">运行中 {{ runningCount }}</el-tag>
        <el-button
          v-if="isAdmin"
          type="primary"
          :disabled="!!closeBundleTask?.running"
          :loading="!!closeBundleTask?.running"
          @click="onCloseBundle"
        >
          一键收盘同步
        </el-button>
        <el-button plain @click="router.push('/pipeline')">流水线</el-button>
        <el-button text @click="load">刷新状态</el-button>
      </div>
    </header>

    <section class="close-hero" v-if="closeBundleTask && isAdmin">
      <div class="close-copy">
        <h2>收盘后点一次就够</h2>
        <p>
          自动串行：大盘指数 → 板块行情 → 涨停池 → 热点 → 资讯。
          最近成功 {{ fmtTime(closeBundleTask.lastSuccessAt) }} ·
          <span :class="healthClass(closeBundleTask.healthLevel)">{{ healthLabel(closeBundleTask.healthLevel) }}</span>
        </p>
      </div>
      <div class="close-actions">
        <el-button
          type="primary"
          size="large"
          :disabled="!!closeBundleTask.running"
          :loading="!!closeBundleTask.running"
          @click="onCloseBundle"
        >
          {{ closeBundleTask.running ? '同步中…' : '一键收盘同步' }}
        </el-button>
        <el-button
          v-if="closeBundleTask.latestJob"
          size="large"
          plain
          @click="selectJob(closeBundleTask.latestJob)"
        >
          看进度/日志
        </el-button>
      </div>
    </section>

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
                  上次{{ statusLabel(task.latestJob.status) }}
                </el-tag>
              </div>
              <p class="desc">{{ task.description }}</p>
              <p class="hint">{{ task.defaultParamsHint }}</p>
              <div class="task-health">
                <span class="health-dot" :class="healthClass(task.healthLevel)" />
                <span class="health-label" :class="healthClass(task.healthLevel)">{{ healthLabel(task.healthLevel) }}</span>
                <span
                  class="health-time"
                  :title="`最近完整成功 ${fmtTime(task.lastSuccessAt)}`"
                >
                  最近完整成功 {{ fmtTime(task.lastSuccessAt) }}
                </span>
              </div>
              <div class="task-actions">
                <el-button
                  v-if="isAdmin"
                  type="primary"
                  size="small"
                  :disabled="task.running"
                  @click="onStart(task)"
                >
                  启动
                </el-button>
                <span v-if="task.taskType === 'DECISION' && !isAdmin" class="system-managed">
                  系统共享生成
                </span>
                <el-button
                  v-if="isAdmin && task.running && task.latestJob && task.taskType !== 'DECISION'"
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
                  type="primary"
                  plain
                  :loading="detailLoadingJobId === task.latestJob.id"
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

        <div v-if="isAdmin" class="custom-box">
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
        <div ref="detailPanelRef" class="panel" v-loading="detailLoadingJobId != null">
          <div class="panel-head">
            <h3>当前任务</h3>
            <el-button
              v-if="isAdmin && activeJob && activeJob.taskType !== 'DECISION' && (activeJob.status === 'RUNNING' || activeJob.status === 'PENDING')"
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
            <pre class="log">{{ activeLogText || '暂无日志' }}</pre>
          </template>
          <el-empty
            v-else
            :description="isAdmin ? '选择任务或启动同步后在此查看进度' : '选择任务后在此查看进度'"
            :image-size="64"
          />
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
              <template #default="{ row }">
                <time v-if="row.startedAt" class="job-start-time">
                  <span>{{ fmtTime(row.startedAt).slice(0, 10) }}</span>
                  <span>{{ fmtTime(row.startedAt).slice(11) }}</span>
                </time>
                <span v-else>-</span>
              </template>
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

.close-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin: 0 0 16px;
  padding: 16px 18px;
  border-radius: var(--radius);
  border: 1px solid rgba(10, 132, 255, 0.28);
  background: linear-gradient(120deg, rgba(10, 132, 255, 0.12), rgba(52, 199, 89, 0.08));
  box-shadow: var(--shadow-soft);
}

.close-copy h2 {
  margin: 0 0 6px;
  font-size: 18px;
}

.close-copy p {
  margin: 0;
  font-size: 13px;
  color: var(--text);
  line-height: 1.45;
}

.close-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
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

.task-card {
  display: flex;
  flex-direction: column;
  min-height: 198px;
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
  flex-wrap: wrap;
  gap: 6px;
  min-height: 20px;
  margin-top: auto;
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

.health-label {
  flex-shrink: 0;
  white-space: nowrap;
}

.health-time {
  flex: 1 1 180px;
  min-width: 0;
  color: var(--muted);
  margin-left: auto;
  white-space: normal;
  font-variant-numeric: tabular-nums;
}

.task-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 32px;
}

.task-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.system-managed {
  align-self: center;
  color: var(--muted);
  font-size: 12px;
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

.job-start-time {
  display: grid;
  gap: 2px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  line-height: 1.35;
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
    grid-template-columns: minmax(0, 1fr);
  }

  .layout > *,
  .side {
    min-width: 0;
  }

  .side {
    position: static;
  }
}

@media (max-width: 560px) {
  .task-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .task-actions :deep(.el-button) {
    height: 36px;
    min-height: 36px;
    min-width: 64px;
    margin: 0;
    padding: 0 14px;
    border-radius: 6px;
    font-size: 13px;
    line-height: 1;
    letter-spacing: 0;
  }

  .custom-form > * {
    width: 100% !important;
  }
}
</style>
