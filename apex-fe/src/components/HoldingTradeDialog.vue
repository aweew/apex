<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { searchStock } from '../api/stock'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  holding: {
    type: Object,
    default: null,
  },
  initialSide: {
    type: String,
    default: 'BUY',
  },
  loading: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['update:modelValue', 'submit'])
const searchLoading = ref(false)
const searchOptions = ref([])
const form = reactive({
  code: '',
  name: '',
  side: 'BUY',
  quantity: 100,
  tradePrice: '',
  tradeTime: '',
})

const title = computed(() => (form.side === 'SELL' ? '卖出持仓' : '买入持仓'))
const currentQuantity = computed(() => Number(props.holding?.quantity || 0))
const estimatedAmount = computed(() => {
  const quantity = Number(form.quantity)
  const tradePrice = Number(form.tradePrice)
  if (!Number.isFinite(quantity) || !Number.isFinite(tradePrice) || quantity <= 0 || tradePrice <= 0) {
    return '--'
  }
  return (quantity * tradePrice).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
})

function currentDateTime() {
  const now = new Date()
  const pad = (value) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}:00`
}

function resetForm() {
  const side = props.initialSide === 'SELL' && props.holding ? 'SELL' : 'BUY'
  form.code = props.holding?.code || ''
  form.name = props.holding?.name || ''
  form.side = side
  form.quantity = side === 'SELL' ? Math.max(1, currentQuantity.value) : 100
  form.tradePrice = props.holding?.marketPrice ?? ''
  form.tradeTime = currentDateTime()
  searchOptions.value = props.holding?.code
    ? [{ code: props.holding.code, name: props.holding.name || props.holding.code }]
    : []
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) resetForm()
  },
)

watch(
  () => form.side,
  (side) => {
    if (side === 'SELL') {
      form.quantity = Math.max(1, currentQuantity.value)
    } else if (!Number.isFinite(Number(form.quantity)) || Number(form.quantity) <= 0) {
      form.quantity = 100
    }
  },
)

async function onSearchStock(query) {
  const keyword = String(query || '').trim()
  if (!keyword) {
    searchOptions.value = []
    return
  }
  searchLoading.value = true
  try {
    const response = await searchStock(keyword)
    searchOptions.value = response?.data || []
  } catch {
    searchOptions.value = []
  } finally {
    searchLoading.value = false
  }
}

function onPickStock(code) {
  const stock = searchOptions.value.find((item) => item.code === code)
  if (stock) form.name = stock.name || ''
}

function submitTrade() {
  if (!String(form.code || '').trim()) {
    ElMessage.warning('请选择证券')
    return
  }
  const quantity = Number(form.quantity)
  if (!Number.isInteger(quantity) || quantity <= 0) {
    ElMessage.warning('成交数量必须为正整数')
    return
  }
  if (form.side === 'SELL' && quantity > currentQuantity.value) {
    ElMessage.warning('卖出数量不能超过当前持仓')
    return
  }
  const tradePrice = Number(form.tradePrice)
  if (!Number.isFinite(tradePrice) || tradePrice <= 0) {
    ElMessage.warning('成交价必须大于0')
    return
  }
  emit('submit', {
    holdingId: props.holding?.id || null,
    code: String(form.code).trim(),
    name: String(form.name || '').trim() || null,
    side: form.side,
    quantity,
    tradePrice,
    tradeTime: form.tradeTime || null,
  })
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    width="520px"
    destroy-on-close
    append-to-body
    align-center
    class="holding-trade-dialog"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="trade-dialog-header">
        <div>
          <p class="trade-dialog-eyebrow">交易录入</p>
          <h2>{{ title }}</h2>
        </div>
        <div v-if="holding" class="trade-security-summary">
          <strong>{{ form.name || form.code }}</strong>
          <span>{{ form.code }}</span>
        </div>
      </div>
    </template>

    <el-form class="trade-form" label-position="top">
      <el-form-item label="交易方向" required class="trade-side-item">
        <el-radio-group v-model="form.side" :disabled="!holding" class="trade-side-switch">
          <el-radio-button value="BUY">买入</el-radio-button>
          <el-radio-button value="SELL">卖出</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="证券" required>
        <el-select
          v-if="!holding"
          v-model="form.code"
          filterable
          remote
          clearable
          :remote-method="onSearchStock"
          :loading="searchLoading"
          placeholder="输入代码或名称"
          style="width: 100%"
          @change="onPickStock"
        >
          <el-option
            v-for="stock in searchOptions"
            :key="stock.code"
            :label="`${stock.code} ${stock.name || ''}`"
            :value="stock.code"
          />
        </el-select>
        <el-input v-else :model-value="`${form.code} ${form.name}`" disabled />
      </el-form-item>

      <div class="trade-input-grid">
        <el-form-item label="成交数量" required>
          <el-input-number
            v-model="form.quantity"
            :min="1"
            :max="form.side === 'SELL' ? currentQuantity : undefined"
            :step="100"
            controls-position="right"
          />
          <div v-if="form.side === 'SELL'" class="trade-quantity-meta">
            <span>可卖 {{ currentQuantity.toLocaleString('zh-CN') }} 股</span>
            <el-button link type="primary" @click="form.quantity = currentQuantity">全部卖出</el-button>
          </div>
        </el-form-item>
        <el-form-item label="成交价" required>
          <el-input-number
            v-model="form.tradePrice"
            :min="0.0001"
            :precision="4"
            :step="0.1"
            controls-position="right"
          />
        </el-form-item>
      </div>

      <el-form-item label="成交时间">
        <el-date-picker
          v-model="form.tradeTime"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          format="YYYY-MM-DD HH:mm"
          style="width: 100%"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="trade-dialog-footer">
        <div class="trade-amount">
          <span>预计成交额</span>
          <strong>¥ {{ estimatedAmount }}</strong>
        </div>
        <div class="trade-footer-actions">
          <el-button @click="emit('update:modelValue', false)">取消</el-button>
          <el-button :type="form.side === 'SELL' ? 'danger' : 'primary'" :loading="loading" @click="submitTrade">
            确认{{ form.side === 'SELL' ? '卖出' : '买入' }}
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.holding-trade-dialog :deep(.el-dialog) {
  overflow: hidden;
  border: 1px solid var(--glass-border);
  border-radius: 8px;
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.22);
}

.holding-trade-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--line);
}

.holding-trade-dialog :deep(.el-dialog__headerbtn) {
  top: 22px;
  right: 20px;
}

.holding-trade-dialog :deep(.el-dialog__body) {
  padding: 20px 24px 8px;
}

.holding-trade-dialog :deep(.el-dialog__footer) {
  padding: 18px 24px 22px;
  border-top: 1px solid var(--line);
  background: var(--glass-tint);
}

.trade-dialog-header,
.trade-dialog-footer,
.trade-footer-actions,
.trade-quantity-meta {
  display: flex;
  align-items: center;
}

.trade-dialog-header,
.trade-dialog-footer {
  justify-content: space-between;
  gap: 16px;
}

.trade-dialog-eyebrow {
  margin: 0 0 5px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 600;
}

.trade-dialog-header h2 {
  margin: 0;
  color: var(--ink);
  font-size: 20px;
  font-weight: 700;
  line-height: 1.25;
}

.trade-security-summary {
  min-width: 0;
  padding-right: 32px;
  color: var(--ink-soft);
  text-align: right;
}

.trade-security-summary strong,
.trade-security-summary span {
  display: block;
}

.trade-security-summary strong {
  overflow: hidden;
  font-size: 14px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trade-security-summary span {
  margin-top: 3px;
  color: var(--muted);
  font-size: 12px;
}

.trade-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.trade-form :deep(.el-form-item__label) {
  padding-bottom: 7px;
  color: var(--slate);
  font-size: 13px;
  font-weight: 650;
  line-height: 1.2;
}

.trade-form :deep(.el-input-number),
.trade-form :deep(.el-date-editor),
.trade-form :deep(.el-select) {
  width: 100%;
}

.trade-form :deep(.el-input__wrapper),
.trade-form :deep(.el-input-number .el-input__wrapper) {
  min-height: 42px;
  box-shadow: 0 0 0 1px var(--line-strong) inset;
}

.trade-form :deep(.el-input__wrapper.is-focus),
.trade-form :deep(.el-input-number .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--accent) inset;
}

.trade-side-item {
  margin-bottom: 20px !important;
}

.trade-side-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  width: 100%;
}

.trade-side-switch :deep(.el-radio-button),
.trade-side-switch :deep(.el-radio-button__inner) {
  width: 100%;
  min-height: 40px;
  padding: 10px 14px;
  font-weight: 650;
}

.trade-input-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.trade-input-grid :deep(.el-form-item) {
  min-width: 0;
}

.trade-quantity-meta {
  justify-content: space-between;
  min-height: 24px;
  margin-top: 5px;
  color: var(--muted);
  font-size: 12px;
}

.trade-quantity-meta :deep(.el-button) {
  height: 24px;
  padding: 0;
  font-size: 12px;
}

.trade-amount span,
.trade-amount strong {
  display: block;
}

.trade-amount span {
  color: var(--muted);
  font-size: 12px;
}

.trade-amount strong {
  margin-top: 3px;
  color: var(--ink);
  font-size: 18px;
  font-variant-numeric: tabular-nums;
  line-height: 1.15;
}

.trade-footer-actions {
  flex: 0 0 auto;
  gap: 8px;
}

.trade-footer-actions :deep(.el-button) {
  min-width: 82px;
  min-height: 38px;
  margin-left: 0;
}

@media (max-width: 560px) {
  .holding-trade-dialog :deep(.el-dialog) {
    width: calc(100% - 24px) !important;
  }

  .holding-trade-dialog :deep(.el-dialog__header),
  .holding-trade-dialog :deep(.el-dialog__body) {
    padding-right: 18px;
    padding-left: 18px;
  }

  .holding-trade-dialog :deep(.el-dialog__footer) {
    padding: 16px 18px 18px;
  }

  .trade-input-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .trade-dialog-footer {
    align-items: flex-end;
  }

  .trade-amount strong {
    font-size: 16px;
  }

  .trade-footer-actions :deep(.el-button) {
    min-width: 70px;
  }
}
</style>
