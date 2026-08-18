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
    :title="title"
    width="480px"
    destroy-on-close
    append-to-body
    align-center
    class="holding-trade-dialog"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-width="88px">
      <el-form-item label="方向" required>
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
      <el-form-item label="成交数量" required>
        <div class="trade-quantity-field">
          <el-input-number
            v-model="form.quantity"
            :min="1"
            :max="form.side === 'SELL' ? currentQuantity : undefined"
            :step="100"
            controls-position="right"
          />
          <el-button v-if="form.side === 'SELL'" text type="primary" @click="form.quantity = currentQuantity">
            全部卖出
          </el-button>
        </div>
      </el-form-item>
      <el-form-item label="成交价" required>
        <el-input-number
          v-model="form.tradePrice"
          :min="0.0001"
          :precision="4"
          :step="0.1"
          controls-position="right"
          style="width: 100%"
        />
      </el-form-item>
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
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button :type="form.side === 'SELL' ? 'danger' : 'primary'" :loading="loading" @click="submitTrade">
        确认{{ form.side === 'SELL' ? '卖出' : '买入' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.trade-side-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  width: 100%;
}

.trade-side-switch :deep(.el-radio-button),
.trade-side-switch :deep(.el-radio-button__inner) {
  width: 100%;
}

.trade-quantity-field {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  width: 100%;
}

.trade-quantity-field :deep(.el-input-number) {
  width: 100%;
}

@media (max-width: 560px) {
  .trade-quantity-field {
    grid-template-columns: 1fr;
  }

  .trade-quantity-field :deep(.el-button) {
    min-height: 36px;
  }
}
</style>
