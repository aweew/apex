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
  assert.match(dashboardSource, /HOME_CACHE_KEY\s*=\s*'apex\.dashboard\.home\.v19'/)
  assert.match(dashboardSource, /const command\s*=\s*computed\(\(\)\s*=>\s*home\.value\?\.command\s*\|\|\s*null\)/)
  assert.match(
    dashboardSource,
    /command\?\.preMarketSummary\?\.headline[\s\S]{0,300}?market\?\.positionAdvice/,
  )
})

test('dashboard renders the structured today forecast and Asia-Pacific basis', () => {
  assert.match(dashboardSource, /command\.preMarketSummary\?\.forecast\?\.marketOutlook/)
  assert.match(dashboardSource, /class="command-forecast"/)
  assert.match(dashboardSource, /forecast\.focusItems/)
  assert.match(dashboardSource, /forecast\.riskItems/)
  assert.match(dashboardSource, /forecast\.watchConditions/)
  assert.match(dashboardSource, /morningBriefing\.value\?\.asiaQuotes/)
  assert.match(dashboardSource, /<h5>亚太情绪<\/h5>/)
  assert.match(dashboardSource, /class="asia-index-grid"/)
  assert.match(dashboardSource, /morningBriefing\.value\?\.ftseA50Future/)
  assert.match(dashboardSource, /<h5>A股盘前<\/h5>/)
  assert.match(dashboardSource, /富时 A50 期指连续暂未获取/)
  assert.match(dashboardSource, /const openingAuction\s*=\s*computed\(/)
  assert.match(dashboardSource, /<h5>集合竞价确认<\/h5>/)
  assert.match(dashboardSource, /v-for="item in openingAuction\.indexes"/)
  assert.match(dashboardSource, /集合竞价是开盘前最后确认，不以外盘信号替代/)
})

test('dashboard separates external environment signals and explains their A-share impact', () => {
  assert.match(dashboardSource, /const externalMarketItems\s*=\s*computed\(/)
  assert.match(dashboardSource, /<h5>外围环境<\/h5>/)
  assert.match(dashboardSource, /v-for="item in externalMarketItems"/)
  assert.match(dashboardSource, /class="external-market-grid"/)
  assert.match(dashboardSource, /item\.aShareImpact/)
  assert.match(dashboardSource, /const externalMarketAvailableCount\s*=\s*computed\(/)
  assert.match(dashboardSource, /v-if="item\.available"/)
  assert.match(dashboardSource, /v-else>暂未获取<\/span>/)
  assert.match(dashboardSource, /影响 A 股开盘情绪的外部线索，并非单独买卖信号/)
  assert.match(dashboardSource, /已获取 \{\{ externalMarketAvailableCount \}\}\/5 项/)
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
  assert.match(dashboardSource, /command\.tradeDate/)
  assert.match(dashboardSource, /command\.marketDataAsOf/)
  assert.match(dashboardSource, /command\.marketDataUpdatedAt/)
  assert.match(dashboardSource, /command\.decisionDataAsOf/)
  assert.match(dashboardSource, /command\.generatedAt/)
  assert.match(dashboardSource, /commandStatusLabel\(command\.status\)/)
  assert.match(dashboardSource, /command\.value\?\.phase === 'IN_SESSION'/)
  assert.match(dashboardSource, /盘中判断/)
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

test('dashboard labels focus and actions without filler wording', () => {
  assert.match(dashboardSource, /<h4>今日重点<\/h4>/)
  assert.doesNotMatch(dashboardSource, /class="command-directions"/)
  assert.doesNotMatch(dashboardSource, /preMarketSummary\.opportunityItems/)
  assert.doesNotMatch(dashboardSource, /preMarketSummary\.riskItems/)
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
  assert.match(dashboardSource, /\.command-action\s*\{[^}]*min-height:\s*48px;/s)
  assert.match(dashboardSource, /\.command-action[^}]*overflow-wrap:\s*anywhere;/s)
  assert.doesNotMatch(dashboardSource, /\.command-(?:band|grid|column|action)\s*\{[^}]*(?<!-)height:\s*\d+px;/s)
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
  assert.match(dashboardSource, /const kolSources = computed\(\(\) => marketOpinion\.value\?\.kolSources \|\| \[\]\)/)
  assert.match(dashboardSource, /<h5>观点雷达<\/h5>/)
  assert.match(dashboardSource, /<h6>机构观点<\/h6>/)
  assert.match(dashboardSource, /<h6>游资席位行为<\/h6>/)
  assert.match(dashboardSource, /<h6>公开账号观点<\/h6>/)
  assert.match(dashboardSource, /marketOpinion\.kolSourceStatus/)
})

test('dashboard keeps report reading inside a closable preview and exposes active-seat context', () => {
  assert.match(dashboardSource, /const opinionPreviewOpen = ref\(false\)/)
  assert.match(dashboardSource, /function openOpinionPreview\(item\)/)
  assert.match(dashboardSource, /@click="openOpinionPreview\(item\)"/)
  assert.match(dashboardSource, /<el-dialog[^>]+v-model="opinionPreviewOpen"[^>]+title="研报预览"/)
  assert.match(dashboardSource, /<iframe[^>]+:src="opinionPreview\.url"/)
  assert.match(dashboardSource, /在新标签打开/)
  assert.match(dashboardSource, /item\.summary \|\| formatOpinionAmount\(item\.netAmount\)/)
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
    /\.command-meta\s*\{[^}]*font-size:\s*11px;[^}]*line-height:\s*1\.5;/s,
  )
  assert.match(
    dashboardSource,
    /\.command-action-copy\s*\{[^}]*font-size:\s*12px;[^}]*line-height:\s*1\.5;/s,
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

test('dashboard morning context leads with a conclusion and keeps supporting evidence quiet', () => {
  assert.match(dashboardSource, /class="morning-context-time-label">更新<\/span>/)
  assert.match(dashboardSource, /class="morning-news-lead"/)
  assert.match(dashboardSource, /class="morning-news-summary-label">核心结论<\/span>/)
  assert.match(
    dashboardSource,
    /\.morning-news-lead\s*\{[^}]*grid-template-columns:\s*auto minmax\(0,\s*1fr\);[^}]*border-left:\s*2px solid/s,
  )
  assert.match(dashboardSource, /\.morning-news-summary\s*\{[^}]*max-width:\s*78ch;/s)
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

test('dashboard keeps overnight layers stable across desktop and phone layouts', () => {
  assert.match(
    dashboardSource,
    /\.overnight-index-grid\s*\{[^}]*grid-template-columns:\s*repeat\(3,\s*minmax\(0,\s*1fr\)\);/s,
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
  assert.deepEqual(themeColumnRules, ['24px fit-content(180px) minmax(82px, max-content)'])
  assert.match(
    dashboardSource,
    /\.overnight-theme\s*\{[^}]*justify-content:\s*start;/s,
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
    /\.overnight-theme\s*\{[^}]*grid-template-columns:\s*24px fit-content\(180px\) minmax\(82px,\s*max-content\);/s,
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
