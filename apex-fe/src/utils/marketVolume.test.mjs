import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildVolumeChangeParts,
  formatVolumeChangeAmount,
  formatVolumeChangeText,
  resolveVolumeChangeAmount,
} from './marketVolume.js'

test('formatVolumeChangeAmount uses absolute Chinese amount units', () => {
  assert.equal(formatVolumeChangeAmount(135884002291), '1359亿')
  assert.equal(formatVolumeChangeAmount(-250000), '25.0万')
  assert.equal(formatVolumeChangeAmount(null), '')
})

test('resolveVolumeChangeAmount prefers exact field and supports legacy response', () => {
  assert.equal(resolveVolumeChangeAmount({ indexVolumeChange: -42 }, -5), -42)
  assert.equal(
    formatVolumeChangeAmount(resolveVolumeChangeAmount({ indexVolume: 2683569998793.4613 }, 5.33)),
    '1358亿',
  )
})

test('formatVolumeChangeText includes direction amount and percentage', () => {
  assert.equal(formatVolumeChangeText({
    volumeTrend: '放量',
    volumeVsMa5Pct: 5.33,
    indexVolumeChange: 135884002291,
  }), '放量 1359亿 +5.33%')
  assert.equal(formatVolumeChangeText({
    volumeTrend: '缩量',
    volumeVsMa5Pct: -4.5,
    indexVolumeChange: -10000000000,
  }), '缩量 100亿 -4.50%')
  assert.equal(formatVolumeChangeText({ volumeLabel: '今日' }), '今日')
})

test('buildVolumeChangeParts separates neutral detail from colored percentage', () => {
  assert.deepEqual(buildVolumeChangeParts({
    volumeTrend: '放量',
    volumeVsMa5Pct: 5.33,
    indexVolumeChange: 135884002291,
  }), {
    detailText: '放量 1359亿',
    percentageText: '+5.33%',
  })
  assert.deepEqual(buildVolumeChangeParts({ volumeLabel: '今日' }), {
    detailText: '今日',
    percentageText: '',
  })
})
