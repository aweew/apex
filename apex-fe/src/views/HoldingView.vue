<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listHoldings, refreshHoldingQuotes, removeHolding, saveHolding } from '../api/holding'

const router = useRouter()
const loading = ref(false)
const refreshing = ref(false)
const rows = ref([])
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive({
  id: null,
  code: '',
  name: '',
  quantity: 100,
  costPrice: '',
  stopLoss: '',
  takeProfit: '',
  note: '',
})

async function load() {
  loading.value = true
  try {
    const res = await listHoldings()
    rows.value = res.data || []
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function onRefreshQuotes(forceAll = false) {
  refreshing.value = true
  try {
    const res = await refreshHoldingQuotes(!forceAll)
    rows.value = res.data?.holdings || []
    ElMessage.success(res.data?.message || '行情已刷新')
  } catch (e) {
    ElMessage.error(e.message || '刷新行情失败')
  } finally {
    refreshing.value = false
  }
}

function openCreate() {
  form.id = null
  form.code = ''
  form.name = ''
  form.quantity = 100
  form.costPrice = ''
  form.stopLoss = ''
  form.takeProfit = ''
  form.note = ''
  dialogVisible.value = true
}

function openEdit(row) {
  form.id = row.id
  form.code = row.code || ''
  form.name = row.name || ''
  form.quantity = row.quantity ?? 0
  form.costPrice = row.costPrice ?? ''
  form.stopLoss = row.stopLoss ?? ''
  form.takeProfit = row.takeProfit ?? ''
  form.note = row.note || ''
  dialogVisible.value = true
}

async function onSave() {
  if (!String(form.code || '').trim()) {
    ElMessage.warning('请填写证券代码')
    return
  }
  saving.value = true
  try {
    await saveHolding({
      id: form.id,
      code: String(form.code).trim(),
      name: String(form.name || '').trim() || null,
      quantity: Number(form.quantity || 0),
      costPrice: form.costPrice === '' ? null : Number(form.costPrice),
      stopLoss: form.stopLoss === '' ? null : Number(form.stopLoss),
      takeProfit: form.takeProfit === '' ? null : Number(form.takeProfit),
      note: String(form.note || '').trim() || null,
    })
    ElMessage.success(form.id ? '已更新' : '已添加')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onRemove(row) {
  try {
    await ElMessageBox.confirm(`确认删除持仓 ${row.code} ${row.name || ''}？`, '删除持仓', {
      type: 'warning',
    })
    await removeHolding(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

function fmtPct(v) {
  if (v == null) return '-'
  return (Number(v) * 100).toFixed(2) + '%'
}

onMounted(async () => {
  await load()
  const missing = rows.value.some((r) => r.marketPrice == null)
  if (missing) {
    await onRefreshQuotes(false)
  }
})
</script>

<template>
  <div class="page" v-loading="loading">
    <header class="header">
      <div>
        <h1>我的持仓</h1>
        <p>手动维护持仓；现价/盈亏来自行情刷新（支持 A 股与港股通）。智能决策卖出/持有读这里</p>
      </div>
      <div class="actions">
        <el-button type="primary" @click="openCreate">添加持仓</el-button>
        <el-button type="success" :loading="refreshing" @click="onRefreshQuotes(false)">刷新行情</el-button>
        <el-button @click="load">刷新列表</el-button>
        <el-button @click="router.push('/decision')">智能决策</el-button>
      </div>
    </header>

    <el-empty v-if="!loading && !rows.length" description="还没有持仓，点「添加持仓」开始录入">
      <el-button type="primary" @click="openCreate">添加持仓</el-button>
    </el-empty>

    <el-table v-else :data="rows" size="small" stripe>
      <el-table-column prop="code" label="代码" width="100" fixed sortable>
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" width="120" sortable />
      <el-table-column prop="quantity" label="数量" width="90" sortable />
      <el-table-column prop="costPrice" label="成本" width="100" sortable />
      <el-table-column prop="marketPrice" label="现价" width="100" sortable />
      <el-table-column prop="marketValue" label="市值" width="110" sortable />
      <el-table-column prop="pnl" label="浮盈亏" width="100" sortable>
        <template #default="{ row }">
          <span :class="Number(row.pnl) >= 0 ? 'up' : 'down'">{{ row.pnl ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="pnlPct" label="盈亏%" width="90" sortable>
        <template #default="{ row }">
          <span :class="Number(row.pnlPct) >= 0 ? 'up' : 'down'">{{ fmtPct(row.pnlPct) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="stopLoss" label="止损" width="100" sortable />
      <el-table-column prop="takeProfit" label="止盈" width="100" sortable />
      <el-table-column prop="note" label="备注" min-width="140" show-overflow-tooltip sortable />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onRemove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑持仓' : '添加持仓'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="代码" required>
          <el-input v-model="form.code" placeholder="如 600519" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="可空，自动补全" />
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="form.quantity" :min="0" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="成本价">
          <el-input v-model="form.costPrice" placeholder="可选" />
        </el-form-item>
        <el-form-item label="止损">
          <el-input v-model="form.stopLoss" placeholder="可选" />
        </el-form-item>
        <el-form-item label="止盈">
          <el-input v-model="form.takeProfit" placeholder="可选" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.note" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
