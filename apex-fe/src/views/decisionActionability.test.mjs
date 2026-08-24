import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const decisionSource = await readFile(new URL('./DecisionView.vue', import.meta.url), 'utf8')

test('decision buys present the action plan and market data status on desktop and mobile', () => {
  assert.match(decisionSource, /label="行动计划"/)
  assert.ok((decisionSource.match(/class="decision-action-plan"/g) || []).length >= 2)
  assert.match(decisionSource, /class="decision-data-status"/)
  assert.match(decisionSource, /市场数据/)
})

test('observation-only and red-data buy suggestions cannot open a paper order', () => {
  assert.match(decisionSource, /from '\.\.\/utils\/decisionActionability\.js'/)
  assert.match(decisionSource, /function decisionExecutionContext\(\)/)
  assert.match(decisionSource, /if \(buy && !canExecutePaperBuy\(row\)\)/)
  assert.match(decisionSource, /:disabled="!canExecutePaperBuy\(row\)"/)
  assert.match(decisionSource, /orderFromDecision\(/)
})

test('today action list remains before expandable decision evidence', () => {
  const actionPanel = decisionSource.indexOf('<section class="action-panel">')
  const evidencePanels = decisionSource.indexOf('<details class="decision-evidence-toggle">')

  assert.ok(actionPanel > 0)
  assert.ok(evidencePanels > 0)
  assert.match(decisionSource, /\.action-panel\s*\{\s*order:\s*2;/)
  assert.match(decisionSource, /\.decision-evidence-toggle\s*\{\s*order:\s*3;/)
})

test('decision workspace provides the Xiaoling analysis entry without restoring global navigation', () => {
  assert.match(decisionSource, /class="xiaoling-link"[\s\S]*?@click="router\.push\('\/ai-center'\)"/)
  assert.match(decisionSource, /<MagicStick \/>/)
})
