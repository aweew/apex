import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const dashboardSource = await readFile(new URL('./DashboardView.vue', import.meta.url), 'utf8')

test('dashboard places overnight market and news before action panels', () => {
  const contextIndex = dashboardSource.indexOf('盘前依据')
  const actionIndex = dashboardSource.indexOf('<div class="two-col">')

  assert.ok(contextIndex > 0)
  assert.ok(actionIndex > contextIndex)
  assert.match(dashboardSource, /home\.value\?\.morningBriefing/)
  assert.match(dashboardSource, /aria-label="盘前依据"/)
})

test('dashboard uses command headline with legacy advice fallback and a new cache version', () => {
  assert.match(dashboardSource, /HOME_CACHE_KEY\s*=\s*'apex\.dashboard\.home\.v20'/)
  assert.match(dashboardSource, /const command\s*=\s*computed\(\(\)\s*=>\s*home\.value\?\.command\s*\|\|\s*null\)/)
  assert.match(
    dashboardSource,
    /command\?\.preMarketSummary\?\.headline[\s\S]{0,300}?market\?\.positionAdvice/,
  )
})

test('dashboard renders the structured forecast and only shows available opening-auction quotes', () => {
  assert.match(dashboardSource, /command\.preMarketSummary\?\.forecast\?\.marketOutlook/)
  assert.match(dashboardSource, /class="command-forecast"/)
  assert.match(dashboardSource, /forecast\.focusItems/)
  assert.match(dashboardSource, /forecast\.riskItems/)
  assert.match(dashboardSource, /forecast\.watchConditions/)
  assert.match(dashboardSource, /morningBriefing\.value\?\.asiaQuotes/)
  assert.match(dashboardSource, /<h5>亚太情绪<\/h5>/)
  assert.match(dashboardSource, /class="asia-index-grid"/)
  assert.match(dashboardSource, /morningBriefing\.value\?\.ftseA50Future/)
  assert.match(dashboardSource, /富时 A50 期指连续暂未获取/)
  assert.match(dashboardSource, /const openingAuction\s*=\s*computed\(/)
  assert.match(
    dashboardSource,
    /const hasOpeningAuction\s*=\s*computed\(\(\)\s*=>\s*Boolean\([\s\S]{0,120}?openingAuction\.value\?\.available[\s\S]{0,100}?openingAuction\.value\?\.indexes\?\.length/s,
  )
  assert.match(dashboardSource, /<div v-if="hasOpeningAuction" class="overnight-layer">/)
  assert.match(dashboardSource, /<h5>集合竞价确认<\/h5>/)
  assert.match(dashboardSource, /v-for="item in openingAuction\.indexes"/)
  assert.doesNotMatch(dashboardSource, /集合竞价是开盘前最后确认，不以外盘信号替代/)
  assert.doesNotMatch(dashboardSource, /opening-auction-note/)
})

test('dashboard shows Golden Dragon and representative China concept stocks as core overnight data', () => {
  assert.match(dashboardSource, /const chinaGoldenDragon\s*=\s*computed\(/)
  assert.match(dashboardSource, /const chinaConceptMovers\s*=\s*computed\(/)
  assert.match(dashboardSource, /<h5>中国资产<\/h5>/)
  assert.match(dashboardSource, /morningBriefing\.value\?\.chinaGoldenDragon/)
  assert.match(dashboardSource, /morningBriefing\.value\?\.chinaConceptQuotes/)
  assert.match(dashboardSource, /v-for="quote in chinaConceptMovers"/)
  assert.match(dashboardSource, /function fmtOvernightQuoteTime\(quote\)/)
  assert.match(dashboardSource, /quote\.symbol\?\.startsWith\('us'\) \? '美东' : '北京'/)
  assert.match(dashboardSource, /class="china-asset-time"/)
  assert.match(dashboardSource, /纳斯达克中国金龙指数暂未获取/)
  const chinaAssetsIndex = dashboardSource.indexOf('<h5>中国资产</h5>')
  const disclosureIndex = dashboardSource.indexOf('class="morning-disclosure"')
  const ftseA50Index = dashboardSource.indexOf('<strong>富时 A50 期指连续</strong>')
  assert.ok(chinaAssetsIndex > 0)
  assert.ok(ftseA50Index > chinaAssetsIndex)
  assert.ok(ftseA50Index < disclosureIndex)
  assert.doesNotMatch(dashboardSource, /<h5>A股盘前<\/h5>/)
  assert.match(
    dashboardSource,
    /\.china-assets-grid,[\s\S]*?\.china-concept-grid\s*\{[^}]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\);/s,
  )
  assert.match(
    dashboardSource,
    /\.overnight-index-grid\.china-assets-grid \.overnight-quote,[\s\S]*?\.overnight-index-grid\.china-concept-grid \.overnight-quote\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\) auto;/s,
  )
  assert.match(
    dashboardSource,
    /@media \(max-width: 900px\)[\s\S]*?\.china-assets-grid,[\s\S]*?\.china-concept-grid\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\);/s,
  )
  assert.match(
    dashboardSource,
    /@media \(max-width: 560px\)[\s\S]*?\.overnight-quote-name small\.china-asset-meta\s*\{[^}]*display:\s*flex;/s,
  )
})

test('dashboard separates external environment signals and explains their A-share impact', () => {
  assert.match(dashboardSource, /const externalMarketItems\s*=\s*computed\(/)
  assert.match(dashboardSource, /<h5>外围环境<\/h5>/)
  assert.match(dashboardSource, /v-for="item in externalMarketItems"/)
  assert.match(dashboardSource, /class="external-market-grid"/)
  assert.match(dashboardSource, /item\.aShareImpact\s*\|\|\s*item\.ashareImpact\s*\|\|\s*'影响说明暂未获取'/)
  assert.match(dashboardSource, /const externalMarketAvailableCount\s*=\s*computed\(/)
  assert.match(dashboardSource, /v-if="item\.available"/)
  assert.match(dashboardSource, /v-else>暂未获取<\/span>/)
  assert.match(dashboardSource, /影响 A 股开盘情绪的外部线索，并非单独买卖信号/)
  assert.match(dashboardSource, /已获取 \{\{ externalMarketAvailableCount \}\}\/5 项/)
  assert.match(
    dashboardSource,
    /\.external-market-grid\s*\{[^}]*column-gap:\s*24px;[^}]*border-top:\s*1px solid/s,
  )
  assert.match(
    dashboardSource,
    /\.external-market-card\s*\{[^}]*padding:\s*10px 0 11px;[^}]*border:\s*0;[^}]*border-bottom:\s*1px solid[^}]*border-radius:\s*0;[^}]*background:\s*transparent;/s,
  )
})

test('dashboard presents structured pre-market event impacts with evidence labels', () => {
  assert.match(dashboardSource, /const preMarketEventImpacts\s*=\s*computed\(/)
  assert.match(dashboardSource, /<h5>盘前事件影响<\/h5>/)
  assert.match(dashboardSource, /v-for="item in visiblePreMarketEventImpacts"/)
  assert.match(dashboardSource, /item\.impactScope/)
  assert.match(dashboardSource, /item\.verificationStatus/)
  assert.match(dashboardSource, /item\.impactExplanation/)
})

test('dashboard places the command band after market effect and before pre-market context', () => {
  const effectIndex = dashboardSource.indexOf('aria-label="赚钱效应"')
  const commandIndex = dashboardSource.indexOf('aria-label="开盘准备"')
  const contextIndex = dashboardSource.indexOf('aria-label="盘前依据"')

  assert.ok(effectIndex > 0)
  assert.ok(commandIndex > effectIndex)
  assert.ok(contextIndex > commandIndex)
  assert.match(dashboardSource, /<section\s+v-if="command"[^>]+class="command-band[^>]+aria-label="开盘准备"/s)
  assert.match(dashboardSource, /const commandDataTimeText = computed/)
  assert.match(dashboardSource, /行情 \$\{compactUpdatedTime\} 更新/)
  assert.match(dashboardSource, /行情截至 \$\{command\.value\.marketDataAsOf\}/)
  assert.doesNotMatch(dashboardSource, /class="command-meta"/)
  assert.doesNotMatch(dashboardSource, /fmtCommandTime/)
  assert.match(dashboardSource, /commandStatusLabel\(command\.status\)/)
  assert.match(dashboardSource, /command\.value\?\.phase === 'IN_SESSION'/)
  assert.match(dashboardSource, /盘中判断/)
})

test('dashboard links to the full pre-market report without requiring a standalone menu item', () => {
  assert.match(dashboardSource, /router\.push\('\/pre-market-report'\)/)
  assert.match(dashboardSource, /完整研报/)
})

test('dashboard renders position controls and at most three command actions in backend order', () => {
  assert.match(dashboardSource, /command\.value\?\.operationGuide\?\.items[\s\S]{0,160}?\.slice\(0,\s*3\)/)
  assert.match(
    dashboardSource,
    /const hasExecutableNewPosition\s*=\s*computed\([\s\S]{0,240}?BUY_CONDITIONALLY[\s\S]{0,120}?READY/,
  )
  assert.match(dashboardSource, /v-for="item in commandOperationItems"/)
  assert.match(dashboardSource, /command\.operationGuide\.targetPositionMin/)
  assert.match(dashboardSource, /command\.operationGuide\.targetPositionMax/)
  assert.match(dashboardSource, /fmtFactor\(command\.operationGuide\.newPositionFactor\)/)
  assert.match(dashboardSource, /function fmtFactor\(value\)[\s\S]{0,180}?\.toFixed\(2\)[\s\S]{0,80}?倍/)
  assert.match(dashboardSource, /@click="openCommandAction\(item\.code\)"/)
  assert.match(dashboardSource, /RISK_FIRST:\s*'\/portfolio'/)
  assert.match(dashboardSource, /BUY_CONDITIONALLY:\s*'\/decision'/)
  assert.match(dashboardSource, /WATCH_ALERTS:\s*'\/observe'/)
  assert.match(dashboardSource, /REFRESH_DATA:\s*'\/sync'/)
  assert.match(dashboardSource, /code\s*===\s*'VIEW_CONTEXT'/)
  assert.match(dashboardSource, /getElementById\('pre-market-context'\)/)
  assert.match(dashboardSource, /v-if="Number\(item\.targetCount\) > 0"[^>]+class="command-target-count"/)
  assert.match(dashboardSource, /\{\{ item\.targetCount \}\}/)
  assert.match(dashboardSource, /v-if="command\.operationGuide && command\.status === 'READY' && hasExecutableNewPosition"/)
  assert.match(dashboardSource, /v-if="!commandOperationItems\.length" class="command-guide-summary"/)
})

test('dashboard hides decision placeholders while a new decision is generating', () => {
  assert.match(dashboardSource, /<h4>今日重点<\/h4>/)
  assert.doesNotMatch(dashboardSource, /class="command-directions"/)
  assert.doesNotMatch(dashboardSource, /preMarketSummary\.opportunityItems/)
  assert.doesNotMatch(dashboardSource, /preMarketSummary\.riskItems/)
  assert.match(
    dashboardSource,
    /<p\s+v-if="command\.status !== 'GENERATING' && decision\?\.hasToday && command\.preMarketSummary\?\.headline"\s+class="command-headline"\s*>/s,
  )
  assert.match(
    dashboardSource,
    /<div\s+v-if="command\.status !== 'GENERATING' && decision\?\.hasToday && command\.preMarketSummary\?\.watchConditions\?\.length"\s+class="command-watch"\s*>/s,
  )
  assert.doesNotMatch(dashboardSource, /盘前结论待生成/)
  assert.match(dashboardSource, /command\.status === 'READY' \? '取消条件' : '恢复条件'/)
  assert.match(dashboardSource, /<h4>执行清单<\/h4>/)
})

test('dashboard omits repeated broad-market evidence from the pre-market summary', () => {
  assert.doesNotMatch(dashboardSource, /class="command-evidence"/)
  assert.doesNotMatch(dashboardSource, /preMarketSummary\.evidenceItems/)
  assert.doesNotMatch(dashboardSource, />核心依据<\/span>/)
  assert.doesNotMatch(dashboardSource, /<h4>盘前总结<\/h4>\s*<span>/)
  assert.match(dashboardSource, /command\.operationGuide && command\.status === 'READY'/)
})

test('dashboard command band is two-column on desktop and safe on phone layouts', () => {
  assert.match(
    dashboardSource,
    /\.command-grid\s*\{[^}]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\);/s,
  )
  assert.match(
    dashboardSource,
    /@media \(max-width: 900px\)[\s\S]*?\.command-grid\s*\{[^}]*grid-template-columns:\s*1fr;/s,
  )
  assert.match(
    dashboardSource,
    /\.morning-context-grid\s*\{[^}]*display:\s*grid;[^}]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\);[^}]*gap:\s*0;/s,
  )
  assert.match(dashboardSource, /\.command-action\s*\{[^}]*min-height:\s*64px;/s)
  assert.match(dashboardSource, /\.command-action[^}]*overflow-wrap:\s*anywhere;/s)
  assert.doesNotMatch(dashboardSource, /\.command-(?:band|grid|column|action)\s*\{[^}]*(?<!-)height:\s*\d+px;/s)
})

test('dashboard gives the command summary more desktop width and top-aligns the guide content', () => {
  assert.match(
    dashboardSource,
    /@media \(min-width: 1200px\)[\s\S]*?\.command-grid\s*\{[^}]*grid-template-columns:\s*minmax\(0, 1\.25fr\) minmax\(300px, 0\.85fr\);/s,
  )
  assert.match(
    dashboardSource,
    /\.command-guide\s*\{[^}]*align-self:\s*stretch;[^}]*justify-content:\s*flex-start;[^}]*border-left:\s*1px solid var\(--line\);/s,
  )
  assert.match(
    dashboardSource,
    /\.command-actions\s*\{[^}]*margin-top:\s*12px;[^}]*margin-bottom:\s*0;/s,
  )
  assert.doesNotMatch(dashboardSource, /\.command-actions\s*\{[^}]*margin-(?:top|bottom):\s*auto;/s)
})

test('dashboard morning context has a compact responsive layout', () => {
  assert.match(
    dashboardSource,
    /\.morning-context\s*\{[^}]*padding:\s*0 18px 20px 2px;/s,
  )
  assert.match(
    dashboardSource,
    /@media \(max-width: 900px\)[\s\S]*?\.morning-context\s*\{[^}]*padding-right:\s*14px;[^}]*padding-left:\s*0;/s,
  )
  assert.match(dashboardSource, /\.morning-news-block\s*\{[^}]*padding-left:\s*28px;[^}]*border-left:\s*1px solid var\(--line\);/s)
  assert.match(
    dashboardSource,
    /@media \(max-width: 480px\)[\s\S]*\.morning-context-grid\s*\{[^}]*grid-template-columns:\s*1fr;/s,
  )
  assert.match(dashboardSource, /v-for="item in morningNewsCards"/)
  assert.match(dashboardSource, /const marketOpinion = computed\(\(\) => morningBriefing\.value\?\.marketOpinion \|\| null\)/)
  assert.match(dashboardSource, /const traderSeatViews = computed\(\(\) => marketOpinion\.value\?\.traderSeatViews \|\| \[\]\)/)
  assert.match(dashboardSource, /const activeSeats = computed\(\(\) => marketOpinion\.value\?\.activeSeats \|\| \[\]\)/)
  assert.match(dashboardSource, /const kolSources = computed\(\(\) => marketOpinion\.value\?\.kolSources \|\| \[\]\)/)
  assert.match(dashboardSource, /<h5>观点雷达<\/h5>/)
  assert.match(dashboardSource, /<h6>机构观点<\/h6>/)
  assert.match(dashboardSource, /<h6>已核验游资席位<\/h6>/)
  assert.match(dashboardSource, /<h6>龙虎榜活跃营业部<\/h6>/)
  assert.match(dashboardSource, /v-for="item in activeSeats"/)
  assert.match(dashboardSource, /仅表示公开席位行为，不代表具体自然人观点/)
  assert.match(dashboardSource, /\.opinion-group-head span\s*\{[^}]*min-width:\s*0;[^}]*text-align:\s*right;[^}]*overflow-wrap:\s*anywhere;/s)
  assert.match(dashboardSource, /<h6>公开账号观点<\/h6>/)
  assert.match(dashboardSource, /marketOpinion\.kolSourceStatus/)
})

test('dashboard gives collapsed market context less desktop width than the denser news column', () => {
  assert.match(
    dashboardSource,
    /class="morning-context-grid"\s+:class="\{ 'is-market-collapsed': !morningMarketExpanded \}"/,
  )
  assert.match(
    dashboardSource,
    /@media \(min-width: 1200px\)[\s\S]*?\.morning-context-grid\.is-market-collapsed\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*3fr\) minmax\(0,\s*5fr\);/s,
  )
})

test('dashboard keeps each Asia quote grouped instead of stretching it across the column', () => {
  assert.match(
    dashboardSource,
    /\.asia-index-grid \.overnight-quote\s*\{[^}]*justify-content:\s*flex-start;[^}]*gap:\s*12px;/s,
  )
  assert.match(dashboardSource, /\.asia-index-grid\s*\{[^}]*column-gap:\s*24px;/s)
})

test('dashboard keeps report reading inside a closable preview and exposes active-seat context', () => {
  assert.match(dashboardSource, /const opinionPreviewOpen = ref\(false\)/)
  assert.match(dashboardSource, /function openOpinionPreview\(item\)/)
  assert.match(dashboardSource, /@click="openOpinionPreview\(item\)"/)
  assert.match(dashboardSource, /<el-dialog[^>]+v-model="opinionPreviewOpen"[^>]+title="研报预览"/)
  assert.match(dashboardSource, /<iframe[^>]+:src="opinionPreview\.url"/)
  assert.match(dashboardSource, /在新标签打开/)
  assert.match(dashboardSource, /item\.summary \|\| '涉及股票未披露'/)
  assert.match(dashboardSource, /formatOpinionAmount\(item\.netAmount\) \|\| '金额未披露'/)
})

test('dashboard constrains overnight grids and quote prices within their desktop column', () => {
  assert.match(
    dashboardSource,
    /\.overnight-block\s*\{[^}]*padding-right:\s*28px;/s,
  )
  assert.match(
    dashboardSource,
    /\.morning-block-head > span\s*\{[^}]*min-width:\s*0;[^}]*text-align:\s*right;[^}]*overflow-wrap:\s*anywhere;/s,
  )
  assert.match(
    dashboardSource,
    /\.overnight-layer-head span\s*\{[^}]*min-width:\s*0;[^}]*text-align:\s*right;[^}]*overflow-wrap:\s*anywhere;/s,
  )
  assert.match(
    dashboardSource,
    /\.morning-context-empty\s*\{[^}]*max-width:\s*100%;[^}]*overflow-wrap:\s*anywhere;/s,
  )
  assert.match(
    dashboardSource,
    /@media \(max-width: 480px\)[\s\S]*?\.overnight-block\s*\{[^}]*padding-right:\s*0;/s,
  )
  assert.match(
    dashboardSource,
    /\.overnight-index-grid\s*\{[^}]*min-width:\s*0;[^}]*width:\s*100%;/s,
  )
  assert.match(
    dashboardSource,
    /\.overnight-theme-grid\s*\{[^}]*min-width:\s*0;[^}]*width:\s*100%;/s,
  )
  assert.match(
    dashboardSource,
    /\.overnight-star-grid\s*\{[^}]*min-width:\s*0;[^}]*width:\s*100%;/s,
  )
  assert.match(
    dashboardSource,
    /\.overnight-star-grid \.overnight-quote\s*\{[^}]*justify-content:\s*flex-start;[^}]*gap:\s*12px;/s,
  )
  assert.match(
    dashboardSource,
    /\.overnight-quote-name small\s*\{[^}]*min-width:\s*0;[^}]*overflow:\s*hidden;[^}]*text-overflow:\s*ellipsis;/s,
  )
})

test('dashboard keeps dense briefing copy readable across desktop and mobile', () => {
  assert.match(
    dashboardSource,
    /\.index-line \.n\s*\{[^}]*font-size:\s*11px;[^}]*line-height:\s*1\.35;/s,
  )
  assert.match(
    dashboardSource,
    /\.command-head p\s*\{[^}]*font-size:\s*12px;[^}]*line-height:\s*1\.5;/s,
  )
  assert.match(
    dashboardSource,
    /\.command-action-copy\s*\{[^}]*font-size:\s*12px;[^}]*line-height:\s*1\.6;/s,
  )
  assert.match(
    dashboardSource,
    /\.overnight-layer-head h5\s*\{[^}]*font-size:\s*12px;[^}]*line-height:\s*1\.5;/s,
  )
  assert.match(
    dashboardSource,
    /\.external-market-card p,[\s\S]*?\.external-market-note\s*\{[^}]*font-size:\s*11px;[^}]*line-height:\s*1\.6;/s,
  )
  assert.match(
    dashboardSource,
    /\.morning-news-item\s*\{[^}]*min-height:\s*42px;[^}]*font-size:\s*12px;/s,
  )
  assert.match(
    dashboardSource,
    /\.opinion-item\s*\{[^}]*min-height:\s*36px;[^}]*font-size:\s*12px;/s,
  )
  assert.match(
    dashboardSource,
    /\.mobile-score > em,[\s\S]*?\.mobile-exit-rule > em\s*\{[^}]*font-size:\s*11px;/s,
  )
  assert.match(
    dashboardSource,
    /\.mobile-action-tag\s*\{[^}]*font-size:\s*11px;[^}]*line-height:\s*1\.35;/s,
  )
})

test('mobile market overview balances labels and values across each card', () => {
  const compactMarketStart = dashboardSource.indexOf(
    '@media (max-width: 560px), (min-width: 561px) and (max-width: 900px) and (orientation: landscape)',
  )
  const compactMarketEnd = dashboardSource.indexOf('@media (max-width: 560px) {', compactMarketStart)
  const compactMarketStyles = dashboardSource.slice(compactMarketStart, compactMarketEnd)

  assert.equal(dashboardSource.match(/class="stat-value"/g)?.length, 2)
  assert.match(
    compactMarketStyles,
    /\.index-line\s*\{[^}]*display:\s*grid;[^}]*grid-template-columns:\s*minmax\(0, 1fr\) auto;/s,
  )
  assert.match(
    compactMarketStyles,
    /\.index-line \.p\s*\{[^}]*grid-column:\s*2;[^}]*grid-row:\s*1 \/ span 2;[^}]*text-align:\s*right;/s,
  )
  assert.match(
    compactMarketStyles,
    /\.stat-line \.stat:not\(\.volume-stat\)\s*\{[^}]*display:\s*grid;[^}]*grid-template-columns:\s*max-content minmax\(0, 1fr\);/s,
  )
  assert.match(
    compactMarketStyles,
    /\.stat-line \.stat-value\s*\{[^}]*justify-self:\s*end;[^}]*text-align:\s*right;/s,
  )
  assert.match(
    compactMarketStyles,
    /\.stat-line \.volume-stat\s*\{[^}]*align-items:\s*center;/s,
  )
  assert.match(
    compactMarketStyles,
    /\.stat-line \.stat:not\(\.volume-stat\)\s*\{[^}]*min-height:\s*50px;[^}]*padding-block:\s*7px;/s,
  )
})

test('mobile market volume aligns the percentage sign independently from its digits', () => {
  assert.match(dashboardSource, /const volumePercentageParts = computed/)
  assert.match(dashboardSource, /class="vol-sign"/)
  assert.match(dashboardSource, /class="vol-number"/)
  assert.match(dashboardSource, /\.vol-percentage\s*\{[^}]*display:\s*inline-flex;[^}]*align-items:\s*center;/s)
  assert.match(dashboardSource, /\.vol-sign\s*\{[^}]*transform:\s*translateY\(-1px\);/s)
})

test('mobile money effect cards stay compact and vertically center signed values', () => {
  const phoneStylesStart = dashboardSource.indexOf('@media (max-width: 560px) {')
  const phoneStylesEnd = dashboardSource.indexOf('@media (max-width: 900px) {', phoneStylesStart)
  const phoneStyles = dashboardSource.slice(phoneStylesStart, phoneStylesEnd)

  assert.match(
    phoneStyles,
    /\.effect-cell\s*\{[^}]*flex-direction:\s*row;[^}]*align-items:\s*center;[^}]*justify-content:\s*space-between;[^}]*min-height:\s*48px;/s,
  )
  assert.match(phoneStyles, /\.effect-cell em,\s*\.effect-cell b\s*\{[^}]*display:\s*inline-flex;[^}]*align-items:\s*center;[^}]*min-height:\s*20px;/s)
  assert.match(phoneStyles, /\.effect-cell b\s*\{[^}]*justify-content:\s*flex-end;[^}]*text-align:\s*right;[^}]*white-space:\s*nowrap;/s)
  assert.match(
    phoneStyles,
    /\.effect-cell:last-child:nth-child\(odd\)\s*\{[^}]*grid-column:\s*1 \/ -1;/s,
  )
  assert.match(dashboardSource, /const sign = n > 0 \? '\+' : n < 0 \? '−' : ''/)
  assert.match(dashboardSource, /`\$\{sign\}\$\{Math\.abs\(n\)\.toFixed\(2\)\}%`/)
})

test('dashboard morning context leads with a conclusion and keeps supporting evidence quiet', () => {
  assert.match(dashboardSource, /<span v-if="morningBriefingTime" class="morning-context-time">/)
  assert.match(dashboardSource, /<time>\{\{ morningBriefingTime \}\}<\/time>/)
  assert.match(dashboardSource, /class="morning-news-lead"/)
  assert.match(dashboardSource, /class="morning-news-summary-label">核心结论<\/span>/)
  assert.match(
    dashboardSource,
    /\.morning-news-lead\s*\{[^}]*grid-template-columns:\s*auto minmax\(0,\s*1fr\);[^}]*border-left:\s*2px solid/s,
  )
  assert.match(
    dashboardSource,
    /\.morning-news-summary\s*\{[^}]*max-width:\s*68ch;[^}]*line-height:\s*1\.75;/s,
  )
  assert.match(dashboardSource, /\.morning-context-block\s*\{[^}]*align-self:\s*start;/s)
  assert.match(
    dashboardSource,
    /@media \(max-width: 480px\)[\s\S]*?\.morning-news-block\s*\{[^}]*order:\s*-1;[^}]*border-bottom:\s*1px solid/s,
  )
  assert.match(
    dashboardSource,
    /@media \(max-width: 560px\)[\s\S]*?\.morning-news-item a,[\s\S]*?\.morning-news-title\s*\{[^}]*-webkit-line-clamp:\s*2;/s,
  )
})

test('dashboard separates overnight indexes, market themes and star quotes with legacy fallback', () => {
  assert.match(dashboardSource, /morningBriefing\.value\?\.indexQuotes/)
  assert.match(dashboardSource, /morningBriefing\.value\?\.marketThemes/)
  assert.match(dashboardSource, /morningBriefing\.value\?\.starQuotes/)
  assert.match(
    dashboardSource,
    /const overnightIndexes\s*=\s*computed\([\s\S]{0,500}?indexQuotes[\s\S]{0,500}?marketQuotes/,
  )
  assert.match(
    dashboardSource,
    /const overnightStars\s*=\s*computed\([\s\S]{0,500}?starQuotes[\s\S]{0,500}?marketQuotes/,
  )
  assert.match(dashboardSource, /legacyIndexSymbols\.has\(quote\.symbol\)/)
  assert.match(dashboardSource, /!legacyIndexSymbols\.has\(quote\.symbol\)/)
  assert.match(dashboardSource, /v-for="quote in overnightIndexes"/)
  assert.match(dashboardSource, /v-for="\(theme, index\) in overnightThemes"/)
  assert.match(dashboardSource, /v-for="quote in overnightStars"/)
  assert.doesNotMatch(dashboardSource, /绝对涨跌幅前八/)
})

test('dashboard benchmark index cards prioritize percentage changes over point values', () => {
  const benchmarkStart = dashboardSource.indexOf('class="overnight-index-grid benchmark-index-grid"')
  const benchmarkEnd = dashboardSource.indexOf('<p v-else class="morning-context-empty">', benchmarkStart)
  const benchmarkMarkup = dashboardSource.slice(benchmarkStart, benchmarkEnd)

  assert.ok(benchmarkStart > 0)
  assert.match(dashboardSource, /<h5>三大指数<\/h5>[\s\S]{0,80}?<span>涨跌幅<\/span>/)
  assert.match(benchmarkMarkup, /v-for="quote in overnightIndexes"/)
  assert.match(benchmarkMarkup, /fmtIndexPct\(quote\.pctChg\)/)
  assert.doesNotMatch(benchmarkMarkup, /latestPrice|fmtQuotePrice/)
  assert.match(
    dashboardSource,
    /\.benchmark-index-grid \.overnight-quote > b\s*\{[^}]*font-size:\s*15px;[^}]*font-weight:\s*750;/s,
  )
})

test('dashboard keeps overnight layers stable across desktop and phone layouts', () => {
  assert.match(
    dashboardSource,
    /\.overnight-index-grid\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\);/s,
  )
  assert.match(
    dashboardSource,
    /\.overnight-index-grid \.overnight-quote\s*\{[^}]*display:\s*grid;[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\) auto;[^}]*border-bottom:\s*1px solid/s,
  )
  assert.match(
    dashboardSource,
    /\.overnight-index-grid \.overnight-quote-name strong\s*\{[^}]*overflow:\s*visible;[^}]*text-overflow:\s*clip;[^}]*white-space:\s*normal;/s,
  )
  assert.match(
    dashboardSource,
    /\.morning-block-head\s*\{[^}]*align-items:\s*center;[^}]*min-height:\s*28px;/s,
  )
  assert.match(
    dashboardSource,
    /\.overnight-theme-grid\s*\{[^}]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\);/s,
  )
  assert.match(
    dashboardSource,
    /\.overnight-star-grid\s*\{[^}]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\);/s,
  )
  const themeColumnRules = [...dashboardSource.matchAll(
    /\.overnight-theme\s*\{[^}]*grid-template-columns:\s*([^;]+);/gs,
  )].map((match) => match[1])
  assert.deepEqual(themeColumnRules, ['24px minmax(0, 1fr) 104px'])
  assert.match(
    dashboardSource,
    /\.overnight-theme\s*\{[^}]*justify-content:\s*stretch;/s,
  )
  assert.match(dashboardSource, /\.overnight-theme-stats\s*\{[^}]*align-items:\s*stretch;/s)
  assert.match(
    dashboardSource,
    /\.overnight-theme-stats \.overnight-theme-breadth\s*\{[^}]*grid-template-columns:\s*28px 58px;[^}]*justify-content:\s*end;/s,
  )
})

test('dashboard pre-market summary has a compact hierarchy and accessible action', () => {
  assert.match(dashboardSource, /class="morning-context-title"/)
  assert.match(dashboardSource, /class="morning-context-status"/)
  assert.match(
    dashboardSource,
    /\.morning-context-head\s*\{[^}]*display:\s*grid;[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\) auto;/s,
  )
  assert.match(dashboardSource, /\.morning-context-title\s*\{[^}]*text-align:\s*left;/s)
  assert.match(dashboardSource, /\.morning-context-link[^}]*min-height:\s*44px;/s)
  assert.match(
    dashboardSource,
    /@media \(max-width: 560px\)[\s\S]*?\.morning-context-head\s*\{[^}]*grid-template-columns:\s*1fr;/s,
  )
})

test('dashboard theme ranking exposes breadth without relying on color alone', () => {
  assert.match(dashboardSource, /v-for="\(theme, index\) in overnightThemes"/)
  assert.match(dashboardSource, /class="overnight-theme-rank"/)
  assert.match(dashboardSource, /class="overnight-theme-breadth"/)
  assert.match(dashboardSource, /class="overnight-theme-breadth-track"/)
  assert.match(dashboardSource, /themeUpPct\(theme\)/)
  assert.match(dashboardSource, /\{\{ theme\.upCount \?\? 0 \}\}\/\{\{ theme\.quoteCount \?\? 0 \}\} 上涨/)
  assert.match(
    dashboardSource,
    /\.overnight-theme\s*\{[^}]*grid-template-columns:\s*24px minmax\(0,\s*1fr\) 104px;/s,
  )
})

test('dashboard renders the pre-market breadth forecast as a red-green tug of war with backtest truth', () => {
  assert.match(dashboardSource, /const breadthForecast\s*=\s*computed\(/)
  assert.match(dashboardSource, /class="breadth-forecast\s+enter\s+delay-1"/)
  assert.match(dashboardSource, /预测上涨/)
  assert.match(dashboardSource, /预测下跌/)
  assert.match(dashboardSource, /class="breadth-forecast-track"/)
  assert.match(dashboardSource, /class="breadth-forecast-up"/)
  assert.match(dashboardSource, /class="breadth-forecast-down"/)
  assert.match(dashboardSource, /breadthForecast\.actualUpRatio/)
  assert.match(dashboardSource, /breadthForecast\.rollingBacktestSummary/)
  assert.match(dashboardSource, /@media \(max-width: 560px\)[\s\S]*?\.breadth-forecast-main\s*\{[^}]*grid-template-columns:\s*1fr;/s)
})

test('dashboard keeps empty forecasts compact without hiding available forecast detail', () => {
  assert.match(
    dashboardSource,
    /class="breadth-forecast enter delay-1"\s+:class="\{ 'is-empty': !breadthForecast\.available \}"/,
  )
  assert.match(
    dashboardSource,
    /\.breadth-forecast\.is-empty\s*\{[^}]*display:\s*block;[^}]*padding:\s*9px 14px;/s,
  )
  assert.match(
    dashboardSource,
    /\.breadth-forecast\.is-empty \.breadth-forecast-empty\s*\{[^}]*display:\s*flex;[^}]*align-items:\s*baseline;/s,
  )
})

test('dashboard progressively discloses secondary pre-market evidence in both columns', () => {
  assert.match(dashboardSource, /const morningMarketExpanded = ref\(false\)/)
  assert.match(dashboardSource, /const morningNewsExpanded = ref\(false\)/)
  assert.match(
    dashboardSource,
    /const visiblePreMarketEventImpacts = computed\([\s\S]{0,220}?slice\(0, 3\)/,
  )
  assert.match(
    dashboardSource,
    /id="morning-market-more"[^>]+class="morning-context-more"[^>]+v-show="morningMarketExpanded"/,
  )
  assert.match(
    dashboardSource,
    /id="morning-news-more"[^>]+class="morning-context-more"[^>]+v-show="morningNewsExpanded"/,
  )
  assert.match(dashboardSource, /aria-controls="morning-market-more"/)
  assert.match(dashboardSource, /:aria-expanded="morningMarketExpanded"/)
  assert.match(dashboardSource, /aria-controls="morning-news-more"/)
  assert.match(dashboardSource, /:aria-expanded="morningNewsExpanded"/)
  assert.match(dashboardSource, /<p v-if="morningNewsExpanded">\{\{ item\.impactExplanation \}\}<\/p>/)
  assert.match(
    dashboardSource,
    /v-if="morningNewsExpanded && \(item\.relatedCodes\?\.length \|\| item\.themes\?\.length\)"/,
  )
  assert.match(
    dashboardSource,
    /\.morning-disclosure\s*\{[^}]*min-height:\s*44px;/s,
  )
})

test('dashboard avoids repeated auction state copy and keeps directional color on values', () => {
  assert.doesNotMatch(
    dashboardSource,
    /<p v-else class="morning-context-empty">\{\{ openingAuction\?\.stateDesc/,
  )
  assert.match(
    dashboardSource,
    /\.effect-cell\.up,[\s\S]{0,80}?\.effect-cell\.down\s*\{[^}]*background:\s*rgba\(15, 23, 42, 0\.03\);/s,
  )
  assert.match(dashboardSource, /\.effect-cell\.up b \{ color: var\(--up\); \}/)
  assert.match(dashboardSource, /\.effect-cell\.down b \{ color: var\(--down\); \}/)
})
