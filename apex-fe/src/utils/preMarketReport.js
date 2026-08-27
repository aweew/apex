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
      }
      parsedReport.sections.push(currentSection)
      continue
    }

    if (!currentSection) {
      if (!parsedReport.title && line === 'Apex 每日盘前研报') parsedReport.title = line
      parsedReport.date ||= fieldValue(line, '日期：')
      parsedReport.judgement ||= fieldValue(line, '今日判断：')
      continue
    }

    currentSection.lines.push(line)
    parsedReport.priority ||= fieldValue(line, '优先看：')
    parsedReport.risk ||= fieldValue(line, '最大风险：')
  }

  parsedReport.sections = parsedReport.sections.filter((section) => section.lines.length > 0)
  if (!parsedReport.priority) {
    const directionSection = parsedReport.sections.find((section) => section.title === '今日方向')
    parsedReport.priority = directionSection?.lines[0]?.replace(/^\d+[.、]\s*/, '') || ''
  }
  if (!parsedReport.risk) {
    const riskSection = parsedReport.sections.find((section) => section.title === '组合风险')
    parsedReport.risk = riskSection?.lines[0]?.replace(/^[-•]\s*/, '') || ''
  }
  return parsedReport
}
