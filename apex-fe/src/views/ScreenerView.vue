<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  ArrowUp,
  CopyDocument,
  Delete,
  EditPen,
  MoreFilled,
  Plus,
  Rank,
  Refresh,
  RefreshRight,
  Search,
  VideoPlay,
} from '@element-plus/icons-vue'
import {
  copyScreenerStrategy,
  copyScreenerTemplate,
  createScreenerStrategy,
  deleteScreenerStrategy,
  fetchScreenerMarket,
  fetchScreenerMeta,
  fetchScreenerStrategies,
  reorderScreenerStrategies,
  runScreener,
  runScreenerStrategy,
  toggleScreenerStrategy,
  updateScreenerStrategy,
} from '../api/screener'
import { batchBacktest } from '../api/backtest'
import { saveObserve } from '../api/observe'
import { buildTrailingDateRange } from '../utils/backtestLab.js'
import { resolveActionColumnVisible } from '../utils/responsiveTable.js'
import { useSessionViewState } from '../utils/viewState.js'

const router = useRouter()
const activeMode = ref('free')
const loading = ref(false)
const marketLoading = ref(false)
const strategyLoading = ref(false)
const screeningActive = ref(false)
const viewportWidth = ref(window.innerWidth)
const isMobileViewport = computed(() => viewportWidth.value <= 820)
const showActionColumn = computed(() => resolveActionColumnVisible(viewportWidth.value))
const mobileAdvancedOpen = ref(false)

const modeOptions = [
  { label: '自由筛选', value: 'free' },
  { label: '策略选股', value: 'strategy' },
]

const RULE_CATALOG = [
  { value: 'MARKET_BOARD', label: '市场板块', kind: 'market', operator: 'EQ' },
  { value: 'EXCLUDE_ST', label: '排除 ST', kind: 'boolean', operator: 'EQ' },
  { value: 'PE_TTM', label: '滚动市盈率', kind: 'number', operator: 'BETWEEN' },
  { value: 'PB', label: '市净率', kind: 'number', operator: 'BETWEEN' },
  { value: 'TOTAL_MV', label: '总市值（元）', kind: 'number', operator: 'BETWEEN' },
  { value: 'CIRC_MV', label: '流通市值（元）', kind: 'number', operator: 'BETWEEN' },
  { value: 'PCT_CHG', label: '当日涨跌幅（%）', kind: 'number', operator: 'BETWEEN' },
  { value: 'TURNOVER_RATE', label: '当日换手率（%）', kind: 'number', operator: 'BETWEEN' },
  { value: 'VOLUME_RATIO', label: '实时量比', kind: 'number', operator: 'GTE' },
  { value: 'RANGE_RETURN', label: '区间涨跌幅（%）', kind: 'number', operator: 'GTE', lookback: true },
  { value: 'LIMIT_UP_COUNT', label: '近期涨停次数', kind: 'integer', operator: 'GTE', lookback: true },
  { value: 'UP_DAYS', label: '连续上涨天数', kind: 'integer', operator: 'GTE' },
  { value: 'RS20', label: '20日相对强度（%）', kind: 'number', operator: 'GTE' },
  { value: 'ATR_PCT', label: 'ATR14/现价（%）', kind: 'number', operator: 'BETWEEN' },
  { value: 'PRICE_POSITION', label: '区间价格位置（%）', kind: 'number', operator: 'LTE', lookback: true },
  { value: 'DAYS_SINCE_LIMIT_UP', label: '距最近涨停天数', kind: 'integer', operator: 'LTE', lookback: true, defaultLookback: 10 },
  { value: 'VOLUME_MA_RATIO', label: '量能相对均量（%）', kind: 'number', operator: 'BETWEEN', lookback: true, defaultLookback: 5 },
  { value: 'CLOSE_MA_DISTANCE_PCT', label: '收盘相对均线距离（%）', kind: 'number', operator: 'BETWEEN', lookback: true, defaultLookback: 10 },
  { value: 'BREAKOUT_PREVIOUS_HIGH', label: '突破前期高点', kind: 'boolean', operator: 'EQ', lookback: true, defaultLookback: 3 },
  { value: 'MA_BULLISH_ALIGNMENT', label: 'MA5/MA10/MA20 多头排列', kind: 'boolean', operator: 'EQ' },
  { value: 'INTRADAY_ABOVE_AVG_RATIO', label: '均价线上方占比（%）', kind: 'number', operator: 'GTE' },
  { value: 'INTRADAY_CURRENT_ABOVE_AVG', label: '当前价不低于均价', kind: 'boolean', operator: 'EQ' },
  { value: 'INTRADAY_MAX_BELOW_MINUTES', label: '连续跌破均价分钟数', kind: 'integer', operator: 'LTE' },
  { value: 'LIMIT_UP_LEVEL', label: '连板层级', kind: 'integer', operator: 'EQ' },
  { value: 'FIRST_SEAL_TIME', label: '首次封板时间', kind: 'time', operator: 'LTE' },
  { value: 'LAST_SEAL_TIME', label: '最后封板时间', kind: 'time', operator: 'LTE' },
  { value: 'BREAK_COUNT', label: '炸板次数', kind: 'integer', operator: 'LTE' },
  { value: 'SEAL_AMOUNT', label: '封单金额（元）', kind: 'number', operator: 'GTE' },
  { value: 'AMOUNT', label: '成交额（元）', kind: 'number', operator: 'BETWEEN' },
  { value: 'THEME_LINKAGE_COUNT', label: '同题材涨停家数', kind: 'integer', operator: 'GTE' },
]

const NUMERIC_OPERATORS = [
  { label: '大于', value: 'GT' },
  { label: '大于等于', value: 'GTE' },
  { label: '小于', value: 'LT' },
  { label: '小于等于', value: 'LTE' },
  { label: '等于', value: 'EQ' },
  { label: '区间', value: 'BETWEEN' },
]

const strategies = ref([])
const selectedStrategyKey = ref('')
const strategyRunResult = ref(null)
const strategyEditorOpen = ref(false)
const strategyEditorSaving = ref(false)
const strategyManageOpen = ref(false)
const managedUserStrategies = ref([])
const draggingStrategyId = ref(null)
const strategyForm = ref(emptyStrategyForm())

const systemStrategies = computed(() => strategies.value.filter((item) => item.template))
const userStrategies = computed(() => strategies.value.filter((item) => !item.template))
const selectedStrategy = computed(() => strategies.value.find(
  (item) => strategyKey(item) === selectedStrategyKey.value,
) || null)
const strategyHasRun = computed(() => activeMode.value === 'strategy' && strategyRunResult.value)

function strategyKey(strategy) {
  return strategy?.template ? `template:${strategy.templateKey}` : `user:${strategy?.id}`
}

function emptyStrategyForm() {
  return {
    id: null,
    name: '',
    description: '',
    runMode: 'REALTIME',
    enabled: true,
    rules: [newStrategyRule('PCT_CHG')],
  }
}

function newStrategyRule(ruleType = 'PCT_CHG') {
  const catalog = RULE_CATALOG.find((item) => item.value === ruleType) || RULE_CATALOG[0]
  return {
    ruleType: catalog.value,
    operatorCode: catalog.operator,
    minValue: catalog.operator === 'BETWEEN' ? '' : '',
    maxValue: catalog.operator === 'BETWEEN' ? '' : '',
    intValue: catalog.kind === 'integer' ? 1 : null,
    textValue: catalog.kind === 'market' ? 'MAIN_BOARD' : catalog.kind === 'time' ? '103000' : '',
    boolValue: catalog.kind === 'boolean' ? true : null,
    lookbackDays: catalog.lookback ? (catalog.defaultLookback || 20) : null,
    toleranceValue: null,
  }
}

function syncViewportWidth() {
  viewportWidth.value = window.innerWidth
}

const meta = ref({
  marketCount: null,
  universeCount: null,
  universeBatchNo: null,
  note: '',
})

function emptyForm() {
  return {
    scope: '__MARKET__',
    groupName: '我的自选',
    peMin: '',
    peMax: '',
    pbMin: '',
    pbMax: '',
    industry: '',
    pctChgMin: '',
    pctChgMax: '',
    pctChg20Min: '',
    pctChg20Max: '',
    minCircMvYi: '',
    maxCircMvYi: '',
    minBars: '',
    excludeSt: true,
    excludeLimitUp: false,
    excludeLimitDown: false,
    minVolumeRatio: '',
    minUpDays: '',
    rs20Min: '',
    maxAtrPct: '',
    minAtrPct: '',
    limit: 50,
  }
}

const form = ref(emptyForm())
const rows = ref([])
const batchRows = ref([])

const marketKeyword = ref('')
const marketPage = ref(1)
const marketSize = ref(50)
const marketTotal = ref(0)
const marketRows = ref([])

useSessionViewState('screener', {
  activeMode,
  form,
  marketKeyword,
  marketPage,
  marketSize,
  selectedStrategyKey,
})

const displayRows = computed(() => {
  const sourceRows = activeMode.value === 'strategy'
    ? rows.value
    : screeningActive.value ? rows.value : marketRows.value
  const keyword = String(marketKeyword.value || '').trim().toLowerCase()
  if (!screeningActive.value || !keyword) return sourceRows
  return sourceRows.filter((row) =>
    String(row.code || '').toLowerCase().includes(keyword)
      || String(row.name || '').toLowerCase().includes(keyword),
  )
})

const mobileAdvancedFilterCount = computed(() => {
  const values = [
    form.value.peMin,
    form.value.peMax,
    form.value.pbMin,
    form.value.pbMax,
    form.value.industry,
    form.value.pctChgMin,
    form.value.pctChgMax,
    form.value.pctChg20Min,
    form.value.pctChg20Max,
    form.value.minCircMvYi,
    form.value.maxCircMvYi,
    form.value.minBars,
    form.value.minVolumeRatio,
    form.value.minUpDays,
    form.value.rs20Min,
    form.value.maxAtrPct,
    form.value.minAtrPct,
  ]
  const filledCount = values.filter((value) => value !== '' && value != null).length
  const optionCount = [
    !form.value.excludeSt,
    form.value.excludeLimitUp,
    form.value.excludeLimitDown,
    Number(form.value.limit || 50) !== 50,
  ].filter(Boolean).length
  return filledCount + optionCount
})

const mobileTotalPages = computed(() => {
  return Math.max(1, Math.ceil(marketTotal.value / marketSize.value))
})

const mobilePageRange = computed(() => {
  if (!marketTotal.value) return '0 条'
  const start = (marketPage.value - 1) * marketSize.value + 1
  const end = Math.min(marketPage.value * marketSize.value, marketTotal.value)
  return `${start}-${end} / ${marketTotal.value}`
})

function hasAdvancedFilters() {
  if (form.value.scope !== '__MARKET__') return true
  if (form.value.excludeLimitUp || form.value.excludeLimitDown) return true
  if (Number(form.value.limit || 50) !== 50) return true
  return [
    form.value.peMin,
    form.value.peMax,
    form.value.pbMin,
    form.value.pbMax,
    form.value.industry,
    form.value.pctChgMin,
    form.value.pctChgMax,
    form.value.pctChg20Min,
    form.value.pctChg20Max,
    form.value.minCircMvYi,
    form.value.maxCircMvYi,
    form.value.minBars,
    form.value.minVolumeRatio,
    form.value.minUpDays,
    form.value.rs20Min,
    form.value.maxAtrPct,
    form.value.minAtrPct,
  ].some((value) => value !== '' && value != null)
}

function numOrNull(v) {
  if (v === '' || v == null) return null
  const n = Number(v)
  return Number.isNaN(n) ? null : n
}

function formatPct(value) {
  if (value === '' || value == null) return '-'
  const percentage = Number(value)
  if (Number.isNaN(percentage)) return String(value)
  return `${percentage > 0 ? '+' : ''}${percentage.toFixed(2)}%`
}

function trendClass(value) {
  if (value === '' || value == null) return ''
  return Number(value) >= 0 ? 'up' : 'down'
}

function formatNumber(value, digits = 2) {
  if (value === '' || value == null) return '-'
  const numberValue = Number(value)
  if (Number.isNaN(numberValue)) return String(value)
  return numberValue.toFixed(digits)
}

function formatCircMv(value) {
  if (value === '' || value == null) return '-'
  const marketValue = Number(value)
  if (Number.isNaN(marketValue)) return String(value)
  return `${(marketValue / 1e8).toFixed(1)}亿`
}

function resolveGroupName() {
  if (form.value.scope === '__MARKET__') return '__MARKET__'
  return String(form.value.groupName || '').trim() || '我的自选'
}

async function loadMeta() {
  try {
    const res = await fetchScreenerMeta()
    meta.value = res.data || meta.value
  } catch {
    // 摘要失败不阻断选股
  }
}

async function loadStrategies(preserveSelection = true) {
  strategyLoading.value = true
  try {
    const res = await fetchScreenerStrategies()
    strategies.value = res.data || []
    const currentExists = strategies.value.some((item) => strategyKey(item) === selectedStrategyKey.value)
    if (!preserveSelection || !currentExists) {
      const recommended = strategies.value.find(
        (item) => item.templateKey === 'PUBLIC_FIRST_BOARD_DISPERSION',
      ) || strategies.value[0]
      selectedStrategyKey.value = recommended ? strategyKey(recommended) : ''
    }
  } catch (e) {
    strategies.value = []
    ElMessage.error(e.message || '加载策略失败')
  } finally {
    strategyLoading.value = false
  }
}

function onModeChange(mode) {
  rows.value = []
  batchRows.value = []
  screeningActive.value = false
  marketKeyword.value = ''
  if (mode === 'strategy') {
    strategyRunResult.value = null
    loadStrategies(true)
  } else {
    loadMarket(true)
  }
}

function onStrategyChange() {
  rows.value = []
  batchRows.value = []
  screeningActive.value = false
  strategyRunResult.value = null
}

function selectStrategy(strategy) {
  const nextStrategyKey = strategyKey(strategy)
  if (selectedStrategyKey.value === nextStrategyKey) return
  selectedStrategyKey.value = nextStrategyKey
  onStrategyChange()
}

async function onRunStrategy() {
  const strategy = selectedStrategy.value
  if (!strategy) {
    ElMessage.warning('请选择策略')
    return
  }
  strategyLoading.value = true
  try {
    const payload = {
      limit: Number(form.value.limit || 50),
    }
    if (strategy.template) payload.templateKey = strategy.templateKey
    else payload.strategyId = strategy.id
    const res = await runScreenerStrategy(payload)
    strategyRunResult.value = res.data || null
    rows.value = res.data?.matches || []
    screeningActive.value = true
    const matched = Number(res.data?.matchedCount || 0)
    const returned = rows.value.length
    ElMessage.success(matched > returned ? `命中 ${matched} 只，展示前 ${returned} 只` : `命中 ${matched} 只`)
  } catch (e) {
    rows.value = []
    strategyRunResult.value = null
    ElMessage.error(e.message || '策略运行失败')
  } finally {
    strategyLoading.value = false
  }
}

function ruleCatalog(ruleType) {
  return RULE_CATALOG.find((item) => item.value === ruleType) || RULE_CATALOG[0]
}

function ruleOperators(rule) {
  const kind = ruleCatalog(rule.ruleType).kind
  if (kind === 'boolean' || kind === 'market') return [{ label: '等于', value: 'EQ' }]
  if (kind === 'time') {
    return NUMERIC_OPERATORS.filter((item) => ['EQ', 'GTE', 'LTE'].includes(item.value))
  }
  return NUMERIC_OPERATORS
}

function onRuleTypeChange(rule) {
  Object.assign(rule, newStrategyRule(rule.ruleType))
}

function addStrategyRule() {
  strategyForm.value.rules.push(newStrategyRule())
}

function removeStrategyRule(index) {
  if (strategyForm.value.rules.length <= 1) {
    ElMessage.warning('策略至少保留一条规则')
    return
  }
  strategyForm.value.rules.splice(index, 1)
}

function openCreateStrategy() {
  strategyForm.value = emptyStrategyForm()
  strategyEditorOpen.value = true
}

function openEditStrategy() {
  const strategy = selectedStrategy.value
  if (!strategy || strategy.template) return
  strategyForm.value = {
    id: strategy.id,
    name: strategy.name || '',
    description: strategy.description || '',
    runMode: strategy.runMode || 'REALTIME',
    enabled: strategy.enabled !== false,
    rules: (strategy.rules || []).map((rule) => ({
      ruleType: rule.ruleType,
      operatorCode: rule.operatorCode,
      minValue: rule.minValue ?? '',
      maxValue: rule.maxValue ?? '',
      intValue: rule.intValue ?? null,
      textValue: rule.textValue ?? '',
      boolValue: rule.boolValue ?? null,
      lookbackDays: rule.lookbackDays ?? null,
      toleranceValue: rule.toleranceValue ?? null,
    })),
  }
  strategyEditorOpen.value = true
}

function strategyRulePayload(rule, index) {
  const catalog = ruleCatalog(rule.ruleType)
  const payload = {
    ruleType: rule.ruleType,
    operatorCode: rule.operatorCode,
    minValue: null,
    maxValue: null,
    intValue: null,
    textValue: null,
    boolValue: null,
    lookbackDays: catalog.lookback ? Number(rule.lookbackDays || 20) : null,
    toleranceValue: numOrNull(rule.toleranceValue),
    sortNo: (index + 1) * 10,
  }
  if (catalog.kind === 'boolean') payload.boolValue = Boolean(rule.boolValue)
  else if (catalog.kind === 'market' || catalog.kind === 'time') payload.textValue = rule.textValue || null
  else if (rule.operatorCode === 'BETWEEN') {
    payload.minValue = numOrNull(rule.minValue)
    payload.maxValue = numOrNull(rule.maxValue)
  } else if (catalog.kind === 'integer') payload.intValue = Number(rule.intValue)
  else payload.minValue = numOrNull(rule.minValue)
  return payload
}

async function saveStrategyEditor() {
  const name = String(strategyForm.value.name || '').trim()
  if (!name) {
    ElMessage.warning('请输入策略名称')
    return
  }
  if (!strategyForm.value.rules.length) {
    ElMessage.warning('策略至少需要一条规则')
    return
  }
  const payload = {
    name,
    description: String(strategyForm.value.description || '').trim() || null,
    runMode: strategyForm.value.runMode,
    enabled: strategyForm.value.enabled,
    rules: strategyForm.value.rules.map(strategyRulePayload),
  }
  strategyEditorSaving.value = true
  try {
    const res = strategyForm.value.id
      ? await updateScreenerStrategy(strategyForm.value.id, payload)
      : await createScreenerStrategy(payload)
    strategyEditorOpen.value = false
    selectedStrategyKey.value = strategyKey(res.data)
    await loadStrategies(true)
    ElMessage.success(strategyForm.value.id ? '策略已更新' : '策略已创建')
  } catch (e) {
    ElMessage.error(e.message || '保存策略失败')
  } finally {
    strategyEditorSaving.value = false
  }
}

async function copySelectedStrategy() {
  const strategy = selectedStrategy.value
  if (!strategy) return
  strategyLoading.value = true
  try {
    const res = strategy.template
      ? await copyScreenerTemplate(strategy.templateKey)
      : await copyScreenerStrategy(strategy.id)
    selectedStrategyKey.value = strategyKey(res.data)
    await loadStrategies(true)
    ElMessage.success('已复制为我的策略')
  } catch (e) {
    ElMessage.error(e.message || '复制策略失败')
  } finally {
    strategyLoading.value = false
  }
}

async function toggleSelectedStrategy() {
  const strategy = selectedStrategy.value
  if (!strategy || strategy.template) return
  try {
    await toggleScreenerStrategy(strategy.id, !strategy.enabled)
    await loadStrategies(true)
    ElMessage.success(strategy.enabled ? '策略已停用' : '策略已启用')
  } catch (e) {
    ElMessage.error(e.message || '更新策略状态失败')
  }
}

async function removeSelectedStrategy() {
  const strategy = selectedStrategy.value
  if (!strategy || strategy.template) return
  try {
    await ElMessageBox.confirm(`删除策略「${strategy.name}」？`, '删除策略', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteScreenerStrategy(strategy.id)
    await loadStrategies(false)
    rows.value = []
    strategyRunResult.value = null
    ElMessage.success('策略已删除')
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message || '删除策略失败')
  }
}

function openStrategyManager() {
  managedUserStrategies.value = userStrategies.value.map((item) => ({ ...item }))
  strategyManageOpen.value = true
}

function onStrategyDragStart(strategyId) {
  draggingStrategyId.value = strategyId
}

async function onStrategyDrop(targetId) {
  const sourceId = draggingStrategyId.value
  draggingStrategyId.value = null
  if (!sourceId || sourceId === targetId) return
  const sourceIndex = managedUserStrategies.value.findIndex((item) => item.id === sourceId)
  const targetIndex = managedUserStrategies.value.findIndex((item) => item.id === targetId)
  if (sourceIndex < 0 || targetIndex < 0) return
  const [moved] = managedUserStrategies.value.splice(sourceIndex, 1)
  managedUserStrategies.value.splice(targetIndex, 0, moved)
  await persistStrategyOrder()
}

async function moveStrategy(index, offset) {
  const target = index + offset
  if (target < 0 || target >= managedUserStrategies.value.length) return
  const [moved] = managedUserStrategies.value.splice(index, 1)
  managedUserStrategies.value.splice(target, 0, moved)
  await persistStrategyOrder()
}

async function persistStrategyOrder() {
  try {
    await reorderScreenerStrategies(managedUserStrategies.value.map((item) => item.id))
    await loadStrategies(true)
  } catch (e) {
    ElMessage.error(e.message || '策略排序失败')
    await loadStrategies(true)
    managedUserStrategies.value = userStrategies.value.map((item) => ({ ...item }))
  }
}

function handleStrategyCommand(command) {
  if (command === 'create') openCreateStrategy()
  else if (command === 'edit') openEditStrategy()
  else if (command === 'copy') copySelectedStrategy()
  else if (command === 'toggle') toggleSelectedStrategy()
  else if (command === 'remove') removeSelectedStrategy()
  else if (command === 'manage') openStrategyManager()
  else if (command === 'load-free') loadStrategyIntoFreeFilter()
}

function loadStrategyIntoFreeFilter() {
  const strategy = selectedStrategy.value
  if (!strategy) return
  const nextForm = emptyForm()
  let mapped = 0
  for (const rule of strategy.rules || []) {
    const between = rule.operatorCode === 'BETWEEN'
    if (rule.ruleType === 'EXCLUDE_ST') {
      nextForm.excludeSt = rule.boolValue !== false
      mapped++
    } else if (rule.ruleType === 'PE_TTM') {
      if (between) [nextForm.peMin, nextForm.peMax] = [rule.minValue ?? '', rule.maxValue ?? '']
      else if (['GTE', 'GT'].includes(rule.operatorCode)) nextForm.peMin = rule.minValue ?? ''
      else nextForm.peMax = rule.minValue ?? ''
      mapped++
    } else if (rule.ruleType === 'PB') {
      if (between) [nextForm.pbMin, nextForm.pbMax] = [rule.minValue ?? '', rule.maxValue ?? '']
      else if (['GTE', 'GT'].includes(rule.operatorCode)) nextForm.pbMin = rule.minValue ?? ''
      else nextForm.pbMax = rule.minValue ?? ''
      mapped++
    } else if (rule.ruleType === 'PCT_CHG') {
      if (between) [nextForm.pctChgMin, nextForm.pctChgMax] = [rule.minValue ?? '', rule.maxValue ?? '']
      else if (['GTE', 'GT'].includes(rule.operatorCode)) nextForm.pctChgMin = rule.minValue ?? ''
      else nextForm.pctChgMax = rule.minValue ?? ''
      mapped++
    } else if (rule.ruleType === 'CIRC_MV') {
      if (between) {
        nextForm.minCircMvYi = Number(rule.minValue || 0) / 1e8
        nextForm.maxCircMvYi = Number(rule.maxValue || 0) / 1e8
      }
      mapped++
    } else if (rule.ruleType === 'RANGE_RETURN' && Number(rule.lookbackDays || 20) === 20) {
      if (between) [nextForm.pctChg20Min, nextForm.pctChg20Max] = [rule.minValue ?? '', rule.maxValue ?? '']
      else if (['GTE', 'GT'].includes(rule.operatorCode)) nextForm.pctChg20Min = rule.minValue ?? ''
      else nextForm.pctChg20Max = rule.minValue ?? ''
      mapped++
    } else if (rule.ruleType === 'VOLUME_RATIO' && ['GTE', 'GT'].includes(rule.operatorCode)) {
      nextForm.minVolumeRatio = rule.minValue ?? ''
      mapped++
    } else if (rule.ruleType === 'UP_DAYS' && ['GTE', 'GT'].includes(rule.operatorCode)) {
      nextForm.minUpDays = rule.intValue ?? ''
      mapped++
    } else if (rule.ruleType === 'RS20' && ['GTE', 'GT'].includes(rule.operatorCode)) {
      nextForm.rs20Min = rule.minValue ?? ''
      mapped++
    } else if (rule.ruleType === 'ATR_PCT') {
      if (between) [nextForm.minAtrPct, nextForm.maxAtrPct] = [rule.minValue ?? '', rule.maxValue ?? '']
      mapped++
    }
  }
  form.value = nextForm
  activeMode.value = 'free'
  rows.value = []
  screeningActive.value = false
  strategyRunResult.value = null
  loadMarket(true)
  ElMessage.info(`已载入 ${mapped} 条可映射规则，其余策略规则未改动自由筛选器`)
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '—'
}

function evidenceSummary(row) {
  return (row?.evidence || []).slice(0, 4)
    .map((item) => `${item.ruleName} ${item.actualValue}`)
    .join(' · ')
}

async function onRun() {
  loading.value = true
  try {
    const res = await runScreener({
      groupName: resolveGroupName(),
      peMin: numOrNull(form.value.peMin),
      peMax: numOrNull(form.value.peMax),
      pbMin: numOrNull(form.value.pbMin),
      pbMax: numOrNull(form.value.pbMax),
      industry: form.value.industry || null,
      pctChgMin: numOrNull(form.value.pctChgMin),
      pctChgMax: numOrNull(form.value.pctChgMax),
      pctChg20Min: numOrNull(form.value.pctChg20Min),
      pctChg20Max: numOrNull(form.value.pctChg20Max),
      minCircMv: form.value.minCircMvYi !== '' ? Number(form.value.minCircMvYi) * 1e8 : null,
      maxCircMv: form.value.maxCircMvYi !== '' ? Number(form.value.maxCircMvYi) * 1e8 : null,
      minBars: numOrNull(form.value.minBars),
      excludeSt: form.value.excludeSt,
      excludeLimitUp: form.value.excludeLimitUp,
      excludeLimitDown: form.value.excludeLimitDown,
      minVolumeRatio: numOrNull(form.value.minVolumeRatio),
      minUpDays: numOrNull(form.value.minUpDays),
      rs20Min: numOrNull(form.value.rs20Min),
      maxAtrPct: numOrNull(form.value.maxAtrPct),
      minAtrPct: numOrNull(form.value.minAtrPct),
      limit: Number(form.value.limit || 50),
    })
    rows.value = res.data || []
    screeningActive.value = true
    const scopeLabel = form.value.scope === '__MARKET__' ? '全市场' : `自选「${resolveGroupName()}」`
    ElMessage.success(`${scopeLabel}选出 ${rows.value.length} 只`)
    loadMeta()
  } catch (e) {
    ElMessage.error(e.message || '选股失败')
  } finally {
    loading.value = false
  }
}

function onReset() {
  form.value = emptyForm()
  marketKeyword.value = ''
  mobileAdvancedOpen.value = false
  screeningActive.value = false
  rows.value = []
  batchRows.value = []
  ElMessage.info('已清空条件，默认全市场')
  loadMarket(true)
}

function onQuery() {
  if (hasAdvancedFilters()) {
    onRun()
    return
  }
  screeningActive.value = false
  loadMarket(true)
}

async function loadMarket(resetPage = false) {
  if (resetPage) marketPage.value = 1
  marketLoading.value = true
  try {
    const res = await fetchScreenerMarket({
      keyword: marketKeyword.value || undefined,
      page: marketPage.value,
      size: marketSize.value,
      excludeSt: form.value.excludeSt,
    })
    const page = res.data || {}
    marketRows.value = page.records || []
    marketTotal.value = Number(page.total || 0)
    marketPage.value = Number(page.current || marketPage.value)
    marketSize.value = Number(page.size || marketSize.value)
  } catch (e) {
    marketRows.value = []
    marketTotal.value = 0
    ElMessage.error(e.message || '加载全市场失败')
  } finally {
    marketLoading.value = false
  }
}

function onMarketPageChange(p) {
  marketPage.value = p
  loadMarket(false)
}

function onMarketSizeChange(s) {
  marketSize.value = s
  loadMarket(true)
}

async function addObserve(row) {
  if (!row?.code) return
  try {
    await saveObserve({
      code: row.code,
      name: row.name || '',
      status: 'WATCHING',
      reason: activeMode.value === 'strategy' ? `策略选股：${selectedStrategy.value?.name || ''}`
        : screeningActive.value ? '条件选股' : '全市场浏览',
      tags: activeMode.value === 'strategy' ? 'strategy-screener'
        : screeningActive.value ? 'screener' : 'market',
    })
    ElMessage.success(`${row.code} 已进观察池`)
  } catch (e) {
    ElMessage.error(e.message || '加入失败')
  }
}

async function onBatchBacktest() {
  if (!displayRows.value.length) {
    ElMessage.warning('请先选股')
    return
  }
  loading.value = true
  try {
    const codes = displayRows.value.slice(0, 8).map((r) => r.code)
    const backtestRange = buildTrailingDateRange(2)
    const res = await batchBacktest({
      codes,
      strategyId: 'S1',
      beginDate: backtestRange.beginDate,
      endDate: backtestRange.endDate,
      limit: 8,
    })
    batchRows.value = res.data || []
    ElMessage.success('批量回测完成')
  } catch (e) {
    ElMessage.error(e.message || '批量回测失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadMeta()
  if (activeMode.value === 'strategy') loadStrategies(true)
  else loadMarket(true)
  window.addEventListener('resize', syncViewportWidth)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewportWidth)
})
</script>

<template>
  <div class="page screener-page">
    <header class="header screener-header">
      <div>
        <p class="eyebrow">Screener</p>
        <h1>{{ isMobileViewport ? '股票筛选' : '股票' }}</h1>
        <p class="meta-line">
          <span class="chip">全市场 <b>{{ meta.marketCount ?? '—' }}</b></span>
          <span class="chip pool">股票池 <b>{{ meta.universeCount ?? '—' }}</b></span>
          <span v-if="meta.universeBatchNo" class="muted meta-batch">批次 {{ meta.universeBatchNo }}</span>
        </p>
        <p v-if="meta.note" class="hint">{{ meta.note }}</p>
      </div>
      <div class="actions header-refresh-actions">
        <el-button
          v-if="isMobileViewport"
          class="mobile-refresh-button"
          :icon="Refresh"
          aria-label="刷新股票数量"
          title="刷新股票数量"
          @click="loadMeta"
        />
        <el-button v-else @click="loadMeta">刷新数量</el-button>
      </div>
    </header>

    <div class="screener-mode-switch" aria-label="选股模式">
      <el-segmented v-model="activeMode" :options="modeOptions" @change="onModeChange" />
    </div>

    <template v-if="activeMode === 'free'">
    <section v-if="!isMobileViewport" class="filter-panel desktop-filter-panel" aria-label="股票筛选条件">
      <div class="filter-heading">
        <div>
          <h2>股票列表</h2>
          <span class="muted">
            {{ screeningActive ? `筛选结果 ${displayRows.length} 只` : `共 ${marketTotal} 只` }} · 池内标「池」
          </span>
        </div>
        <div class="actions row-actions">
          <el-button type="primary" :loading="loading || marketLoading" @click="onQuery">查询</el-button>
          <el-button @click="onReset">重置</el-button>
          <el-button :disabled="!screeningActive" :loading="loading" @click="onBatchBacktest">批量回测前8</el-button>
        </div>
      </div>

      <el-form :inline="true" class="form" @submit.prevent="onQuery">
        <el-form-item label="代码/名称">
          <el-input v-model="marketKeyword" clearable style="width: 140px" @keyup.enter="onQuery" />
        </el-form-item>
        <el-form-item label="范围">
          <el-select v-model="form.scope" style="width: 120px">
            <el-option
              :label="meta.marketCount != null ? `全部市场 (${meta.marketCount})` : '全部市场'"
              value="__MARKET__"
            />
            <el-option label="自选分组" value="__WATCH__" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scope === '__WATCH__'" label="分组">
          <el-input v-model="form.groupName" style="width: 120px" placeholder="我的自选" clearable />
        </el-form-item>
        <el-form-item label="PE≥"><el-input v-model="form.peMin" clearable style="width: 70px" /></el-form-item>
        <el-form-item label="PE≤"><el-input v-model="form.peMax" clearable style="width: 70px" /></el-form-item>
        <el-form-item label="PB≥"><el-input v-model="form.pbMin" clearable style="width: 70px" /></el-form-item>
        <el-form-item label="PB≤"><el-input v-model="form.pbMax" clearable style="width: 70px" /></el-form-item>
        <el-form-item label="行业"><el-input v-model="form.industry" clearable style="width: 120px" placeholder="如 银行" /></el-form-item>
        <el-form-item label="今日≥"><el-input v-model="form.pctChgMin" clearable style="width: 70px" placeholder="%" /></el-form-item>
        <el-form-item label="今日≤"><el-input v-model="form.pctChgMax" clearable style="width: 70px" placeholder="%" /></el-form-item>
        <el-form-item label="20日≥"><el-input v-model="form.pctChg20Min" clearable style="width: 70px" placeholder="%" /></el-form-item>
        <el-form-item label="20日≤"><el-input v-model="form.pctChg20Max" clearable style="width: 70px" placeholder="%" /></el-form-item>
        <el-form-item label="流通≥亿"><el-input v-model="form.minCircMvYi" clearable style="width: 80px" /></el-form-item>
        <el-form-item label="流通≤亿"><el-input v-model="form.maxCircMvYi" clearable style="width: 80px" /></el-form-item>
        <el-form-item label="K线≥"><el-input v-model="form.minBars" clearable style="width: 80px" placeholder="可选" /></el-form-item>
        <el-form-item>
          <template #label><TermTip term="volume_ratio">量比≥</TermTip></template>
          <el-input v-model="form.minVolumeRatio" clearable style="width: 70px" placeholder="可选" />
        </el-form-item>
        <el-form-item>
          <template #label><TermTip term="up_days">连涨≥</TermTip></template>
          <el-input v-model="form.minUpDays" clearable style="width: 70px" placeholder="天" />
        </el-form-item>
        <el-form-item>
          <template #label><TermTip term="rs20">RS20≥</TermTip></template>
          <el-input v-model="form.rs20Min" clearable style="width: 70px" placeholder="相对300" />
        </el-form-item>
        <el-form-item>
          <template #label><TermTip term="atr_pct">ATR%≤</TermTip></template>
          <el-input v-model="form.maxAtrPct" clearable style="width: 70px" placeholder="可选" />
        </el-form-item>
        <el-form-item>
          <template #label><TermTip term="atr_pct">ATR%≥</TermTip></template>
          <el-input v-model="form.minAtrPct" clearable style="width: 70px" />
        </el-form-item>
        <el-form-item label="条数"><el-input v-model="form.limit" style="width: 70px" /></el-form-item>
        <el-form-item><el-checkbox v-model="form.excludeSt">排除ST</el-checkbox></el-form-item>
        <el-form-item><el-checkbox v-model="form.excludeLimitUp">排除涨停</el-checkbox></el-form-item>
        <el-form-item><el-checkbox v-model="form.excludeLimitDown">排除跌停</el-checkbox></el-form-item>
      </el-form>
    </section>

    <section v-else class="mobile-filter-surface" aria-label="股票筛选条件">
      <div class="mobile-filter-heading">
        <div>
          <h2>筛选条件</h2>
          <span>{{ screeningActive ? '当前为条件筛选结果' : '默认浏览全部市场' }}</span>
        </div>
        <span v-if="mobileAdvancedFilterCount" class="mobile-filter-count">
          {{ mobileAdvancedFilterCount }} 项已设置
        </span>
      </div>

      <form class="mobile-filter-form" @submit.prevent="onQuery">
        <label class="mobile-field mobile-keyword-field">
          <span>代码或名称</span>
          <el-input
            v-model="marketKeyword"
            clearable
            :prefix-icon="Search"
            placeholder="输入代码或股票名称"
            inputmode="search"
          />
        </label>

        <fieldset class="mobile-scope-field">
          <legend>筛选范围</legend>
          <div class="mobile-segmented" role="group" aria-label="筛选范围">
            <button
              type="button"
              :class="{ 'is-active': form.scope === '__MARKET__' }"
              :aria-pressed="form.scope === '__MARKET__'"
              @click="form.scope = '__MARKET__'"
            >
              全部市场
            </button>
            <button
              type="button"
              :class="{ 'is-active': form.scope === '__WATCH__' }"
              :aria-pressed="form.scope === '__WATCH__'"
              @click="form.scope = '__WATCH__'"
            >
              自选分组
            </button>
          </div>
        </fieldset>

        <label v-if="form.scope === '__WATCH__'" class="mobile-field">
          <span>自选分组</span>
          <el-input v-model="form.groupName" clearable placeholder="我的自选" />
        </label>

        <button
          type="button"
          class="advanced-filter-toggle"
          :aria-expanded="mobileAdvancedOpen"
          aria-controls="mobile-screener-advanced"
          @click="mobileAdvancedOpen = !mobileAdvancedOpen"
        >
          <span>
            更多条件
            <small v-if="mobileAdvancedFilterCount">{{ mobileAdvancedFilterCount }}</small>
          </span>
          <el-icon :class="{ 'is-open': mobileAdvancedOpen }"><ArrowDown /></el-icon>
        </button>

        <div v-show="mobileAdvancedOpen" id="mobile-screener-advanced" class="mobile-advanced-filters">
          <section class="mobile-filter-group">
            <h3>估值</h3>
            <div class="mobile-field-grid">
              <label class="mobile-field">
                <span>PE 最低</span>
                <el-input v-model="form.peMin" clearable inputmode="decimal" placeholder="不限" />
              </label>
              <label class="mobile-field">
                <span>PE 最高</span>
                <el-input v-model="form.peMax" clearable inputmode="decimal" placeholder="不限" />
              </label>
              <label class="mobile-field">
                <span>PB 最低</span>
                <el-input v-model="form.pbMin" clearable inputmode="decimal" placeholder="不限" />
              </label>
              <label class="mobile-field">
                <span>PB 最高</span>
                <el-input v-model="form.pbMax" clearable inputmode="decimal" placeholder="不限" />
              </label>
            </div>
          </section>

          <section class="mobile-filter-group">
            <h3>涨跌与行业</h3>
            <div class="mobile-field-grid">
              <label class="mobile-field mobile-field-wide">
                <span>行业</span>
                <el-input v-model="form.industry" clearable placeholder="如 银行" />
              </label>
              <label class="mobile-field">
                <span>今日最低</span>
                <el-input v-model="form.pctChgMin" clearable inputmode="decimal" placeholder="%" />
              </label>
              <label class="mobile-field">
                <span>今日最高</span>
                <el-input v-model="form.pctChgMax" clearable inputmode="decimal" placeholder="%" />
              </label>
              <label class="mobile-field">
                <span>20日最低</span>
                <el-input v-model="form.pctChg20Min" clearable inputmode="decimal" placeholder="%" />
              </label>
              <label class="mobile-field">
                <span>20日最高</span>
                <el-input v-model="form.pctChg20Max" clearable inputmode="decimal" placeholder="%" />
              </label>
            </div>
          </section>

          <section class="mobile-filter-group">
            <h3>规模与趋势</h3>
            <div class="mobile-field-grid">
              <label class="mobile-field">
                <span>流通市值最低</span>
                <el-input v-model="form.minCircMvYi" clearable inputmode="decimal" placeholder="亿元" />
              </label>
              <label class="mobile-field">
                <span>流通市值最高</span>
                <el-input v-model="form.maxCircMvYi" clearable inputmode="decimal" placeholder="亿元" />
              </label>
              <label class="mobile-field">
                <span>K线数量最低</span>
                <el-input v-model="form.minBars" clearable inputmode="numeric" placeholder="条" />
              </label>
              <label class="mobile-field">
                <span><TermTip term="volume_ratio">量比最低</TermTip></span>
                <el-input v-model="form.minVolumeRatio" clearable inputmode="decimal" placeholder="不限" />
              </label>
              <label class="mobile-field">
                <span><TermTip term="up_days">连续上涨最低</TermTip></span>
                <el-input v-model="form.minUpDays" clearable inputmode="numeric" placeholder="天" />
              </label>
              <label class="mobile-field">
                <span><TermTip term="rs20">RS20 最低</TermTip></span>
                <el-input v-model="form.rs20Min" clearable inputmode="decimal" placeholder="相对沪深300" />
              </label>
              <label class="mobile-field">
                <span><TermTip term="atr_pct">ATR% 最低</TermTip></span>
                <el-input v-model="form.minAtrPct" clearable inputmode="decimal" placeholder="不限" />
              </label>
              <label class="mobile-field">
                <span><TermTip term="atr_pct">ATR% 最高</TermTip></span>
                <el-input v-model="form.maxAtrPct" clearable inputmode="decimal" placeholder="不限" />
              </label>
            </div>
          </section>

          <section class="mobile-filter-group mobile-filter-options">
            <h3>结果与风险</h3>
            <label class="mobile-field mobile-limit-field">
              <span>最多返回</span>
              <el-input v-model="form.limit" inputmode="numeric" />
            </label>
            <div class="mobile-risk-options">
              <el-checkbox v-model="form.excludeSt">排除 ST</el-checkbox>
              <el-checkbox v-model="form.excludeLimitUp">排除涨停</el-checkbox>
              <el-checkbox v-model="form.excludeLimitDown">排除跌停</el-checkbox>
            </div>
          </section>
        </div>

        <div class="mobile-filter-actions">
          <el-button type="primary" native-type="submit" :icon="Search" :loading="loading || marketLoading">
            查询股票
          </el-button>
          <el-button :icon="RefreshRight" @click="onReset">重置</el-button>
        </div>
      </form>
    </section>
    </template>

    <section v-else class="strategy-panel" aria-label="策略选股">
      <div class="strategy-toolbar">
        <div v-if="!isMobileViewport" class="strategy-library-heading">
          <span>策略库</span>
          <strong>{{ systemStrategies.length }} 套系统模板<span v-if="userStrategies.length"> · {{ userStrategies.length }} 套我的策略</span></strong>
        </div>
        <div v-else class="strategy-selector">
          <span>选择策略</span>
          <el-select
            class="mobile-strategy-select"
            v-model="selectedStrategyKey"
            :loading="strategyLoading"
            placeholder="选择系统模板或我的策略"
            popper-class="screener-strategy-popper"
            @change="onStrategyChange"
          >
            <el-option-group v-if="systemStrategies.length" label="系统模板">
              <el-option
                v-for="strategy in systemStrategies"
                :key="strategyKey(strategy)"
                :label="strategy.name"
                :value="strategyKey(strategy)"
              />
            </el-option-group>
            <el-option-group v-if="userStrategies.length" label="我的策略">
              <el-option
                v-for="strategy in userStrategies"
                :key="strategyKey(strategy)"
                :label="`${strategy.enabled ? '' : '已停用 · '}${strategy.name}`"
                :value="strategyKey(strategy)"
              />
            </el-option-group>
          </el-select>
        </div>
        <div class="strategy-actions">
          <el-button
            type="primary"
            :icon="VideoPlay"
            :loading="strategyLoading"
            :disabled="!selectedStrategy || selectedStrategy.enabled === false"
            @click="onRunStrategy"
          >
            运行策略
          </el-button>
          <el-dropdown trigger="click" @command="handleStrategyCommand">
            <el-button :icon="MoreFilled" aria-label="策略操作" title="策略操作">
              <span class="strategy-action-label">策略操作</span>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="create" :icon="Plus">新建策略</el-dropdown-item>
                <el-dropdown-item
                  command="copy"
                  :icon="CopyDocument"
                  :disabled="!selectedStrategy"
                >
                  复制策略
                </el-dropdown-item>
                <el-dropdown-item
                  command="edit"
                  :icon="EditPen"
                  :disabled="!selectedStrategy || selectedStrategy.template"
                >
                  编辑策略
                </el-dropdown-item>
                <el-dropdown-item command="manage" :icon="Rank" :disabled="!userStrategies.length">
                  策略排序
                </el-dropdown-item>
                <el-dropdown-item command="load-free" :disabled="!selectedStrategy" divided>
                  载入自由筛选
                </el-dropdown-item>
                <el-dropdown-item
                  command="toggle"
                  :disabled="!selectedStrategy || selectedStrategy.template"
                >
                  {{ selectedStrategy?.enabled ? '停用策略' : '启用策略' }}
                </el-dropdown-item>
                <el-dropdown-item
                  command="remove"
                  :icon="Delete"
                  :disabled="!selectedStrategy || selectedStrategy.template"
                  divided
                >
                  删除策略
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <div v-if="selectedStrategy" class="strategy-workspace">
        <nav v-if="!isMobileViewport" class="strategy-catalog" aria-label="策略库">
          <section class="strategy-catalog-group">
            <h2>系统策略 <small>{{ systemStrategies.length }}</small></h2>
            <button
              v-for="strategy in systemStrategies"
              :key="strategyKey(strategy)"
              type="button"
              :class="{ 'is-active': strategyKey(strategy) === selectedStrategyKey }"
              :aria-current="strategyKey(strategy) === selectedStrategyKey ? 'true' : undefined"
              @click="selectStrategy(strategy)"
            >
              <span>
                <b>{{ strategy.name }}</b>
                <small>{{ strategy.guide?.category || '系统模板' }}</small>
              </span>
              <em :class="strategy.runMode === 'CLOSE' ? 'is-close' : 'is-realtime'">
                {{ strategy.runMode === 'CLOSE' ? '收盘' : '实时' }}
              </em>
            </button>
          </section>
          <section v-if="userStrategies.length" class="strategy-catalog-group">
            <h2>我的策略 <small>{{ userStrategies.length }}</small></h2>
            <button
              v-for="strategy in userStrategies"
              :key="strategyKey(strategy)"
              type="button"
              :class="{ 'is-active': strategyKey(strategy) === selectedStrategyKey }"
              :aria-current="strategyKey(strategy) === selectedStrategyKey ? 'true' : undefined"
              @click="selectStrategy(strategy)"
            >
              <span>
                <b>{{ strategy.name }}</b>
                <small>{{ strategy.enabled ? '已启用' : '已停用' }}</small>
              </span>
              <em :class="strategy.runMode === 'CLOSE' ? 'is-close' : 'is-realtime'">
                {{ strategy.runMode === 'CLOSE' ? '收盘' : '实时' }}
              </em>
            </button>
          </section>
        </nav>

        <article class="strategy-definition">
          <div class="strategy-title-line">
            <div>
              <p v-if="selectedStrategy.guide?.category" class="strategy-category">{{ selectedStrategy.guide.category }}</p>
              <h2>{{ selectedStrategy.name }}</h2>
              <p>{{ selectedStrategy.description || '未填写策略说明' }}</p>
            </div>
            <div class="strategy-flags">
              <el-tag size="small" effect="plain">{{ selectedStrategy.template ? '系统模板' : `版本 ${selectedStrategy.versionNo}` }}</el-tag>
              <el-tag size="small" :type="selectedStrategy.runMode === 'CLOSE' ? 'warning' : 'info'" effect="plain">
                {{ selectedStrategy.runMode === 'CLOSE' ? '收盘策略' : '实时策略' }}
              </el-tag>
              <el-tag v-if="!selectedStrategy.template" size="small" :type="selectedStrategy.enabled ? 'success' : 'info'" effect="plain">
                {{ selectedStrategy.enabled ? '已启用' : '已停用' }}
              </el-tag>
            </div>
          </div>

          <template v-if="selectedStrategy.guide">
            <section class="strategy-core-idea">
              <span>核心逻辑</span>
              <p>{{ selectedStrategy.guide.coreIdea }}</p>
            </section>

            <div class="strategy-guide-overview">
              <section>
                <h3>通俗理解</h3>
                <p>{{ selectedStrategy.guide.plainExplanation }}</p>
              </section>
              <section>
                <h3>适用环境</h3>
                <p>{{ selectedStrategy.guide.suitableMarket }}</p>
              </section>
            </div>

            <div class="strategy-playbook-grid">
              <section>
                <h3>操作方法</h3>
                <ol>
                  <li v-for="step in selectedStrategy.guide.executionSteps" :key="step">{{ step }}</li>
                </ol>
              </section>
              <section class="strategy-risk-section">
                <h3>风险纪律</h3>
                <ul>
                  <li v-for="risk in selectedStrategy.guide.riskNotes" :key="risk">{{ risk }}</li>
                </ul>
              </section>
            </div>
          </template>

          <section class="strategy-rules-section">
            <div class="strategy-rules-heading">
              <h3>筛选条件</h3>
              <span>{{ selectedStrategy.rules?.length || 0 }} 条规则，全部满足才会命中</span>
            </div>
            <ol class="strategy-rule-list">
              <li v-for="rule in selectedStrategy.rules" :key="rule.id || `${rule.ruleType}-${rule.sortNo}`">
                <span>{{ rule.ruleName }}</span>
                <b>{{ rule.summary }}</b>
              </li>
            </ol>
          </section>
          <p v-if="selectedStrategy.disclaimer" class="strategy-disclaimer">{{ selectedStrategy.disclaimer }}</p>
        </article>
      </div>

      <div v-else-if="!strategyLoading" class="strategy-empty">
        <span>暂无可用策略</span>
        <el-button type="primary" plain :icon="Plus" @click="openCreateStrategy">新建策略</el-button>
      </div>

      <div v-if="strategyRunResult?.dataStatus" class="strategy-data-status">
        <div class="strategy-status-grid">
          <span><small>运行时间</small><b>{{ formatDateTime(strategyRunResult.dataStatus.runAt) }}</b></span>
          <span><small>实时截面</small><b>{{ formatDateTime(strategyRunResult.dataStatus.snapshotAsOf) }}</b></span>
          <span><small>日线截止</small><b>{{ strategyRunResult.dataStatus.dailyAsOf || '—' }}</b></span>
          <span><small>分时截止</small><b>{{ strategyRunResult.dataStatus.intradayAsOf || '未使用' }}</b></span>
          <span><small>阶段命中</small><b>{{ strategyRunResult.snapshotMatchedCount }} → {{ strategyRunResult.historicalMatchedCount }} → {{ strategyRunResult.matchedCount }}</b></span>
          <span><small>分时复核</small><b>{{ strategyRunResult.dataStatus.intradayReviewedCount }} / {{ strategyRunResult.dataStatus.intradayCandidateCount }}</b></span>
        </div>
        <ul v-if="strategyRunResult.dataStatus.issues?.length" class="strategy-issues">
          <li v-for="issue in strategyRunResult.dataStatus.issues" :key="`${issue.stage}-${issue.issueType}`">
            {{ issue.message }}（{{ issue.count }} 只）
          </li>
        </ul>
      </div>
    </section>

    <el-table
      v-if="!isMobileViewport"
      v-loading="loading || marketLoading || strategyLoading"
      class="screener-table"
      :data="displayRows"
      stripe
      style="width: 100%"
      empty-text="暂无符合条件的股票"
    >
        <el-table-column prop="name" label="股票" min-width="132" align="center">
          <template #default="{ row }">
            <StockIdentity
              :security="row"
              interactive
              include-main
              compact
              @select="router.push(`/stock/${row.code}`)"
            />
          </template>
        </el-table-column>
        <el-table-column v-if="activeMode === 'free' && !screeningActive" label="股票池" width="74">
          <template #default="{ row }">
            <el-tag v-if="row.inUniverse" size="small" type="success" effect="plain">池</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="latestPrice" label="现价" min-width="84" />
        <el-table-column prop="pctChg" label="今日%" min-width="80">
          <template #default="{ row }">
            <span :class="Number(row.pctChg) >= 0 ? 'up' : 'down'">{{ row.pctChg ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="activeMode === 'free' && screeningActive" prop="pctChg5" label="5日%" min-width="80">
          <template #default="{ row }">
            <span :class="Number(row.pctChg5) >= 0 ? 'up' : 'down'">{{ row.pctChg5 ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="activeMode === 'free' && screeningActive" prop="pctChg20" label="20日%" min-width="80">
          <template #default="{ row }">
            <span :class="Number(row.pctChg20) >= 0 ? 'up' : 'down'">{{ row.pctChg20 ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="screeningActive" prop="volumeRatio" min-width="72">
          <template #header><TermTip term="volume_ratio">量比</TermTip></template>
        </el-table-column>
        <el-table-column v-if="activeMode === 'free' && screeningActive" prop="upDays" min-width="64">
          <template #header><TermTip term="up_days">连涨</TermTip></template>
        </el-table-column>
        <el-table-column v-if="activeMode === 'free' && screeningActive" prop="rs20VsHs300" min-width="72" sortable>
          <template #header><TermTip term="rs20">RS20</TermTip></template>
        </el-table-column>
        <el-table-column v-if="activeMode === 'free' && screeningActive" prop="atrPct" min-width="72" sortable>
          <template #header><TermTip term="atr_pct">ATR%</TermTip></template>
        </el-table-column>
        <el-table-column prop="peTtm" min-width="72" sortable>
          <template #header><TermTip term="pe_ttm">PE</TermTip></template>
        </el-table-column>
        <el-table-column prop="pb" min-width="72" sortable>
          <template #header><TermTip term="pb">PB</TermTip></template>
        </el-table-column>
        <el-table-column v-if="activeMode === 'strategy'" prop="turnoverRate" label="换手%" min-width="78" />
        <el-table-column v-if="activeMode === 'strategy'" prop="totalMv" label="总值(亿)" min-width="92">
          <template #default="{ row }">
            {{ row.totalMv != null ? (Number(row.totalMv) / 1e8).toFixed(1) : '-' }}
          </template>
        </el-table-column>
        <el-table-column v-else prop="circMv" label="流通(亿)" min-width="88">
          <template #default="{ row }">
            {{ row.circMv != null ? (Number(row.circMv) / 1e8).toFixed(1) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="industry" label="行业" min-width="110" show-overflow-tooltip />
        <el-table-column v-if="activeMode === 'free'" prop="barCount" label="K线" min-width="72" />
        <el-table-column v-if="activeMode === 'strategy'" label="命中依据" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="evidence-line">{{ evidenceSummary(row) || '规则全部通过' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="showActionColumn" label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link @click="router.push({ path: '/backtest', query: { code: row.code } })">回测</el-button>
            <el-button link type="warning" @click="addObserve(row)">观察</el-button>
            <el-button link @click="router.push({ path: '/paper', query: { code: row.code, side: 'BUY' } })">模拟</el-button>
          </template>
        </el-table-column>
    </el-table>

    <section v-if="isMobileViewport" class="mobile-results-section" aria-labelledby="mobile-screener-results-title">
      <div class="mobile-results-heading">
        <div>
          <h2 id="mobile-screener-results-title">股票列表</h2>
          <span>
            {{ activeMode === 'strategy'
              ? strategyHasRun ? `命中 ${strategyRunResult.matchedCount} 只` : '请选择并运行策略'
              : screeningActive ? `筛选结果 ${displayRows.length} 只` : `共 ${marketTotal} 只` }}
            <template v-if="activeMode === 'free' && !screeningActive"> · 池内标「池」</template>
          </span>
        </div>
        <el-button
          v-if="screeningActive"
          plain
          :loading="loading"
          @click="onBatchBacktest"
        >
          回测前 8
        </el-button>
      </div>

      <div v-loading="loading || marketLoading || strategyLoading" class="screener-mobile-list">
        <button
          v-for="row in displayRows"
          :key="row.code"
          type="button"
          class="screener-mobile-card"
          @click="router.push(`/stock/${row.code}`)"
        >
          <span class="mobile-stock-heading">
            <span class="mobile-stock-identity">
              <StockIdentity :security="row" include-main compact />
              <span v-if="activeMode === 'free' && !screeningActive && row.inUniverse" class="universe-badge">池</span>
            </span>
            <span class="mobile-stock-quote">
              <strong :class="trendClass(row.pctChg)">{{ formatPct(row.pctChg) }}</strong>
              <small>{{ row.latestPrice ?? '-' }}</small>
            </span>
          </span>

          <span class="mobile-stock-metrics" :class="{ 'is-screening': screeningActive }">
            <template v-if="activeMode === 'strategy'">
              <span><small>换手</small><b>{{ formatPct(row.turnoverRate) }}</b></span>
              <span><small>量比</small><b>{{ formatNumber(row.volumeRatio) }}</b></span>
              <span><small>总市值</small><b>{{ formatCircMv(row.totalMv) }}</b></span>
              <span><small>涨停</small><b>{{ row.limitUpCount ?? '—' }}</b></span>
            </template>
            <template v-else>
              <span>
                <small>PE</small>
                <b>{{ formatNumber(row.peTtm) }}</b>
              </span>
              <span>
                <small>PB</small>
                <b>{{ formatNumber(row.pb) }}</b>
              </span>
              <span>
                <small>流通</small>
                <b>{{ formatCircMv(row.circMv) }}</b>
              </span>
              <span>
                <small>行业</small>
                <b class="mobile-industry">{{ row.industry || '-' }}</b>
              </span>
            </template>
            <template v-if="activeMode === 'free' && screeningActive">
              <span>
                <small>20日</small>
                <b :class="trendClass(row.pctChg20)">{{ formatPct(row.pctChg20) }}</b>
              </span>
              <span>
                <small>量比</small>
                <b>{{ formatNumber(row.volumeRatio) }}</b>
              </span>
              <span>
                <small>RS20</small>
                <b>{{ formatNumber(row.rs20VsHs300) }}</b>
              </span>
              <span>
                <small>ATR%</small>
                <b>{{ formatNumber(row.atrPct) }}</b>
              </span>
            </template>
          </span>
          <span v-if="activeMode === 'strategy'" class="mobile-evidence-line">
            {{ evidenceSummary(row) || '规则全部通过' }}
          </span>
        </button>

        <div v-if="!displayRows.length && !loading && !marketLoading" class="mobile-empty-state">
          暂无符合条件的股票
        </div>
      </div>

      <div v-if="activeMode === 'free' && !screeningActive" class="mobile-pager" aria-label="股票列表分页">
        <el-button
          class="mobile-pager-button"
          :disabled="marketPage <= 1 || marketLoading"
          @click="onMarketPageChange(marketPage - 1)"
        >
          上一页
        </el-button>
        <span>
          <b>{{ marketPage }} / {{ mobileTotalPages }}</b>
          <small>{{ mobilePageRange }}</small>
        </span>
        <el-button
          class="mobile-pager-button"
          :disabled="marketPage >= mobileTotalPages || marketLoading"
          @click="onMarketPageChange(marketPage + 1)"
        >
          下一页
        </el-button>
      </div>
    </section>

    <div v-if="activeMode === 'free' && !isMobileViewport && !screeningActive" class="pager">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="marketTotal"
        :current-page="marketPage"
        :page-size="marketSize"
        :page-sizes="[50, 100, 200]"
        @current-change="onMarketPageChange"
        @size-change="onMarketSizeChange"
      />
    </div>

    <section v-if="isMobileViewport && batchRows.length" class="mobile-batch-results" aria-labelledby="mobile-batch-title">
      <h3 id="mobile-batch-title">批量回测排名</h3>
      <button
        v-for="(row, index) in batchRows"
        :key="`${row.code}-${row.jobId || index}`"
        type="button"
        :disabled="!row.jobId"
        @click="row.jobId && router.push({ path: '/backtest', query: { code: row.code } })"
      >
        <span class="mobile-batch-rank">{{ index + 1 }}</span>
        <span class="mobile-batch-stock">
          <b>{{ row.code }}</b>
          <small>{{ row.error || `${row.tradeCount ?? '-'} 笔成交` }}</small>
        </span>
        <span class="mobile-batch-return">
          <b :class="trendClass(row.totalReturn)">
            {{ row.totalReturn != null ? formatPct(Number(row.totalReturn) * 100) : '-' }}
          </b>
          <small>回撤 {{ row.maxDrawdown != null ? formatPct(Number(row.maxDrawdown) * 100) : '-' }}</small>
        </span>
      </button>
    </section>

    <h3 v-if="!isMobileViewport && batchRows.length">批量回测排名</h3>
    <el-table v-if="!isMobileViewport && batchRows.length" :data="batchRows" size="small" style="width: 100%">
        <el-table-column prop="code" label="股票" width="120">
          <template #default="{ row }"><StockIdentity :security="row" compact /></template>
        </el-table-column>
        <el-table-column prop="jobId" label="任务" width="80" />
        <el-table-column prop="totalReturn" label="收益" width="100">
          <template #default="{ row }">
            {{ row.totalReturn != null ? (Number(row.totalReturn) * 100).toFixed(2) + '%' : row.error || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="maxDrawdown" label="回撤" width="100">
          <template #default="{ row }">
            {{ row.maxDrawdown != null ? (Number(row.maxDrawdown) * 100).toFixed(2) + '%' : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="sharpe" label="夏普" width="90" />
        <el-table-column prop="sortino" label="Sortino" width="90" />
        <el-table-column prop="tradeCount" label="成交" width="80" />
        <el-table-column label="详情" width="100">
          <template #default="{ row }">
            <el-button v-if="row.jobId" link type="primary" @click="router.push({ path: '/backtest', query: { code: row.code } })">查看</el-button>
          </template>
        </el-table-column>
    </el-table>

    <el-dialog
      v-model="strategyEditorOpen"
      class="strategy-editor-dialog"
      :title="strategyForm.id ? '编辑策略' : '新建策略'"
      width="min(860px, calc(100vw - 24px))"
      append-to-body
      destroy-on-close
    >
      <el-form label-position="top" class="strategy-editor-form" @submit.prevent="saveStrategyEditor">
        <div class="strategy-editor-basics">
          <el-form-item label="策略名称">
            <el-input v-model="strategyForm.name" maxlength="64" show-word-limit />
          </el-form-item>
          <el-form-item label="运行模式">
            <el-select v-model="strategyForm.runMode">
              <el-option label="实时" value="REALTIME" />
              <el-option label="收盘" value="CLOSE" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态" class="strategy-enabled-field">
            <el-switch v-model="strategyForm.enabled" active-text="启用" inactive-text="停用" />
          </el-form-item>
          <el-form-item label="策略说明" class="strategy-description-field">
            <el-input v-model="strategyForm.description" type="textarea" :rows="2" maxlength="512" />
          </el-form-item>
        </div>

        <div class="strategy-rule-editor-heading">
          <div>
            <h3>筛选规则</h3>
            <span>同一策略内全部按 AND 执行</span>
          </div>
          <el-button :icon="Plus" @click="addStrategyRule">添加规则</el-button>
        </div>

        <div class="strategy-rule-editor-list">
          <div
            v-for="(rule, index) in strategyForm.rules"
            :key="`${rule.ruleType}-${index}`"
            class="strategy-rule-editor-row"
          >
            <span class="rule-index">{{ index + 1 }}</span>
            <el-select v-model="rule.ruleType" class="rule-type-select" @change="onRuleTypeChange(rule)">
              <el-option
                v-for="catalog in RULE_CATALOG"
                :key="catalog.value"
                :label="catalog.label"
                :value="catalog.value"
              />
            </el-select>
            <el-select v-model="rule.operatorCode" class="rule-operator-select">
              <el-option
                v-for="operator in ruleOperators(rule)"
                :key="operator.value"
                :label="operator.label"
                :value="operator.value"
              />
            </el-select>
            <div class="rule-value-editor">
              <el-switch
                v-if="ruleCatalog(rule.ruleType).kind === 'boolean'"
                v-model="rule.boolValue"
                active-text="是"
                inactive-text="否"
              />
              <el-select v-else-if="ruleCatalog(rule.ruleType).kind === 'market'" v-model="rule.textValue">
                <el-option label="沪深主板" value="MAIN_BOARD" />
              </el-select>
              <el-input
                v-else-if="ruleCatalog(rule.ruleType).kind === 'time'"
                v-model="rule.textValue"
                maxlength="6"
                placeholder="如 103000"
              />
              <div v-else-if="rule.operatorCode === 'BETWEEN'" class="rule-range-editor">
                <el-input v-model="rule.minValue" inputmode="decimal" placeholder="最小值" />
                <span>至</span>
                <el-input v-model="rule.maxValue" inputmode="decimal" placeholder="最大值" />
              </div>
              <el-input-number
                v-else-if="ruleCatalog(rule.ruleType).kind === 'integer'"
                v-model="rule.intValue"
                :min="0"
                controls-position="right"
              />
              <el-input v-else v-model="rule.minValue" inputmode="decimal" placeholder="比较值" />
            </div>
            <label v-if="ruleCatalog(rule.ruleType).lookback" class="rule-lookback-field">
              <span>回看</span>
              <el-input-number v-model="rule.lookbackDays" :min="1" :max="250" controls-position="right" />
              <span>日</span>
            </label>
            <el-button
              class="rule-remove-button"
              text
              type="danger"
              :icon="Delete"
              aria-label="删除规则"
              title="删除规则"
              @click="removeStrategyRule(index)"
            />
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="strategyEditorOpen = false">取消</el-button>
        <el-button type="primary" :loading="strategyEditorSaving" @click="saveStrategyEditor">保存策略</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="strategyManageOpen"
      class="strategy-manager-dialog"
      title="策略排序"
      width="min(560px, calc(100vw - 24px))"
      append-to-body
    >
      <div class="strategy-sort-list">
        <div
          v-for="(strategy, index) in managedUserStrategies"
          :key="strategy.id"
          class="strategy-sort-row"
          :class="{ 'is-dragging': draggingStrategyId === strategy.id }"
          draggable="true"
          @dragstart="onStrategyDragStart(strategy.id)"
          @dragover.prevent
          @drop="onStrategyDrop(strategy.id)"
          @dragend="draggingStrategyId = null"
        >
          <el-icon class="strategy-drag-handle" title="拖拽排序"><Rank /></el-icon>
          <span class="strategy-sort-name">
            <b>{{ strategy.name }}</b>
            <small>{{ strategy.enabled ? '已启用' : '已停用' }}</small>
          </span>
          <span class="strategy-sort-actions">
            <el-button
              text
              :icon="ArrowUp"
              :disabled="index === 0"
              aria-label="上移"
              title="上移"
              @click="moveStrategy(index, -1)"
            />
            <el-button
              text
              :icon="ArrowDown"
              :disabled="index === managedUserStrategies.length - 1"
              aria-label="下移"
              title="下移"
              @click="moveStrategy(index, 1)"
            />
          </span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.eyebrow {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.04em;
  color: var(--accent);
  text-transform: uppercase;
}

.screener-page {
  min-height: calc(100vh - 48px);
}

.screener-mode-switch {
  display: flex;
  margin: 0 0 12px;
  border-bottom: 1px solid var(--line);
}

.screener-mode-switch :deep(.el-segmented) {
  width: 260px;
  margin-bottom: -1px;
  padding: 3px;
  border: 1px solid var(--line);
  border-bottom-color: var(--paper);
  border-radius: 7px 7px 0 0;
  background: var(--paper-deep);
}

.strategy-panel {
  margin-bottom: 14px;
  padding: 0 0 16px;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.strategy-toolbar,
.strategy-title-line,
.strategy-rule-editor-heading,
.strategy-sort-row {
  display: flex;
  align-items: center;
}

.strategy-toolbar,
.strategy-title-line,
.strategy-rule-editor-heading {
  justify-content: space-between;
  gap: 16px;
}

.strategy-toolbar {
  min-height: 64px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
}

.strategy-library-heading {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.strategy-library-heading > span,
.strategy-category {
  margin: 0;
  color: var(--accent);
  font-size: 11px;
  font-weight: 700;
}

.strategy-library-heading strong {
  color: var(--ink-soft);
  font-size: 14px;
  font-weight: 650;
}

.strategy-selector {
  display: grid;
  grid-template-columns: auto minmax(260px, 420px);
  align-items: center;
  gap: 10px;
  min-width: 0;
  color: var(--slate);
  font-size: 13px;
  font-weight: 650;
}

.strategy-selector :deep(.el-select) {
  width: 100%;
}

.strategy-actions,
.strategy-flags,
.strategy-sort-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.strategy-actions :deep(.el-button) {
  margin: 0;
}

.strategy-workspace {
  display: grid;
  grid-template-columns: minmax(220px, 280px) minmax(0, 1fr);
  min-width: 0;
}

.strategy-catalog {
  min-width: 0;
  padding: 16px 14px 4px 0;
  border-right: 1px solid var(--line);
}

.strategy-catalog-group + .strategy-catalog-group {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
}

.strategy-catalog-group h2 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 0 7px;
  color: var(--muted);
  font-size: 11px;
  font-weight: 700;
}

.strategy-catalog-group h2 small {
  font-size: 10px;
  font-variant-numeric: tabular-nums;
}

.strategy-catalog-group button {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 38px;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 52px;
  padding: 8px 9px 8px 11px;
  border: 0;
  border-top: 1px solid color-mix(in srgb, var(--line) 72%, transparent);
  border-left: 3px solid transparent;
  border-radius: 0 5px 5px 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.16s ease, border-color 0.16s ease;
}

.strategy-catalog-group button:last-child {
  border-bottom: 1px solid color-mix(in srgb, var(--line) 72%, transparent);
}

.strategy-catalog-group button:hover {
  background: var(--fill);
}

.strategy-catalog-group button:focus-visible {
  outline: 3px solid rgba(0, 113, 227, 0.18);
  outline-offset: -1px;
}

.strategy-catalog-group button.is-active {
  border-left-color: var(--accent);
  background: color-mix(in srgb, var(--accent) 8%, var(--paper));
}

.strategy-catalog-group button.is-active b {
  color: var(--ink);
}

.strategy-catalog-group button > span {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.strategy-catalog-group button b {
  overflow: hidden;
  color: var(--ink-soft);
  font-size: 13px;
  font-weight: 650;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.strategy-catalog-group button small {
  color: var(--muted);
  font-size: 10px;
  line-height: 1.35;
}

.strategy-catalog-group button em {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 38px;
  height: 22px;
  padding: 0 6px;
  border: 1px solid transparent;
  border-radius: 5px;
  font-size: 10px;
  font-weight: 650;
  font-style: normal;
  line-height: 1;
  white-space: nowrap;
}

.strategy-catalog-group button em.is-realtime {
  border-color: color-mix(in srgb, var(--accent) 20%, transparent);
  background: color-mix(in srgb, var(--accent) 7%, var(--paper));
  color: var(--accent);
}

.strategy-catalog-group button em.is-close {
  border-color: color-mix(in srgb, #b66a16 22%, transparent);
  background: color-mix(in srgb, #b66a16 7%, var(--paper));
  color: #9a5b16;
}

.strategy-definition {
  width: 100%;
  max-width: 1160px;
  min-width: 0;
  padding: 18px 0 2px 22px;
}

.strategy-title-line {
  align-items: flex-start;
}

.strategy-title-line h2 {
  margin: 0 0 3px;
  color: var(--ink);
  font-size: 20px;
  line-height: 1.35;
  letter-spacing: 0;
}

.strategy-title-line p,
.strategy-disclaimer {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.65;
}

.strategy-core-idea {
  display: grid;
  gap: 5px;
  margin-top: 16px;
  padding: 12px 14px;
  border-left: 3px solid var(--accent);
  border-radius: 0 5px 5px 0;
  background: color-mix(in srgb, var(--accent) 6%, var(--paper));
}

.strategy-core-idea span {
  color: var(--accent);
  font-size: 11px;
  font-weight: 700;
}

.strategy-core-idea p,
.strategy-guide-overview p {
  margin: 0;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.7;
}

.strategy-guide-overview,
.strategy-playbook-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 16px;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.strategy-guide-overview > section,
.strategy-playbook-grid > section {
  min-width: 0;
  padding: 13px 16px 13px 0;
}

.strategy-guide-overview > section + section,
.strategy-playbook-grid > section + section {
  padding-left: 16px;
  border-left: 1px solid var(--line);
}

.strategy-guide-overview h3,
.strategy-playbook-grid h3,
.strategy-rules-heading h3 {
  margin: 0 0 7px;
  color: var(--ink);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0;
}

.strategy-playbook-grid {
  margin-top: 0;
  border-top: 0;
}

.strategy-playbook-grid ol,
.strategy-playbook-grid ul {
  display: grid;
  gap: 7px;
  margin: 0;
  padding-left: 20px;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.6;
}

.strategy-risk-section h3,
.strategy-risk-section li::marker {
  color: #9a5d00;
}

.strategy-rules-section {
  margin-top: 18px;
}

.strategy-rules-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.strategy-rules-heading h3 {
  margin-bottom: 0;
}

.strategy-rules-heading span {
  color: var(--muted);
  font-size: 11px;
}

.strategy-disclaimer {
  margin-top: 10px;
  color: #8a5a14;
  font-size: 11px;
}

.strategy-rule-list {
  display: grid;
  margin: 8px 0 0;
  padding: 0;
  border-top: 1px solid var(--line);
  list-style: none;
}

.strategy-rule-list li {
  display: grid;
  grid-template-columns: minmax(120px, 0.55fr) minmax(0, 1.45fr);
  gap: 14px;
  padding: 8px 2px;
  border-bottom: 1px solid var(--line);
  font-size: 13px;
  line-height: 1.4;
}

.strategy-rule-list li span {
  color: var(--muted);
}

.strategy-rule-list li b {
  min-width: 0;
  color: var(--ink-soft);
  font-weight: 650;
  overflow-wrap: anywhere;
}

.strategy-empty {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 72px;
  margin-top: 14px;
  padding-top: 13px;
  border-top: 1px solid var(--line);
  color: var(--muted);
}

.strategy-data-status {
  margin-top: 14px;
  padding-top: 13px;
  border-top: 1px solid var(--line);
}

.strategy-status-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  border: 1px solid var(--line);
  border-radius: 6px;
  overflow: hidden;
}

.strategy-status-grid > span {
  display: grid;
  min-width: 0;
  gap: 3px;
  padding: 9px 10px;
  border-right: 1px solid var(--line);
  background: var(--glass);
}

.strategy-status-grid > span:last-child {
  border-right: 0;
}

.strategy-status-grid small {
  color: var(--muted);
  font-size: 11px;
}

.strategy-status-grid b {
  overflow: hidden;
  color: var(--ink-soft);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.strategy-issues {
  display: grid;
  gap: 4px;
  margin: 9px 0 0;
  padding: 0;
  color: #9a5d00;
  font-size: 12px;
  list-style: none;
}

.evidence-line {
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.45;
}

.strategy-editor-basics {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 140px 110px;
  gap: 0 12px;
}

.strategy-description-field {
  grid-column: 1 / -1;
}

.strategy-enabled-field :deep(.el-form-item__content) {
  min-height: 32px;
}

.strategy-rule-editor-heading {
  margin-top: 4px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
}

.strategy-rule-editor-heading h3 {
  margin: 0;
  color: var(--ink);
  font-size: 16px;
  letter-spacing: 0;
}

.strategy-rule-editor-heading span {
  color: var(--muted);
  font-size: 11px;
}

.strategy-rule-editor-list {
  display: grid;
  margin-top: 10px;
  border-top: 1px solid var(--line);
}

.strategy-rule-editor-row {
  display: grid;
  grid-template-columns: 24px minmax(150px, 1.15fr) 104px minmax(190px, 1.35fr) auto 34px;
  align-items: center;
  gap: 8px;
  padding: 9px 0;
  border-bottom: 1px solid var(--line);
}

.rule-index {
  color: var(--muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  text-align: center;
}

.rule-value-editor,
.rule-range-editor,
.rule-lookback-field {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.rule-value-editor > :deep(*) {
  min-width: 0;
}

.rule-range-editor :deep(.el-input) {
  min-width: 0;
}

.rule-range-editor > span,
.rule-lookback-field > span {
  flex: 0 0 auto;
  color: var(--muted);
  font-size: 11px;
}

.rule-lookback-field :deep(.el-input-number) {
  width: 92px;
}

.rule-remove-button {
  width: 32px;
  height: 32px;
  margin: 0;
}

.strategy-sort-list {
  display: grid;
  border-top: 1px solid var(--line);
}

.strategy-sort-row {
  gap: 10px;
  min-height: 52px;
  border-bottom: 1px solid var(--line);
}

.strategy-sort-row.is-dragging {
  opacity: 0.45;
}

.strategy-drag-handle {
  flex: 0 0 28px;
  cursor: grab;
  color: var(--muted);
}

.strategy-sort-name {
  display: grid;
  flex: 1;
  min-width: 0;
  gap: 2px;
}

.strategy-sort-name b {
  overflow: hidden;
  color: var(--ink);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.strategy-sort-name small {
  color: var(--muted);
  font-size: 11px;
}

.strategy-sort-actions :deep(.el-button) {
  width: 34px;
  height: 34px;
  margin: 0;
}

.meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin: 6px 0 0;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  font-size: 13px;
}

.chip.pool {
  background: color-mix(in srgb, #16a34a 14%, transparent);
}

.chip b {
  font-variant-numeric: tabular-nums;
}

.hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--muted, #888);
  max-width: 720px;
}

.muted {
  color: var(--muted, #888);
  font-size: 12px;
}

.filter-panel {
  margin: 4px 0 12px;
  padding: 12px 0 2px;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.filter-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}

.filter-heading h2 {
  margin: 0 0 3px;
  font-size: 18px;
  line-height: 1.35;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 0;
}

.form {
  margin-bottom: 0;
}

.screener-table {
  width: 100%;
}

.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.mobile-filter-surface,
.mobile-results-section {
  display: none;
}

@media (max-width: 820px) {
  .screener-page {
    --page-title-size: 24px;
    overflow-x: clip;
  }

  .screener-header {
    position: relative;
    display: grid;
    grid-template-columns: minmax(0, 1fr) 44px;
    gap: 10px;
    margin-bottom: 12px;
  }

  .screener-header > div:first-child {
    min-width: 0;
  }

  .screener-header > div:first-child > .eyebrow {
    display: none !important;
  }

  .screener-header h1::after {
    display: none;
  }

  .header-refresh-actions {
    width: 44px !important;
    align-self: start;
  }

  .screener-page .screener-header > .header-refresh-actions > :deep(.mobile-refresh-button) {
    width: 44px !important;
    min-width: 44px;
    min-height: 44px;
    margin: 0;
    padding: 0;
    border-radius: 8px;
  }

  .meta-line {
    flex-wrap: nowrap;
    gap: 6px;
    min-width: 0;
    margin-top: 6px;
  }

  .chip {
    flex: 0 0 auto;
    padding: 3px 8px;
    border-radius: 6px;
    font-size: 12px;
  }

  .meta-batch {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .hint {
    display: -webkit-box;
    margin-top: 5px;
    overflow: hidden;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    font-size: 11px;
    line-height: 1.45;
  }

  .mobile-filter-surface {
    display: block;
    margin: 0 -2px 14px;
    padding: 12px;
    border: 1px solid var(--line);
    border-radius: 8px;
    background: var(--glass-strong);
    box-shadow: var(--shadow-soft);
  }

  .mobile-filter-heading,
  .mobile-results-heading {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 10px;
  }

  .mobile-filter-heading {
    margin-bottom: 12px;
  }

  .mobile-filter-heading h2,
  .mobile-results-heading h2 {
    margin: 0 0 2px;
    color: var(--ink);
    font-family: var(--font-display);
    font-size: 18px;
    font-weight: 650;
    line-height: 1.35;
    letter-spacing: 0;
  }

  .mobile-filter-heading > div > span,
  .mobile-results-heading > div > span {
    color: var(--muted);
    font-size: 11px;
    line-height: 1.35;
  }

  .mobile-filter-count {
    flex: 0 0 auto;
    padding: 3px 7px;
    border-radius: 5px;
    background: color-mix(in srgb, var(--accent) 10%, transparent);
    color: var(--accent);
    font-size: 11px;
    font-weight: 650;
  }

  .mobile-filter-form,
  .mobile-advanced-filters,
  .mobile-filter-group,
  .mobile-field {
    display: grid;
  }

  .mobile-filter-form {
    gap: 10px;
  }

  .mobile-field {
    min-width: 0;
    gap: 5px;
    color: var(--slate);
    font-size: 12px;
    font-weight: 600;
  }

  .mobile-field :deep(.el-input),
  .mobile-field :deep(.el-input__wrapper) {
    width: 100%;
    min-width: 0;
  }

  .mobile-field :deep(.el-input__wrapper) {
    min-height: 44px;
    border-radius: 7px;
    background: var(--paper);
    box-shadow: 0 0 0 1px var(--line) inset;
  }

  .mobile-field :deep(.el-input__wrapper.is-focus) {
    box-shadow: 0 0 0 1px var(--accent) inset;
  }

  .mobile-scope-field {
    min-width: 0;
    margin: 0;
    padding: 0;
    border: 0;
  }

  .mobile-scope-field legend {
    margin-bottom: 5px;
    padding: 0;
    color: var(--slate);
    font-size: 12px;
    font-weight: 600;
  }

  .mobile-segmented {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 3px;
    padding: 3px;
    border: 1px solid var(--line);
    border-radius: 8px;
    background: var(--paper-deep);
  }

  .mobile-segmented button {
    min-width: 0;
    min-height: 44px;
    padding: 0 10px;
    border: 0;
    border-radius: 6px;
    background: transparent;
    color: var(--slate);
    font: inherit;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    touch-action: manipulation;
  }

  .mobile-segmented button.is-active {
    background: var(--glass-strong);
    color: var(--accent);
    box-shadow: 0 1px 4px rgba(20, 32, 51, 0.1);
  }

  .advanced-filter-toggle {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    min-height: 44px;
    padding: 0 2px;
    border: 0;
    border-top: 1px solid var(--line);
    border-bottom: 1px solid var(--line);
    background: transparent;
    color: var(--ink-soft);
    font: inherit;
    font-size: 13px;
    font-weight: 650;
    cursor: pointer;
    touch-action: manipulation;
  }

  .advanced-filter-toggle small {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 18px;
    height: 18px;
    margin-left: 4px;
    padding: 0 5px;
    border-radius: 9px;
    background: var(--accent);
    color: #fff;
    font-size: 10px;
  }

  .advanced-filter-toggle .el-icon {
    transition: transform 0.2s ease;
  }

  .advanced-filter-toggle .el-icon.is-open {
    transform: rotate(180deg);
  }

  .mobile-advanced-filters {
    gap: 14px;
    padding: 4px 0 2px;
  }

  .mobile-filter-group {
    gap: 8px;
  }

  .mobile-filter-group + .mobile-filter-group {
    padding-top: 12px;
    border-top: 1px solid var(--line);
  }

  .mobile-filter-group h3 {
    margin: 0;
    color: var(--ink-soft);
    font-size: 13px;
    font-weight: 700;
    letter-spacing: 0;
  }

  .mobile-field-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px 8px;
  }

  .mobile-field-wide {
    grid-column: 1 / -1;
  }

  .mobile-filter-options {
    grid-template-columns: minmax(88px, 0.7fr) minmax(0, 1.3fr);
    align-items: end;
    column-gap: 12px;
  }

  .mobile-filter-options h3 {
    grid-column: 1 / -1;
  }

  .mobile-risk-options {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 4px;
    min-width: 0;
  }

  .mobile-risk-options :deep(.el-checkbox) {
    min-width: 0;
    min-height: 44px;
    margin: 0;
  }

  .mobile-risk-options :deep(.el-checkbox__label) {
    min-width: 0;
    padding-left: 4px;
    overflow: hidden;
    font-size: 11px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-filter-actions {
    display: grid;
    grid-template-columns: minmax(0, 1.55fr) minmax(96px, 0.8fr);
    gap: 8px;
  }

  .mobile-filter-actions :deep(.el-button) {
    width: 100%;
    min-height: 44px;
    margin: 0;
    border-radius: 8px;
  }

  .mobile-results-section {
    display: block;
  }

  .mobile-results-heading {
    align-items: center;
    margin-bottom: 8px;
  }

  .mobile-results-heading :deep(.el-button) {
    min-height: 40px;
    margin: 0;
    border-radius: 7px;
  }

  .screener-mobile-list {
    display: grid;
    gap: 8px;
    min-height: 88px;
  }

  .screener-mobile-card {
    display: grid;
    gap: 11px;
    width: 100%;
    min-width: 0;
    padding: 12px;
    border: 1px solid var(--line);
    border-radius: 8px;
    background: var(--glass-strong);
    color: inherit;
    box-shadow: 0 3px 12px rgba(20, 32, 51, 0.04);
    font: inherit;
    text-align: left;
    cursor: pointer;
    touch-action: manipulation;
  }

  .screener-mobile-card:active {
    background: var(--fill);
  }

  .screener-mobile-card:focus-visible,
  .mobile-segmented button:focus-visible,
  .advanced-filter-toggle:focus-visible {
    outline: 3px solid rgba(0, 113, 227, 0.2);
    outline-offset: 1px;
  }

  .mobile-stock-heading {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 10px;
    min-width: 0;
  }

  .mobile-stock-identity,
  .mobile-stock-quote {
    display: flex;
    align-items: center;
    min-width: 0;
  }

  .mobile-stock-identity {
    flex: 1 1 auto;
    flex-wrap: wrap;
    gap: 5px;
  }

  .mobile-stock-identity strong {
    min-width: 0;
    max-width: 100%;
    overflow: hidden;
    color: var(--ink);
    font-size: 15px;
    font-weight: 700;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-stock-identity small {
    flex-basis: 100%;
    color: var(--muted);
    font-size: 11px;
    font-variant-numeric: tabular-nums;
  }

  .universe-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    border-radius: 4px;
    background: rgba(42, 157, 143, 0.1);
    color: #16775d;
    font-size: 10px;
    font-weight: 750;
  }

  .mobile-stock-quote {
    flex: 0 0 auto;
    align-items: flex-end;
    flex-direction: column;
    gap: 2px;
  }

  .mobile-stock-quote strong {
    font-size: 15px;
    font-weight: 750;
  }

  .mobile-stock-quote small {
    color: var(--slate);
    font-size: 12px;
    font-variant-numeric: tabular-nums;
  }

  .mobile-stock-metrics {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 0;
    min-width: 0;
    padding-top: 9px;
    border-top: 1px solid var(--line);
  }

  .mobile-stock-metrics > span {
    display: grid;
    min-width: 0;
    gap: 3px;
    padding: 0 7px;
    border-right: 1px solid var(--line);
  }

  .mobile-stock-metrics > span:first-child,
  .mobile-stock-metrics > span:nth-child(5) {
    padding-left: 0;
  }

  .mobile-stock-metrics > span:nth-child(4n) {
    padding-right: 0;
    border-right: 0;
  }

  .mobile-stock-metrics.is-screening > span:nth-child(n + 5) {
    margin-top: 9px;
    padding-top: 9px;
    border-top: 1px solid var(--line);
  }

  .mobile-stock-metrics small {
    color: var(--muted);
    font-size: 10px;
    line-height: 1.2;
  }

  .mobile-stock-metrics b {
    min-width: 0;
    overflow: hidden;
    color: var(--ink-soft);
    font-size: 12px;
    font-variant-numeric: tabular-nums;
    font-weight: 650;
    line-height: 1.25;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-stock-metrics b.up {
    color: var(--up);
  }

  .mobile-stock-metrics b.down {
    color: var(--down);
  }

  .mobile-empty-state {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 120px;
    padding: 20px;
    border: 1px dashed var(--line-strong);
    border-radius: 8px;
    color: var(--muted);
    font-size: 13px;
  }

  .mobile-pager {
    display: grid;
    grid-template-columns: minmax(76px, 1fr) minmax(100px, 1.25fr) minmax(76px, 1fr);
    align-items: center;
    gap: 8px;
    margin-top: 12px;
  }

  .mobile-pager-button {
    width: 100%;
    min-height: 44px;
    margin: 0 !important;
    border-radius: 8px;
  }

  .mobile-pager > span {
    display: grid;
    gap: 2px;
    min-width: 0;
    text-align: center;
  }

  .mobile-pager b,
  .mobile-pager small {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-pager b {
    color: var(--ink-soft);
    font-size: 12px;
    font-variant-numeric: tabular-nums;
  }

  .mobile-pager small {
    color: var(--muted);
    font-size: 10px;
    font-variant-numeric: tabular-nums;
  }

  .mobile-batch-results {
    display: grid;
    gap: 7px;
    margin-top: 18px;
  }

  .mobile-batch-results h3 {
    margin-bottom: 1px;
  }

  .mobile-batch-results > button {
    display: grid;
    grid-template-columns: 24px minmax(0, 1fr) max-content;
    align-items: center;
    gap: 9px;
    min-width: 0;
    min-height: 56px;
    padding: 8px 10px;
    border: 1px solid var(--line);
    border-radius: 8px;
    background: var(--glass-strong);
    color: inherit;
    font: inherit;
    text-align: left;
    cursor: pointer;
  }

  .mobile-batch-results > button:disabled {
    cursor: default;
    opacity: 0.65;
  }

  .mobile-batch-rank {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: 5px;
    background: var(--fill);
    color: var(--slate);
    font-size: 11px;
    font-weight: 750;
  }

  .mobile-batch-stock,
  .mobile-batch-return {
    display: grid;
    min-width: 0;
    gap: 2px;
  }

  .mobile-batch-stock b,
  .mobile-batch-return b {
    font-size: 13px;
    font-variant-numeric: tabular-nums;
  }

  .mobile-batch-stock small,
  .mobile-batch-return small {
    overflow: hidden;
    color: var(--muted);
    font-size: 10px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-batch-return {
    justify-items: end;
    text-align: right;
  }

  .screener-mode-switch {
    margin-bottom: 12px;
  }

  .screener-mode-switch :deep(.el-segmented) {
    width: 100%;
    min-height: 44px;
  }

  .strategy-panel {
    margin: 0 0 14px;
    padding: 0 0 14px;
  }

  .strategy-toolbar {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    justify-content: stretch;
    gap: 10px;
    min-height: 0;
    padding: 12px 0;
  }

  .strategy-toolbar > * {
    min-width: 0;
    width: 100%;
  }

  .strategy-selector {
    grid-template-columns: 1fr;
    gap: 5px;
    width: 100%;
  }

  .mobile-strategy-select :deep(.el-select__wrapper) {
    min-height: 44px;
    border-radius: 8px;
  }

  .strategy-actions {
    display: grid;
    grid-template-columns: minmax(0, 1.45fr) minmax(44px, 0.75fr);
    gap: 8px;
  }

  .strategy-actions :deep(.el-button),
  .strategy-actions :deep(.el-dropdown),
  .strategy-actions :deep(.el-dropdown .el-button) {
    width: 100%;
    min-height: 44px;
    margin: 0;
  }

  .strategy-workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  .strategy-definition {
    max-width: 100%;
    padding: 14px 0 0;
    overflow-wrap: anywhere;
  }

  .strategy-title-line {
    display: grid;
    gap: 8px;
  }

  .strategy-title-line h2 {
    font-size: 17px;
  }

  .strategy-flags {
    flex-wrap: wrap;
  }

  .strategy-core-idea {
    margin-top: 13px;
    padding: 11px 12px;
  }

  .strategy-guide-overview,
  .strategy-playbook-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .strategy-guide-overview > section,
  .strategy-playbook-grid > section {
    padding: 12px 0;
  }

  .strategy-guide-overview > section + section,
  .strategy-playbook-grid > section + section {
    padding-left: 0;
    border-top: 1px solid var(--line);
    border-left: 0;
  }

  .strategy-playbook-grid > section:first-child {
    padding-top: 12px;
  }

  .strategy-rules-heading {
    display: grid;
    gap: 3px;
  }

  .strategy-rule-list li {
    grid-template-columns: 1fr;
    gap: 2px;
    padding: 8px 0;
  }

  .strategy-status-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .strategy-status-grid > span {
    border-right: 1px solid var(--line);
    border-bottom: 1px solid var(--line);
  }

  .strategy-status-grid > span:nth-child(2n) {
    border-right: 0;
  }

  .strategy-status-grid > span:nth-last-child(-n + 2) {
    border-bottom: 0;
  }

  .mobile-evidence-line {
    display: -webkit-box;
    margin-top: 8px;
    padding-top: 8px;
    overflow: hidden;
    border-top: 1px solid var(--line);
    color: var(--ink-soft);
    font-size: 11px;
    line-height: 1.45;
    text-align: left;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }

  .strategy-editor-basics {
    grid-template-columns: minmax(0, 1fr) minmax(110px, 0.6fr);
  }

  .strategy-enabled-field {
    grid-column: 1 / -1;
  }

  .strategy-rule-editor-row {
    grid-template-columns: 24px minmax(0, 1fr) 34px;
    gap: 8px;
  }

  .rule-index {
    grid-row: 1;
  }

  .rule-type-select {
    grid-column: 2;
  }

  .rule-operator-select,
  .rule-value-editor,
  .rule-lookback-field {
    grid-column: 2;
  }

  .rule-remove-button {
    grid-column: 3;
    grid-row: 1;
  }

  .rule-range-editor {
    width: 100%;
  }

  .rule-lookback-field {
    justify-content: flex-start;
  }
}

@media (max-width: 360px) {
  .mobile-filter-surface {
    padding: 10px;
  }

  .mobile-risk-options :deep(.el-checkbox__label) {
    font-size: 10px;
  }

  .strategy-actions {
    grid-template-columns: minmax(0, 1fr) 44px;
  }

  .strategy-action-label {
    display: none;
  }
}
</style>

<style>
@media (max-width: 820px) {
  .screener-strategy-popper .el-select-dropdown__item {
    min-height: 44px;
    line-height: 44px;
  }
}
</style>
