<script setup>
import { computed, ref, watch } from 'vue'
import { Delete, Picture, Plus, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { importPortfolioHoldings, recognizePortfolioImage } from '../api/portfolio'
import {
  applyImportFailures,
  buildImportText,
  clearRecognitionErrorForField,
  createImportRow,
  createImportRowsFromImagePreview,
  parsePortfolioImportText,
  validateImportRows,
} from '../utils/portfolioImport.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  portfolioId: { type: [Number, String], default: null },
  portfolioName: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue', 'imported'])
const importDraft = ref('')
const importRows = ref([])
const importing = ref(false)
const recognizing = ref(false)
const imageInput = ref(null)
const imageName = ref('')
const recognitionWarnings = ref([])

const successCount = computed(() => importRows.value.filter((row) => row.status === 'success').length)
const invalidCount = computed(() => importRows.value.filter((row) => row.error || row.recognitionError).length)
const pendingRows = computed(() => importRows.value.filter((row) => row.status !== 'success'
  && !row.error && !row.recognitionError))
const retrying = computed(() => importRows.value.some((row) => row.status === 'error'))
const busy = computed(() => importing.value || recognizing.value)
const canSubmit = computed(() => !busy.value && Boolean(props.portfolioId)
  && pendingRows.value.length > 0 && invalidCount.value === 0)

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    importDraft.value = ''
    importRows.value = []
    importing.value = false
    recognizing.value = false
    imageName.value = ''
    recognitionWarnings.value = []
  },
)

function parseDraft() {
  const parsedRows = parsePortfolioImportText(importDraft.value)
  if (!parsedRows.length) {
    ElMessage.warning('请先粘贴持仓数据')
    return
  }
  importRows.value = parsedRows
  imageName.value = ''
  recognitionWarnings.value = []
}

function onPaste(event) {
  if (busy.value || clipboardImage(event)) return
  window.setTimeout(parseDraft, 0)
}

function updateDialogVisible(visible) {
  if (busy.value && !visible) return
  emit('update:modelValue', visible)
}

function addRow() {
  importRows.value = validateImportRows([...importRows.value, createImportRow()])
}

function removeRow(rowId) {
  importRows.value = validateImportRows(importRows.value.filter((row) => row.id !== rowId))
}

function updateRow(rowId, field, value) {
  importRows.value = validateImportRows(importRows.value.map((row) => {
    if (row.id !== rowId) return row
    return {
      ...row,
      [field]: value,
      status: 'ready',
      serverError: '',
      recognitionError: clearRecognitionErrorForField(row.recognitionError, field),
    }
  }))
}

function clearImport() {
  importDraft.value = ''
  importRows.value = []
  imageName.value = ''
  recognitionWarnings.value = []
}

function clipboardImage(event) {
  const files = [...(event?.clipboardData?.files || [])]
  return files.find((file) => file.type?.startsWith('image/')) || null
}

function onDialogPaste(event) {
  const file = clipboardImage(event)
  if (!file || busy.value) return
  event.preventDefault()
  recognizeImage(file)
}

function openImagePicker() {
  if (busy.value) return
  if (imageInput.value) imageInput.value.value = ''
  imageInput.value?.click()
}

function onImageChange(event) {
  const file = event.target.files?.[0]
  if (file) recognizeImage(file)
}

function onImageDrop(event) {
  if (busy.value) return
  const file = [...(event.dataTransfer?.files || [])].find((item) => item.type?.startsWith('image/'))
  if (!file) {
    ElMessage.warning('请拖入 PNG、JPEG 或 WebP 截图')
    return
  }
  recognizeImage(file)
}

async function recognizeImage(file) {
  if (busy.value) return
  const supportedTypes = ['image/png', 'image/jpeg', 'image/webp']
  if (!supportedTypes.includes(file.type)) {
    ElMessage.warning('仅支持 PNG、JPEG 或 WebP 截图')
    return
  }
  if (file.size > 8 * 1024 * 1024) {
    ElMessage.warning('截图不能超过 8 MB')
    return
  }
  if (!props.portfolioId) {
    ElMessage.warning('请先选择目标组合')
    return
  }

  recognizing.value = true
  imageName.value = file.name || '剪贴板截图'
  importDraft.value = ''
  importRows.value = []
  recognitionWarnings.value = []
  try {
    const response = await recognizePortfolioImage(props.portfolioId, file)
    const preview = response?.data || {}
    const rows = createImportRowsFromImagePreview(preview)
    if (!rows.length) {
      ElMessage.warning('截图中未识别到持仓')
      return
    }
    importRows.value = rows
    recognitionWarnings.value = Array.isArray(preview.warnings) ? preview.warnings : []
    ElMessage.success(`已识别 ${rows.length} 条持仓，请核对后确认导入`)
  } catch (error) {
    ElMessage.error(error.message || '截图识别失败，请稍后重试')
  } finally {
    recognizing.value = false
  }
}

async function submitImport() {
  importRows.value = validateImportRows(importRows.value)
  if (!canSubmit.value) {
    ElMessage.warning(invalidCount.value ? '请先修正标红的持仓' : '请先添加持仓')
    return
  }

  const submission = buildImportText(importRows.value)
  importing.value = true
  try {
    const response = await importPortfolioHoldings(props.portfolioId, submission.text)
    const result = response?.data || {}
    importRows.value = applyImportFailures(importRows.value, submission.rowIds, result.errors || [])
    emit('imported', result)

    if (Number(result.fail || 0) > 0) {
      ElMessage.warning(`已导入 ${result.success || 0} 条，${result.fail || 0} 条需要修正`)
      return
    }
    ElMessage.success(`已导入 ${result.success || submission.rowIds.length} 条持仓`)
    emit('update:modelValue', false)
  } catch (error) {
    ElMessage.error(error.message || '导入失败，请稍后重试')
  } finally {
    importing.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    width="780px"
    destroy-on-close
    append-to-body
    align-center
    class="portfolio-import-dialog"
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
    :show-close="!busy"
    @paste="onDialogPaste"
    @update:model-value="updateDialogVisible"
  >
    <template #header>
      <div class="import-dialog-header">
        <div>
          <p>批量录入</p>
          <h2>导入持仓</h2>
        </div>
        <div class="import-target">
          <span>目标组合</span>
          <strong>{{ portfolioName || '当前组合' }}</strong>
        </div>
      </div>
    </template>

    <div class="import-safety-note">
      <strong>新增或更新</strong>
      <span>同代码持仓会更新数量与成本，不会删除其他持仓。</span>
    </div>

    <section
      class="import-image-section"
      :class="{ 'is-recognizing': recognizing }"
      @dragover.prevent
      @drop.prevent="onImageDrop"
    >
      <input
        ref="imageInput"
        class="import-file-input"
        type="file"
        accept="image/png,image/jpeg,image/webp"
        :disabled="busy"
        @change="onImageChange"
      >
      <div class="import-image-copy">
        <el-icon><Picture /></el-icon>
        <div>
          <strong>{{ recognizing ? '正在识别截图' : '从券商截图识别' }}</strong>
          <span>{{ imageName || '选择、拖入或粘贴一张截图' }}</span>
        </div>
      </div>
      <el-button type="primary" plain :loading="recognizing" :disabled="busy" @click="openImagePicker">
        {{ recognizing ? '识别中' : '选择截图' }}
      </el-button>
    </section>
    <p class="import-image-note">识别结果仅供预览，核对并确认后才会写入持仓。</p>

    <el-alert
      v-for="warning in recognitionWarnings"
      :key="warning"
      class="import-recognition-alert"
      type="warning"
      :closable="false"
      show-icon
      :title="warning"
    />

    <section class="import-paste-section">
      <div class="import-section-heading">
        <div>
          <strong>粘贴数据</strong>
          <span>证券 ｜ 数量 ｜ 成本价（可空）</span>
        </div>
        <el-button v-if="importRows.length" link type="primary" :disabled="busy" @click="clearImport">清空</el-button>
      </div>
      <el-input
        v-model="importDraft"
        type="textarea"
        :rows="4"
        resize="vertical"
        :disabled="busy"
        placeholder="证券&#9;持仓数量&#9;成本价&#10;贵州茅台&#9;100&#9;1488.50&#10;000001&#9;1000&#9;12.30"
        @paste="onPaste"
      />
      <div class="import-paste-actions">
        <span>支持 Excel、Tab、逗号或空格分隔</span>
        <el-button type="primary" plain :icon="Upload" :disabled="busy" @click="parseDraft">解析预览</el-button>
      </div>
    </section>

    <section v-if="importRows.length" class="import-preview">
      <div class="import-section-heading import-preview-heading">
        <div>
          <strong>导入预览</strong>
          <span>
            {{ importRows.length }} 条
            <template v-if="successCount"> · 已导入 {{ successCount }} 条</template>
            <template v-if="invalidCount"> · 待修正 {{ invalidCount }} 条</template>
          </span>
        </div>
        <el-button :icon="Plus" :disabled="busy" @click="addRow">添加一行</el-button>
      </div>

      <div class="import-desktop-table" role="table" aria-label="持仓导入预览">
        <div class="import-table-header" role="row">
          <span>证券代码或名称</span>
          <span>持仓数量</span>
          <span>成本价</span>
          <span>状态</span>
          <span aria-hidden="true"></span>
        </div>
        <div v-for="(row, index) in importRows" :key="row.id" class="import-table-record" :class="`is-${row.status}`">
          <div class="import-table-row" role="row">
            <el-input
              :model-value="row.security"
              :disabled="row.status === 'success' || busy"
              :class="{ 'has-error': !row.security || row.error.includes('重复证券') || row.recognitionError }"
              :aria-label="`第 ${index + 1} 行证券`"
              placeholder="代码或名称"
              @input="updateRow(row.id, 'security', $event)"
            />
            <el-input
              :model-value="row.quantity"
              :disabled="row.status === 'success' || busy"
              :class="{ 'has-error': row.error.includes('数量') || row.recognitionError?.includes('数量') }"
              :aria-label="`第 ${index + 1} 行持仓数量`"
              inputmode="numeric"
              placeholder="正整数"
              @input="updateRow(row.id, 'quantity', $event)"
            />
            <el-input
              :model-value="row.costPrice"
              :disabled="row.status === 'success' || busy"
              :class="{ 'has-error': row.error.includes('成本价') || row.recognitionError?.includes('成本价') }"
              :aria-label="`第 ${index + 1} 行成本价`"
              inputmode="decimal"
              placeholder="可空"
              @input="updateRow(row.id, 'costPrice', $event)"
            />
            <el-tag v-if="row.status === 'success'" type="success" effect="light">已导入</el-tag>
            <el-tag v-else-if="row.error || row.serverError || row.recognitionError" type="danger" effect="light">待修正</el-tag>
            <el-tag v-else-if="row.recognitionWarning" type="warning" effect="light">待核对</el-tag>
            <el-tag v-else type="info" effect="plain">待导入</el-tag>
            <el-tooltip content="删除此行" placement="top">
              <el-button
                text
                circle
                :icon="Delete"
                :disabled="row.status === 'success' || busy"
                :aria-label="`删除第 ${index + 1} 行`"
                @click="removeRow(row.id)"
              />
            </el-tooltip>
          </div>
          <p v-if="row.error || row.serverError || row.recognitionError" class="import-row-error">
            {{ row.error || row.serverError || row.recognitionError }}
          </p>
          <p v-else-if="row.recognitionWarning" class="import-row-warning">{{ row.recognitionWarning }}</p>
        </div>
      </div>

      <div class="import-mobile-list">
        <article v-for="(row, index) in importRows" :key="row.id" class="import-mobile-record">
          <div class="import-mobile-record-head">
            <strong>第 {{ index + 1 }} 条</strong>
            <div>
              <el-tag v-if="row.status === 'success'" type="success" effect="light">已导入</el-tag>
              <el-tag v-else-if="row.error || row.serverError || row.recognitionError" type="danger" effect="light">待修正</el-tag>
              <el-tag v-else-if="row.recognitionWarning" type="warning" effect="light">待核对</el-tag>
              <el-tag v-else type="info" effect="plain">待导入</el-tag>
              <el-button
                text
                circle
                :icon="Delete"
                :disabled="row.status === 'success' || busy"
                :aria-label="`删除第 ${index + 1} 条`"
                @click="removeRow(row.id)"
              />
            </div>
          </div>
          <label>
            <span>证券代码或名称</span>
            <el-input
              :model-value="row.security"
              :disabled="row.status === 'success' || busy"
              placeholder="代码或名称"
              @input="updateRow(row.id, 'security', $event)"
            />
          </label>
          <div class="import-mobile-number-grid">
            <label>
              <span>持仓数量</span>
              <el-input
                :model-value="row.quantity"
                :disabled="row.status === 'success' || busy"
                inputmode="numeric"
                placeholder="正整数"
                @input="updateRow(row.id, 'quantity', $event)"
              />
            </label>
            <label>
              <span>成本价</span>
              <el-input
                :model-value="row.costPrice"
                :disabled="row.status === 'success' || busy"
                inputmode="decimal"
                placeholder="可空"
                @input="updateRow(row.id, 'costPrice', $event)"
              />
            </label>
          </div>
          <p v-if="row.error || row.serverError || row.recognitionError" class="import-row-error">
            {{ row.error || row.serverError || row.recognitionError }}
          </p>
          <p v-else-if="row.recognitionWarning" class="import-row-warning">{{ row.recognitionWarning }}</p>
        </article>
      </div>
    </section>

    <template #footer>
      <div class="import-dialog-footer">
        <span v-if="importRows.length">
          {{ invalidCount ? `${invalidCount} 条需要修正` : `${pendingRows.length} 条待导入` }}
        </span>
        <span v-else></span>
        <div>
          <el-button :disabled="busy" @click="updateDialogVisible(false)">取消</el-button>
          <el-button type="primary" :loading="importing" :disabled="!canSubmit" @click="submitImport">
            {{ retrying ? `再次导入 ${pendingRows.length} 条` : `确认导入 ${pendingRows.length} 条` }}
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
:global(.portfolio-import-dialog.el-dialog) {
  overflow: hidden;
  border: 1px solid var(--glass-border);
  border-radius: 8px;
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.22);
}

:global(.portfolio-import-dialog .el-dialog__header) {
  margin: 0;
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--line);
}

:global(.portfolio-import-dialog .el-dialog__body) {
  max-height: min(68vh, 720px);
  overflow-y: auto;
  padding: 18px 24px 20px;
}

:global(.portfolio-import-dialog .el-dialog__footer) {
  padding: 16px 24px 18px;
  border-top: 1px solid var(--line);
  background: var(--glass-tint);
}

.import-dialog-header,
.import-section-heading,
.import-paste-actions,
.import-dialog-footer,
.import-mobile-record-head,
.import-mobile-record-head > div {
  display: flex;
  align-items: center;
}

.import-dialog-header,
.import-section-heading,
.import-paste-actions,
.import-dialog-footer,
.import-mobile-record-head {
  justify-content: space-between;
  gap: 16px;
}

.import-dialog-header p {
  margin: 0 0 4px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 650;
}

.import-dialog-header h2 {
  margin: 0;
  color: var(--ink);
  font-size: 20px;
  line-height: 1.25;
}

.import-target {
  min-width: 0;
  padding-right: 34px;
  text-align: right;
}

.import-target span,
.import-target strong {
  display: block;
}

.import-target span {
  color: var(--muted);
  font-size: 11px;
}

.import-target strong {
  max-width: 260px;
  overflow: hidden;
  margin-top: 3px;
  color: var(--ink-soft);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.import-safety-note {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 18px;
  padding: 10px 12px;
  border-left: 3px solid var(--accent);
  background: rgba(0, 113, 227, 0.05);
  color: var(--ink-soft);
  font-size: 13px;
}

.import-safety-note strong {
  flex: 0 0 auto;
  color: var(--accent);
}

.import-image-section {
  display: flex;
  min-height: 74px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px dashed var(--line-strong);
  border-radius: 6px;
  background: #f8f9fb;
  transition: border-color 0.18s ease, background-color 0.18s ease;
}

.import-image-section:hover,
.import-image-section.is-recognizing {
  border-color: var(--accent);
  background: rgba(0, 113, 227, 0.04);
}

.import-file-input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}

.import-image-copy {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.import-image-copy > .el-icon {
  flex: 0 0 auto;
  color: var(--accent);
  font-size: 24px;
}

.import-image-copy strong,
.import-image-copy span {
  display: block;
}

.import-image-copy strong {
  color: var(--ink);
  font-size: 14px;
}

.import-image-copy span {
  overflow: hidden;
  margin-top: 4px;
  color: var(--muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.import-image-note {
  margin: 7px 0 0;
  color: var(--muted);
  font-size: 12px;
}

.import-recognition-alert {
  margin-top: 10px;
}

.import-paste-section {
  margin-top: 20px;
}

.import-section-heading {
  margin-bottom: 8px;
}

.import-section-heading > div:first-child {
  min-width: 0;
}

.import-section-heading strong,
.import-section-heading span {
  display: block;
}

.import-section-heading strong {
  color: var(--ink);
  font-size: 14px;
}

.import-section-heading span,
.import-paste-actions > span,
.import-dialog-footer > span {
  color: var(--muted);
  font-size: 12px;
}

.import-section-heading span {
  margin-top: 3px;
}

.import-paste-section :deep(.el-textarea__inner) {
  min-height: 112px !important;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  line-height: 1.55;
  box-shadow: 0 0 0 1px var(--line-strong) inset;
}

.import-paste-actions {
  margin-top: 8px;
}

.import-preview {
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid var(--line);
}

.import-preview-heading :deep(.el-button) {
  min-height: 36px;
}

.import-desktop-table {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 6px;
}

.import-table-header,
.import-table-row {
  display: grid;
  grid-template-columns: minmax(150px, 1.5fr) minmax(108px, 0.8fr) minmax(108px, 0.8fr) 82px 38px;
  gap: 8px;
  align-items: center;
}

.import-table-header {
  min-height: 38px;
  padding: 0 10px;
  border-bottom: 1px solid var(--line);
  background: #f6f7f9;
  color: var(--muted);
  font-size: 12px;
  font-weight: 650;
}

.import-table-record {
  padding: 9px 10px;
  border-bottom: 1px solid var(--line);
}

.import-table-record:last-child {
  border-bottom: 0;
}

.import-table-record.is-success {
  background: rgba(52, 199, 89, 0.04);
}

.import-table-row :deep(.el-input__wrapper) {
  min-height: 38px;
}

.import-table-row :deep(.has-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}

.import-table-row :deep(.el-tag) {
  justify-self: start;
}

.import-table-row :deep(.el-button) {
  width: 34px;
  height: 34px;
}

.import-row-error {
  margin: 6px 0 0;
  color: var(--el-color-danger);
  font-size: 12px;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.import-row-warning {
  margin: 6px 0 0;
  color: var(--el-color-warning-dark-2);
  font-size: 12px;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.import-mobile-list {
  display: none;
}

.import-dialog-footer > div {
  display: flex;
  gap: 8px;
}

.import-dialog-footer :deep(.el-button) {
  min-height: 38px;
  margin-left: 0;
}

@media (max-width: 640px) {
  :global(.portfolio-import-dialog.el-dialog) {
    width: calc(100% - 24px) !important;
    max-height: calc(100dvh - 24px);
    margin: 12px auto !important;
    display: flex;
    flex-direction: column;
  }

  :global(.portfolio-import-dialog .el-dialog__header),
  :global(.portfolio-import-dialog .el-dialog__body),
  :global(.portfolio-import-dialog .el-dialog__footer) {
    padding-right: 16px;
    padding-left: 16px;
  }

  :global(.portfolio-import-dialog .el-dialog__body) {
    flex: 1 1 auto;
    min-height: 0;
    max-height: none;
    padding-top: 14px;
  }

  :global(.portfolio-import-dialog .el-dialog__header),
  :global(.portfolio-import-dialog .el-dialog__footer) {
    flex: 0 0 auto;
  }

  .import-target strong {
    max-width: 140px;
  }

  .import-safety-note {
    align-items: flex-start;
  }

  .import-image-section {
    min-height: 108px;
    align-items: stretch;
    flex-direction: column;
  }

  .import-image-section :deep(.el-button) {
    width: 100%;
    min-height: 44px;
  }

  .import-paste-actions {
    align-items: flex-end;
  }

  .import-paste-actions > span {
    max-width: 180px;
    line-height: 1.35;
  }

  .import-paste-actions :deep(.el-button),
  .import-preview-heading :deep(.el-button) {
    min-height: 44px;
  }

  .import-desktop-table {
    display: none;
  }

  .import-mobile-list {
    display: grid;
    gap: 10px;
  }

  .import-mobile-record {
    padding: 12px;
    border: 1px solid var(--line);
    border-radius: 6px;
    background: rgba(255, 255, 255, 0.72);
  }

  .import-mobile-record-head {
    min-height: 32px;
    margin-bottom: 10px;
  }

  .import-mobile-record-head > strong {
    color: var(--ink-soft);
    font-size: 13px;
  }

  .import-mobile-record-head > div {
    gap: 6px;
  }

  .import-mobile-record-head :deep(.el-button) {
    width: 40px;
    height: 40px;
  }

  .import-mobile-record label,
  .import-mobile-record label > span {
    display: block;
  }

  .import-mobile-record label > span {
    margin-bottom: 5px;
    color: var(--muted);
    font-size: 12px;
    font-weight: 600;
  }

  .import-mobile-record :deep(.el-input__wrapper) {
    min-height: 42px;
  }

  .import-mobile-number-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    margin-top: 10px;
  }

  .import-dialog-footer {
    align-items: flex-end;
    gap: 8px;
  }

  .import-dialog-footer > div {
    flex: 0 0 auto;
  }

  .import-dialog-footer :deep(.el-button) {
    min-height: 44px;
    padding: 8px 12px;
  }
}
</style>
