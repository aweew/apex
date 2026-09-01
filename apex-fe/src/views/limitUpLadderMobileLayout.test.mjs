import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

import {
  LIMIT_UP_SHARE_WIDTH,
  limitUpCaptureScale,
} from '../utils/limitUpShareSheet.js'

const ladderSource = await readFile(new URL('./LimitUpLadderView.vue', import.meta.url), 'utf8')
const shareSheetSource = await readFile(new URL('../utils/limitUpShareSheet.js', import.meta.url), 'utf8')
const mobileStyles = ladderSource.slice(ladderSource.indexOf('@media (max-width: 720px)'))

test('mobile ladder cards share the remaining row width across three columns', () => {
  assert.match(
    mobileStyles,
    /\.tier\s*\{[\s\S]*?grid-template-columns:\s*max-content minmax\(0, 1fr\);/,
  )
  assert.match(
    mobileStyles,
    /\.tier-grid\s*\{[\s\S]*?display:\s*grid;[\s\S]*?grid-template-columns:\s*repeat\(3, minmax\(0, 1fr\)\);[\s\S]*?width:\s*100%;/,
  )
  assert.match(mobileStyles, /\.card\s*\{[\s\S]*?width:\s*100%;[\s\S]*?min-width:\s*0;/)
})

test('mobile ladder gives concepts more room beside percentage changes', () => {
  assert.match(mobileStyles, /\.card-sub\s*\{[\s\S]*?gap:\s*2px;/)
  assert.match(mobileStyles, /\.card-pct\s*\{[\s\S]*?font-size:\s*9px;/)
})

test('ladder header keeps direct market shortcuts beside the compact refresh control', () => {
  assert.match(ladderSource, /import \{ Grid, Opportunity, Refresh \} from '@element-plus\/icons-vue'/)
  assert.match(ladderSource, /<el-button-group class="lu-view-actions">/)
  assert.match(ladderSource, /<el-button plain :icon="Refresh" :loading="refreshing" @click="onRefresh">刷新<\/el-button>/)
  assert.match(ladderSource, /<el-button plain :icon="Grid" @click="router\.push\(\{ path: '\/market', query: \{ tab: 'sector' \} \}\)">板块<\/el-button>/)
  assert.match(ladderSource, /<el-button plain :icon="Opportunity" @click="router\.push\('\/hot'\)">热点<\/el-button>/)
  assert.doesNotMatch(ladderSource, /<el-dropdown @command="openRelatedView">/)
})

test('mobile ladder keeps the date compact and aligns every header action', () => {
  assert.match(ladderSource, /class="lu-date-picker"/)
  assert.match(mobileStyles, /\.lu-page \.header > \.actions > \.lu-date-picker\s*\{[^}]*flex:\s*0 0 132px;[^}]*width:\s*132px !important;[^}]*min-height:\s*40px;/)
  assert.match(mobileStyles, /\.lu-page \.lu-date-picker :deep\(\.el-input__wrapper\),[\s\S]*?\.lu-page \.lu-view-actions :deep\(\.el-button\)\s*\{[^}]*height:\s*40px !important;[^}]*min-height:\s*40px !important;/)
})

test('mobile sharing defaults to a portrait phone layout', () => {
  assert.equal(LIMIT_UP_SHARE_WIDTH.mobile, 1080)
  assert.match(ladderSource, /function preferredShareMode\(\)[\s\S]*?matchMedia\('\(max-width: 720px\)'\)[\s\S]*?\? 'mobile' : 'desktop'/)
  assert.match(ladderSource, /shareMode\.value = mode === 'desktop' \|\| mode === 'mobile' \? mode : preferredShareMode\(\)/)
  assert.match(shareSheetSource, /const cardW = mobile \? 170 : 100/)
  assert.match(shareSheetSource, /const nameFs = mobile \? 22 : 11/)
  assert.match(ladderSource, /<el-radio-button value="mobile">手机版 1080<\/el-radio-button>/)
})

test('clipboard capture is pre-scaled to avoid messaging app downsampling', () => {
  const previewScale = limitUpCaptureScale({ width: 1080, height: 2800, devicePixelRatio: 3, intent: 'preview' })
  const clipboardScale = limitUpCaptureScale({ width: 1080, height: 2800, devicePixelRatio: 3, intent: 'clipboard' })
  const downloadScale = limitUpCaptureScale({ width: 1080, height: 2800, devicePixelRatio: 3, intent: 'download' })
  assert.equal(previewScale, 1)
  assert.equal(Math.round(1080 * clipboardScale), 1280)
  assert.equal(downloadScale, 3)
  assert.match(ladderSource, /const previewBlob = sharePreviewObjectUrl[\s\S]*?fetch\(sharePreviewObjectUrl\)[\s\S]*?: captureBoard\(shareMode\.value, 'clipboard'\)/)
  assert.match(ladderSource, /copyImageBlob\(previewBlob\)/)
  assert.match(ladderSource, /captureBoard\(shareMode\.value, 'download'\)/)
})
