import assert from 'node:assert/strict'
import test from 'node:test'

import {
  applyImportFailures,
  buildImportText,
  clearRecognitionErrorForField,
  createImportRow,
  createImportRowsFromImagePreview,
  parsePortfolioImportText,
  validateImportRows,
} from './portfolioImport.js'

test('parses spreadsheet rows with a Chinese header and tab separators', () => {
  const rows = parsePortfolioImportText('证券\t持仓数量\t成本价\n贵州茅台\t100\t1488.50\n000001\t2,000\t12.30')

  assert.deepEqual(rows.map(({ security, quantity, costPrice }) => ({ security, quantity, costPrice })), [
    { security: '贵州茅台', quantity: '100', costPrice: '1488.50' },
    { security: '000001', quantity: '2000', costPrice: '12.30' },
  ])
})

test('parses comma, Chinese comma, and whitespace rows without a header', () => {
  const rows = parsePortfolioImportText('000001,1000,12.5\n600519，100，1800\n九洲药业 300 38.20')

  assert.equal(rows.length, 3)
  assert.deepEqual(rows.map((row) => row.security), ['000001', '600519', '九洲药业'])
})

test('validates required values, positive numbers, and duplicate securities', () => {
  const rows = [
    createImportRow({ security: '000001', quantity: '100', costPrice: '12.5' }),
    createImportRow({ security: '000001', quantity: '200', costPrice: '13' }),
    createImportRow({ security: '600519', quantity: '1.5', costPrice: '-1' }),
    createImportRow({ security: '', quantity: '', costPrice: '' }),
  ]

  const validated = validateImportRows(rows)
  assert.match(validated[0].error, /重复证券/)
  assert.match(validated[1].error, /重复证券/)
  assert.match(validated[2].error, /数量必须为正整数/)
  assert.match(validated[2].error, /成本价必须大于 0/)
  assert.match(validated[3].error, /请输入证券/)
})

test('serializes only pending valid rows and maps server failures back to them', () => {
  const rows = validateImportRows([
    createImportRow({ security: '000001', quantity: '100', costPrice: '12.5' }),
    createImportRow({ security: '600519', quantity: '200', costPrice: '' }),
    createImportRow({ security: '300750', quantity: '300', costPrice: '180' }),
  ])

  const submission = buildImportText(rows)
  assert.equal(submission.text, '000001,100,12.5\n600519,200\n300750,300,180')

  const updated = applyImportFailures(rows, submission.rowIds, ['第2行: 无法识别代码/名称: 600519'])
  assert.equal(updated[0].status, 'success')
  assert.equal(updated[1].status, 'error')
  assert.equal(updated[1].serverError, '无法识别代码/名称: 600519')
  assert.equal(updated[2].status, 'success')

  const retry = buildImportText(updated)
  assert.equal(retry.text, '600519,200')
})

test('keeps completed rows immutable when a retry duplicates their security', () => {
  const rows = validateImportRows([
    createImportRow({ security: '000001', quantity: '100', costPrice: '12.5', status: 'success' }),
    createImportRow({ security: '000001', quantity: '200', costPrice: '13' }),
  ])

  assert.equal(rows[0].error, '')
  assert.match(rows[1].error, /重复证券/)
})

test('maps screenshot recognition into editable rows and keeps blocking warnings', () => {
  const rows = createImportRowsFromImagePreview({
    rows: [
      { security: '600519', name: '贵州茅台', quantity: '100', costPrice: '1488.50', confidence: 0.98, valid: true, warning: '' },
      { security: '模糊证券', quantity: '200', costPrice: '10', confidence: 0.62, valid: false, warning: '无法精确匹配证券；置信度较低，请重点复核' },
    ],
    warnings: ['截图右侧被裁切，请确认是否缺少持仓'],
  })

  assert.equal(rows.length, 2)
  assert.equal(rows[0].security, '600519')
  assert.equal(rows[0].recognitionName, '贵州茅台')
  assert.equal(rows[0].recognitionError, '')
  assert.match(rows[1].recognitionError, /无法精确匹配证券/)
  assert.equal(rows[1].confidence, 0.62)
})

test('editing a screenshot row clears only the matching recognition error', () => {
  const [row] = createImportRowsFromImagePreview({
    rows: [{ security: '模糊证券', quantity: '200', costPrice: '10', valid: false, warning: '无法精确匹配证券' }],
  })

  const quantityEditError = clearRecognitionErrorForField(row.recognitionError, 'quantity')
  const securityEditError = clearRecognitionErrorForField(row.recognitionError, 'security')
  const updated = validateImportRows([{ ...row, security: '600519', recognitionError: securityEditError }])

  assert.match(quantityEditError, /无法精确匹配证券/)
  assert.equal(updated[0].error, '')
  assert.equal(updated[0].recognitionError, '')
})
