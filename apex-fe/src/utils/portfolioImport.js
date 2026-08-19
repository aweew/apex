let importRowSequence = 0

const SECURITY_HEADERS = new Set(['证券', '证券代码', '证券名称', '股票', '股票代码', '股票名称', '代码', '名称'])
const QUANTITY_HEADERS = new Set(['数量', '持仓数量', '持有数量', '股份余额', '股票余额', '可用余额'])
const COST_HEADERS = new Set(['成本', '成本价', '持仓成本', '摊薄成本价', '参考成本价'])

function cleanCell(value) {
  return String(value ?? '').trim().replace(/^['"]|['"]$/g, '')
}

function cleanHeader(value) {
  return cleanCell(value).replace(/[\s()（）]/g, '')
}

function splitLine(line) {
  const normalized = String(line || '').replaceAll('，', ',')
  if (normalized.includes('\t')) return normalized.split(/\t+/).map(cleanCell)
  if (normalized.includes(',')) return normalized.split(',').map(cleanCell)
  return normalized.trim().split(/\s+/).map(cleanCell)
}

function findHeaderIndex(cells, candidates) {
  return cells.findIndex((cell) => candidates.has(cleanHeader(cell)))
}

function detectHeader(cells) {
  const securityIndex = findHeaderIndex(cells, SECURITY_HEADERS)
  const quantityIndex = findHeaderIndex(cells, QUANTITY_HEADERS)
  const costPriceIndex = findHeaderIndex(cells, COST_HEADERS)
  if (securityIndex < 0 || quantityIndex < 0) return null
  return { securityIndex, quantityIndex, costPriceIndex }
}

function normalizeNumberText(value) {
  return cleanCell(value).replaceAll(',', '')
}

const RECOGNITION_ERROR_FIELDS = {
  security: ['无法精确匹配证券', '证券代码与名称不一致'],
  quantity: ['数量必须为正整数'],
  costPrice: ['成本价必须大于 0'],
}

function splitRecognitionWarnings(warning) {
  return String(warning || '').split('；').map((item) => item.trim()).filter(Boolean)
}

export function createImportRow(values = {}) {
  return {
    id: values.id || `portfolio-import-${++importRowSequence}`,
    security: cleanCell(values.security),
    quantity: normalizeNumberText(values.quantity),
    costPrice: normalizeNumberText(values.costPrice),
    sourceLine: values.sourceLine || null,
    status: values.status || 'ready',
    error: '',
    serverError: values.serverError || '',
    recognitionError: values.recognitionError || '',
    recognitionWarning: values.recognitionWarning || '',
    recognitionName: values.recognitionName || '',
    confidence: values.confidence ?? null,
  }
}

export function validateImportRows(rows) {
  const duplicateCounts = new Map()
  for (const row of rows) {
    const securityKey = cleanCell(row.security).toLowerCase()
    if (securityKey) duplicateCounts.set(securityKey, (duplicateCounts.get(securityKey) || 0) + 1)
  }

  return rows.map((row) => {
    const security = cleanCell(row.security)
    const quantity = normalizeNumberText(row.quantity)
    const costPrice = normalizeNumberText(row.costPrice)
    const errors = []

    if (!security) errors.push('请输入证券')
    const quantityNumber = Number(quantity)
    if (!Number.isInteger(quantityNumber) || quantityNumber <= 0) errors.push('数量必须为正整数')
    if (costPrice) {
      const costPriceNumber = Number(costPrice)
      if (!Number.isFinite(costPriceNumber) || costPriceNumber <= 0) errors.push('成本价必须大于 0')
    }
    if (row.status !== 'success' && security && duplicateCounts.get(security.toLowerCase()) > 1) {
      errors.push('存在重复证券')
    }

    return {
      ...row,
      security,
      quantity,
      costPrice,
      error: errors.join('；'),
    }
  })
}

export function parsePortfolioImportText(text) {
  const sourceLines = String(text || '').replace(/\r\n?/g, '\n').split('\n')
  const contentLines = []
  for (let index = 0; index < sourceLines.length; index++) {
    const line = sourceLines[index].trim()
    if (!line || line.startsWith('#')) continue
    contentLines.push({ line, sourceLine: index + 1 })
  }
  if (!contentLines.length) return []

  const firstCells = splitLine(contentLines[0].line)
  const header = detectHeader(firstCells)
  const dataLines = header ? contentLines.slice(1) : contentLines
  const rows = []
  for (const item of dataLines) {
    const cells = splitLine(item.line)
    const securityIndex = header?.securityIndex ?? 0
    const quantityIndex = header?.quantityIndex ?? 1
    const costPriceIndex = header?.costPriceIndex ?? 2
    rows.push(createImportRow({
      security: cells[securityIndex],
      quantity: cells[quantityIndex],
      costPrice: costPriceIndex >= 0 ? cells[costPriceIndex] : '',
      sourceLine: item.sourceLine,
    }))
  }
  return validateImportRows(rows)
}

export function buildImportText(rows) {
  const pendingRows = rows.filter((row) => row.status !== 'success' && !row.error && !row.recognitionError)
  return {
    text: pendingRows.map((row) => {
      const values = [row.security, row.quantity]
      if (row.costPrice) values.push(row.costPrice)
      return values.join(',')
    }).join('\n'),
    rowIds: pendingRows.map((row) => row.id),
  }
}

export function createImportRowsFromImagePreview(preview = {}) {
  const rows = Array.isArray(preview.rows) ? preview.rows : []
  return validateImportRows(rows.map((row) => {
    const warnings = splitRecognitionWarnings(row.warning)
    const blockingWarnings = warnings.filter((warning) => Object.values(RECOGNITION_ERROR_FIELDS)
      .flat().some((phrase) => warning.includes(phrase)))
    const reviewWarnings = warnings.filter((warning) => !blockingWarnings.includes(warning)
      && !warning.includes('存在重复证券'))
    return createImportRow({
      security: row.security || row.code || row.name,
      quantity: row.quantity,
      costPrice: row.costPrice,
      recognitionName: row.name,
      recognitionError: blockingWarnings.join('；'),
      recognitionWarning: reviewWarnings.join('；'),
      confidence: row.confidence,
    })
  }))
}

export function clearRecognitionErrorForField(error, field) {
  const fieldPhrases = RECOGNITION_ERROR_FIELDS[field] || []
  return splitRecognitionWarnings(error)
    .filter((warning) => !fieldPhrases.some((phrase) => warning.includes(phrase)))
    .join('；')
}

export function applyImportFailures(rows, submittedRowIds, errors = []) {
  const failureByRowId = new Map()
  const unmatchedErrors = []
  for (const error of errors) {
    const match = String(error || '').match(/^第(\d+)行:\s*(.+)$/)
    const submittedRowId = match ? submittedRowIds[Number(match[1]) - 1] : null
    if (submittedRowId) failureByRowId.set(submittedRowId, match[2])
    else if (error) unmatchedErrors.push(String(error))
  }

  return rows.map((row) => {
    if (!submittedRowIds.includes(row.id)) return row
    const serverError = failureByRowId.get(row.id)
    if (serverError) return { ...row, status: 'error', serverError }
    if (unmatchedErrors.length) return { ...row, status: 'error', serverError: unmatchedErrors.join('；') }
    return { ...row, status: 'success', serverError: '' }
  })
}
