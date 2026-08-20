import assert from 'node:assert/strict'
import test from 'node:test'

import { isConceptBoard, normalizeHotThemes } from './hotTheme.js'

test('concept board filter excludes style and outcome boards', () => {
  assert.equal(isConceptBoard('机器人执行器', 'CONCEPT'), true)
  assert.equal(isConceptBoard('创新药', 'CONCEPT'), true)
  assert.equal(isConceptBoard('昨日打二板以上表现', 'CONCEPT'), false)
  assert.equal(isConceptBoard('昨日连板_含一字', 'CONCEPT'), false)
  assert.equal(isConceptBoard('基金重仓', 'CONCEPT'), false)
  assert.equal(isConceptBoard('医药医疗风格', 'CONCEPT'), false)
  assert.equal(isConceptBoard('创新药', 'INDUSTRY'), false)
})

test('mainline normalization keeps concept boards only', () => {
  const themes = normalizeHotThemes({
    hotThemeItems: [
      { code: 'BK1156', name: '机器人执行器', boardType: 'CONCEPT', pctChg: 1.2 },
      { name: '基金重仓', boardType: 'CONCEPT', pctChg: 0.8 },
      { name: '医药生物', boardType: 'INDUSTRY', pctChg: 0.6 },
    ],
  })

  assert.deepEqual(themes.map((item) => item.name), ['机器人执行器'])
  assert.equal(themes[0].code, 'BK1156')
  assert.equal(themes[0].boardType, 'CONCEPT')
})
