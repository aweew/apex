const SECTION_PATTERN = /^(\d{2})[｜|]\s*(.+)$/
const HIDDEN_LINE_PATTERN = /数据暂缺|未获取|暂不据此|缺少(?:独立)?(?:资金流)?数据|待补充/

function emptyReport() {
  return {
    title: '',
    date: '',
    judgement: '',
    priority: '',
    risk: '',
    sections: [],
  }
}

function fieldValue(line, label) {
  return line.startsWith(label) ? line.slice(label.length).trim() : ''
}

function holdingField(parts, label) {
  const field = parts.find((part) => part.startsWith(label))
  return field ? field.slice(label.length).trim() : ''
}

function metricValue(text) {
  if (!text) return null
  const value = Number.parseFloat(text.replace('%', ''))
  return Number.isFinite(value) ? value : null
}

function cleanLine(line) {
  return String(line || '').replace(/^[-•]\s*/, '').trim()
}

function labelledValue(parts, label) {
  const field = parts.find((part) => part.startsWith(label))
  return field ? field.slice(label.length).trim() : ''
}

export function parseFactLine(line) {
  const text = cleanLine(line)
  const separatorIndex = text.search(/[：｜|]/)
  if (separatorIndex < 0) {
    return { label: '', value: text }
  }
  return {
    label: text.slice(0, separatorIndex).trim(),
    value: text.slice(separatorIndex + 1).trim(),
  }
}

export function parseOpportunityLine(line) {
  const matchedLine = cleanLine(line).match(/^(\d+)[.、]\s*(.+?)[｜|](.+)$/)
  if (!matchedLine) return null

  const opportunityParts = matchedLine[3]
    .split(/[；;]/)
    .map((part) => part.trim())
    .filter(Boolean)
  return {
    rank: Number.parseInt(matchedLine[1], 10),
    direction: matchedLine[2].trim(),
    catalyst: labelledValue(opportunityParts, '催化：'),
    confirmation: labelledValue(opportunityParts, '确认：'),
    invalidation: labelledValue(opportunityParts, '失效：'),
  }
}

export function parseStockPickLine(line) {
  const matchedLine = cleanLine(line).match(/^(\d+)[.、]\s*(.+?)[｜|](.+)$/)
  if (!matchedLine) return null

  const identityMatch = matchedLine[2].trim().match(/^(.+?)\s+([A-Za-z0-9.]+)$/)
  if (!identityMatch) return null
  const stockPickParts = matchedLine[3]
    .split(/[；;]/)
    .map((part) => part.trim())
    .filter(Boolean)
  const level = labelledValue(stockPickParts, '级别：')
  const opinion = labelledValue(stockPickParts, '观点：')
  const evidence = labelledValue(stockPickParts, '依据：')
  const trigger = labelledValue(stockPickParts, '触发：')
  const invalidation = labelledValue(stockPickParts, '失效：')
  if (!level || !opinion || !evidence || !trigger || !invalidation) return null

  return {
    rank: Number.parseInt(matchedLine[1], 10),
    level,
    name: identityMatch[1].trim(),
    code: identityMatch[2].trim(),
    opinion,
    evidence,
    trigger,
    invalidation,
    weight: labelledValue(stockPickParts, '仓位：'),
  }
}

export function parseScenarioLine(line) {
  const fact = parseFactLine(line)
  if (!fact.label || !fact.value) return null
  return {
    name: fact.label,
    condition: fact.value,
  }
}

export function parseHoldingLine(line) {
  const parts = String(line || '')
    .split(/[｜|]/)
    .map((part) => part.trim())
    .filter(Boolean)
  if (parts.length < 2) return null

  const identity = parts[0].replace(/^[-•]\s*/, '')
  const identityMatch = identity.match(/^(.+?)\s+([A-Za-z0-9.]+)$/)
  if (!identityMatch) return null

  const weightText = holdingField(parts, '仓位 ')
  const pnlText = holdingField(parts, '盈亏 ')
  const trend = holdingField(parts, '趋势 ')
  const radarMatch = trend.match(/雷达\s*(\d+)\s*\/\s*(\d+)/)

  return {
    name: identityMatch[1].trim(),
    code: identityMatch[2].trim(),
    status: parts[1],
    reason: holdingField(parts, '入选：'),
    weight: metricValue(weightText),
    weightText,
    priceText: holdingField(parts, '价格 '),
    pnl: metricValue(pnlText),
    pnlText,
    trend,
    advice: holdingField(parts, '处理 '),
    radarHit: radarMatch ? Number.parseInt(radarMatch[1], 10) : null,
    radarTotal: radarMatch ? Number.parseInt(radarMatch[2], 10) : null,
  }
}

export function parsePreMarketReport(content) {
  if (!String(content || '').trim()) return emptyReport()

  const parsedReport = emptyReport()
  let currentSection = null
  const lines = String(content).replaceAll('\r\n', '\n').split('\n')

  for (const rawLine of lines) {
    const line = rawLine.trim()
    if (!line || HIDDEN_LINE_PATTERN.test(line)) continue

    const sectionMatch = line.match(SECTION_PATTERN)
    if (sectionMatch) {
      currentSection = {
        number: sectionMatch[1],
        title: sectionMatch[2].trim(),
        lines: [],
        facts: [],
        stockPicks: [],
        opportunities: [],
        holdings: [],
        portfolioRisks: [],
        scenarios: [],
      }
      parsedReport.sections.push(currentSection)
      continue
    }

    if (!currentSection) {
      if (!parsedReport.title && /^今日(?:投资机会|个股观点)[｜|]/.test(line)) parsedReport.title = line
      parsedReport.date ||= fieldValue(line, '日期：')
      parsedReport.judgement ||= fieldValue(line, '核心观点：') || fieldValue(line, '今日判断：')
      parsedReport.priority ||= fieldValue(line, '首选个股：') || fieldValue(line, '优先看：')
      parsedReport.risk ||= fieldValue(line, '最大风险：')
      continue
    }

    currentSection.lines.push(line)
    if (currentSection.number === '04') {
      const holding = parseHoldingLine(line)
      if (holding) {
        currentSection.holdings.push(holding)
      } else if (cleanLine(line).startsWith('组合风险｜')) {
        currentSection.portfolioRisks.push(parseFactLine(line).value)
      }
    }
    parsedReport.priority ||= fieldValue(line, '首选个股：') || fieldValue(line, '优先看：')
    parsedReport.risk ||= fieldValue(line, '最大风险：')
  }

  parsedReport.sections = parsedReport.sections.filter((section) => section.lines.length > 0)
  for (const section of parsedReport.sections) {
    if (section.number === '03') {
      section.stockPicks = section.lines.map(parseStockPickLine).filter(Boolean)
      if (!section.stockPicks.length) {
        section.opportunities = section.lines.map(parseOpportunityLine).filter(Boolean)
      }
    } else if (section.number === '05') {
      section.scenarios = section.lines.map(parseScenarioLine).filter(Boolean)
    } else if (section.number !== '04') {
      section.facts = section.lines.map(parseFactLine)
    }
  }
  if (!parsedReport.priority) {
    const opportunitySection = parsedReport.sections.find((section) => section.title === '投资机会')
    parsedReport.priority = opportunitySection?.lines[0]
      ?.replace(/^\d+[.、]\s*/, '')
      .split(/[｜|]/)[0]
      .trim() || ''
  }
  if (!parsedReport.risk) {
    const riskSection = parsedReport.sections.find((section) => section.title === '组合风险')
    parsedReport.risk = riskSection?.lines[0]?.replace(/^[-•]\s*/, '') || ''
  }
  return parsedReport
}
