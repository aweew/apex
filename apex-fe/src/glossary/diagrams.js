/**
 * 名词百科内联图解：按 diagram id 返回 SVG 字符串（纯矢量，无外链图片）。
 * 词条在 terms.js 里写 diagram: 'max_drawdown' 即可挂载。
 */

const W = 520
const H = 168

function svg(body, opts = {}) {
  const w = opts.w || W
  const h = opts.h || H
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" width="100%" role="img" aria-label="${opts.label || '图解'}">${body}</svg>`
}

function caption(x, y, text, fill = '#86868b') {
  return `<text x="${x}" y="${y}" fill="${fill}" font-size="11" font-family="system-ui,sans-serif">${text}</text>`
}

/** @type {Record<string, () => string>} */
const BUILDERS = {
  max_drawdown() {
    return svg(
      `
      <rect x="0" y="0" width="${W}" height="${H}" rx="12" fill="#f5f7fa"/>
      <path d="M36 118 C70 110,90 70,130 62 C170 54,190 78,230 48 C270 18,300 40,340 34 C380 28,410 70,460 58 L460 130 L36 130 Z" fill="rgba(0,113,227,.08)"/>
      <path d="M36 118 C70 110,90 70,130 62 C170 54,190 78,230 48 C270 18,300 40,340 34 C380 28,410 70,460 58" fill="none" stroke="#0071e3" stroke-width="2.4"/>
      <circle cx="230" cy="48" r="4.5" fill="#0071e3"/>
      <circle cx="410" cy="70" r="4.5" fill="#c0392b"/>
      <path d="M230 48 L230 70 L410 70" fill="none" stroke="#c0392b" stroke-width="1.6" stroke-dasharray="4 3"/>
      <path d="M410 70 L410 48" fill="none" stroke="#c0392b" stroke-width="1.6"/>
      ${caption(236, 42, '峰值')}
      ${caption(416, 64, '谷底', '#c0392b')}
      ${caption(288, 86, '最大回撤', '#c0392b')}
      ${caption(36, 152, '净值曲线：高点 → 之后最低点的最大跌幅')}
      `,
      { label: '最大回撤图解' },
    )
  },

  sharpe() {
    return svg(
      `
      <rect x="0" y="0" width="${W}" height="${H}" rx="12" fill="#f5f7fa"/>
      <line x1="56" y1="128" x2="480" y2="128" stroke="#d2d2d7" stroke-width="1.5"/>
      <line x1="56" y1="128" x2="56" y2="28" stroke="#d2d2d7" stroke-width="1.5"/>
      ${caption(470, 144, '波动')}
      ${caption(18, 36, '收益')}
      <circle cx="170" cy="88" r="9" fill="rgba(0,113,227,.2)" stroke="#0071e3" stroke-width="2"/>
      <circle cx="320" cy="52" r="9" fill="rgba(52,199,89,.2)" stroke="#34c759" stroke-width="2"/>
      <circle cx="390" cy="96" r="9" fill="rgba(192,57,43,.18)" stroke="#c0392b" stroke-width="2"/>
      ${caption(152, 72, 'A 低夏普')}
      ${caption(300, 36, 'B 高夏普')}
      ${caption(368, 118, 'C 高波低收益')}
      ${caption(56, 152, '同样收益波动越小，或同样波动收益越高 → 夏普越高')}
      `,
      { label: '夏普比率图解' },
    )
  },

  macd() {
    return svg(
      `
      <rect x="0" y="0" width="${W}" height="${H}" rx="12" fill="#f5f7fa"/>
      <line x1="40" y1="78" x2="490" y2="78" stroke="#d2d2d7" stroke-width="1"/>
      <path d="M40 110 C90 120,120 40,170 50 C220 60,250 100,300 70 C350 40,390 30,450 55" fill="none" stroke="#0071e3" stroke-width="2.2"/>
      <path d="M40 118 C100 122,140 70,190 72 C240 74,270 96,320 80 C370 64,410 50,450 62" fill="none" stroke="#86868b" stroke-width="1.8"/>
      <rect x="120" y="78" width="10" height="28" fill="rgba(192,57,43,.55)"/>
      <rect x="150" y="50" width="10" height="28" fill="rgba(52,199,89,.55)"/>
      <rect x="260" y="78" width="10" height="18" fill="rgba(192,57,43,.45)"/>
      <rect x="340" y="48" width="10" height="30" fill="rgba(52,199,89,.55)"/>
      <circle cx="170" cy="50" r="4" fill="#34c759"/>
      ${caption(176, 44, '金叉', '#34c759')}
      ${caption(48, 28, '蓝=DIF · 灰=DEA · 柱=动能')}
      ${caption(40, 152, 'DIF 上穿 DEA=金叉偏多；柱由负转正常伴随动能增强')}
      `,
      { label: 'MACD图解' },
    )
  },

  boll() {
    return svg(
      `
      <rect x="0" y="0" width="${W}" height="${H}" rx="12" fill="#f5f7fa"/>
      <path d="M40 48 C110 30,180 55,250 42 C320 28,390 50,480 38" fill="none" stroke="#0071e3" stroke-width="1.4" stroke-dasharray="4 3"/>
      <path d="M40 88 C110 80,180 92,250 84 C320 76,390 90,480 82" fill="none" stroke="#1d1d1f" stroke-width="1.8"/>
      <path d="M40 128 C110 130,180 118,250 126 C320 134,390 120,480 128" fill="none" stroke="#0071e3" stroke-width="1.4" stroke-dasharray="4 3"/>
      <path d="M40 48 C110 30,180 55,250 42 C320 28,390 50,480 38 L480 128 C390 120,320 134,250 126 C180 118,110 130,40 128 Z" fill="rgba(0,113,227,.06)"/>
      <path d="M40 100 C100 70,150 110,210 60 C270 20,320 100,380 70 C420 55,450 90,480 78" fill="none" stroke="#c0392b" stroke-width="2"/>
      ${caption(486, 42, '上轨')}
      ${caption(486, 86, '中轨')}
      ${caption(486, 132, '下轨')}
      ${caption(40, 152, '带宽扩张=波动加大；收窄常酝酿变盘；贴轨需结合趋势')}
      `,
      { label: '布林带图解' },
    )
  },

  rsi() {
    return svg(
      `
      <rect x="0" y="0" width="${W}" height="${H}" rx="12" fill="#f5f7fa"/>
      <rect x="48" y="28" width="440" height="28" fill="rgba(192,57,43,.1)"/>
      <rect x="48" y="108" width="440" height="28" fill="rgba(52,199,89,.1)"/>
      <line x1="48" y1="42" x2="488" y2="42" stroke="#c0392b" stroke-width="1" stroke-dasharray="3 3"/>
      <line x1="48" y1="82" x2="488" y2="82" stroke="#d2d2d7" stroke-width="1"/>
      <line x1="48" y1="122" x2="488" y2="122" stroke="#34c759" stroke-width="1" stroke-dasharray="3 3"/>
      <path d="M48 100 C90 110,120 40,170 50 C220 60,260 130,310 115 C360 100,400 30,450 55 C470 65,480 70,488 68" fill="none" stroke="#0071e3" stroke-width="2.2"/>
      ${caption(494, 46, '70')}
      ${caption(494, 126, '30')}
      ${caption(56, 48, '超买区', '#c0392b')}
      ${caption(56, 128, '超卖区', '#34c759')}
      ${caption(48, 152, '强趋势中会钝化：高位/低位可长期停留，勿机械反向')}
      `,
      { label: 'RSI图解' },
    )
  },

  ma_cross() {
    return svg(
      `
      <rect x="0" y="0" width="${W}" height="${H}" rx="12" fill="#f5f7fa"/>
      <path d="M40 110 C100 105,140 90,200 70 C260 50,320 55,400 40 C440 34,470 30,490 28" fill="none" stroke="#0071e3" stroke-width="2.2"/>
      <path d="M40 90 C110 95,160 88,220 78 C280 68,340 72,420 58 C450 52,470 50,490 48" fill="none" stroke="#86868b" stroke-width="1.8"/>
      <circle cx="210" cy="72" r="5" fill="#34c759"/>
      ${caption(218, 66, '金叉：快线上穿慢线', '#34c759')}
      ${caption(48, 28, '蓝=短均线 · 灰=长均线')}
      ${caption(40, 152, '金叉偏多、死叉偏空；震荡市假信号多，宜配量能/趋势过滤')}
      `,
      { label: '均线金叉图解' },
    )
  },

  kelly() {
    return svg(
      `
      <rect x="0" y="0" width="${W}" height="${H}" rx="12" fill="#f5f7fa"/>
      <line x1="48" y1="130" x2="480" y2="130" stroke="#d2d2d7"/>
      <line x1="48" y1="130" x2="48" y2="24" stroke="#d2d2d7"/>
      <path d="M48 120 C120 40,200 30,260 55 C320 80,380 115,460 128" fill="none" stroke="#0071e3" stroke-width="2.2"/>
      <line x1="260" y1="55" x2="260" y2="130" stroke="#34c759" stroke-width="1.5" stroke-dasharray="4 3"/>
      <line x1="180" y1="42" x2="180" y2="130" stroke="#86868b" stroke-width="1.2" stroke-dasharray="3 3"/>
      ${caption(248, 48, '全Kelly')}
      ${caption(156, 36, '半Kelly')}
      ${caption(470, 146, '仓位')}
      ${caption(18, 32, '增长')}
      ${caption(40, 152, '全Kelly常过激；实盘多用半Kelly或更保守折扣作上限')}
      `,
      { label: 'Kelly仓位图解' },
    )
  },

  promote_rate() {
    return svg(
      `
      <rect x="0" y="0" width="${W}" height="${H}" rx="12" fill="#f5f7fa"/>
      <rect x="56" y="96" width="70" height="36" rx="8" fill="rgba(0,113,227,.12)" stroke="#0071e3"/>
      <rect x="176" y="72" width="70" height="60" rx="8" fill="rgba(0,113,227,.16)" stroke="#0071e3"/>
      <rect x="296" y="48" width="70" height="84" rx="8" fill="rgba(0,113,227,.22)" stroke="#0071e3"/>
      <rect x="416" y="28" width="70" height="104" rx="8" fill="rgba(192,57,43,.18)" stroke="#c0392b"/>
      ${caption(74, 118, '1板')}
      ${caption(194, 106, '2板')}
      ${caption(314, 94, '3板')}
      ${caption(430, 84, '空间')}
      <path d="M126 114 L176 102" fill="none" stroke="#34c759" stroke-width="2" marker-end="url(#arr)"/>
      <defs><marker id="arr" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto"><path d="M0,0 L6,3 L0,6 Z" fill="#34c759"/></marker></defs>
      ${caption(130, 96, '晋级', '#34c759')}
      ${caption(40, 152, '晋级率 ≈ 今日N板家数 ÷ 昨日(N−1)板家数；基数太小时比例不稳')}
      `,
      { label: '晋级率图解' },
    )
  },

  confluence() {
    return svg(
      `
      <rect x="0" y="0" width="${W}" height="${H}" rx="12" fill="#f5f7fa"/>
      <circle cx="210" cy="78" r="52" fill="rgba(0,113,227,.12)" stroke="#0071e3" stroke-width="1.5"/>
      <circle cx="290" cy="78" r="52" fill="rgba(52,199,89,.12)" stroke="#34c759" stroke-width="1.5"/>
      <circle cx="250" cy="118" r="52" fill="rgba(255,149,0,.12)" stroke="#ff9500" stroke-width="1.5"/>
      ${caption(188, 58, 'S1')}
      ${caption(292, 58, 'S2')}
      ${caption(236, 138, 'S3')}
      ${caption(232, 88, '共振', '#0071e3')}
      ${caption(40, 152, '多策略同向点名提高优先级；同质规则也可能一起在震荡市失效')}
      `,
      { label: '策略共振图解' },
    )
  },

  pe_percentile() {
    return svg(
      `
      <rect x="0" y="0" width="${W}" height="${H}" rx="12" fill="#f5f7fa"/>
      <path d="M48 130 C90 128,110 40,180 40 C250 40,270 128,320 128 C370 128,400 70,470 70" fill="none" stroke="#0071e3" stroke-width="2"/>
      <path d="M48 130 C90 128,110 40,180 40 C250 40,270 128,320 128 L48 128 Z" fill="rgba(0,113,227,.1)"/>
      <line x1="270" y1="30" x2="270" y2="130" stroke="#c0392b" stroke-width="1.6" stroke-dasharray="4 3"/>
      ${caption(248, 24, '当前PE', '#c0392b')}
      ${caption(56, 100, '更便宜样本')}
      ${caption(300, 100, '更贵样本')}
      ${caption(40, 152, '分位80%≈比约80%样本更贵；低分位≠必买，可能基本面恶化')}
      `,
      { label: 'PE分位图解' },
    )
  },

  var_cvar() {
    return svg(
      `
      <rect x="0" y="0" width="${W}" height="${H}" rx="12" fill="#f5f7fa"/>
      <path d="M60 120 C100 118,130 40,220 40 C310 40,340 110,400 120 C430 125,450 128,470 130" fill="none" stroke="#0071e3" stroke-width="2"/>
      <path d="M60 120 C100 118,130 40,220 40 C250 40,270 70,290 90 L60 120 Z" fill="rgba(0,113,227,.08)"/>
      <path d="M60 120 C80 119,95 100,110 95 C125 110,140 118,160 120 Z" fill="rgba(192,57,43,.35)"/>
      <line x1="160" y1="40" x2="160" y2="130" stroke="#c0392b" stroke-width="1.5" stroke-dasharray="4 3"/>
      ${caption(166, 56, 'VaR门槛')}
      ${caption(70, 108, 'CVaR', '#fff')}
      ${caption(40, 152, 'VaR=损失门槛；CVaR=跌破后尾部平均损失（通常更保守）')}
      `,
      { label: 'VaR与CVaR图解' },
    )
  },

  atr_stop() {
    return svg(
      `
      <rect x="0" y="0" width="${W}" height="${H}" rx="12" fill="#f5f7fa"/>
      <path d="M48 100 C90 70,130 110,180 80 C230 50,280 90,330 60 C380 30,430 70,480 55" fill="none" stroke="#1d1d1f" stroke-width="2"/>
      <circle cx="280" cy="78" r="5" fill="#0071e3"/>
      ${caption(288, 72, '入场')}
      <line x1="220" y1="110" x2="480" y2="110" stroke="#c0392b" stroke-width="1.6" stroke-dasharray="5 3"/>
      <line x1="220" y1="40" x2="480" y2="40" stroke="#34c759" stroke-width="1.6" stroke-dasharray="5 3"/>
      <path d="M280 78 L280 110" fill="none" stroke="#c0392b" stroke-width="1.2"/>
      ${caption(486, 114, '止损≈价−k·ATR', '#c0392b')}
      ${caption(486, 44, '止盈≈价+m·ATR', '#34c759')}
      ${caption(40, 152, '波动大时ATR变大，止损自动放宽；波动收敛时收紧')}
      `,
      { label: 'ATR止损图解' },
    )
  },

  hhi() {
    return svg(
      `
      <rect x="0" y="0" width="${W}" height="${H}" rx="12" fill="#f5f7fa"/>
      <rect x="70" y="50" width="160" height="70" rx="8" fill="rgba(192,57,43,.2)" stroke="#c0392b"/>
      <rect x="70" y="50" width="96" height="70" rx="8" fill="rgba(192,57,43,.45)"/>
      ${caption(100, 90, '集中', '#fff')}
      ${caption(90, 136, 'HHI高：少数票占大头')}
      <rect x="300" y="60" width="36" height="50" rx="4" fill="rgba(0,113,227,.55)"/>
      <rect x="342" y="70" width="36" height="40" rx="4" fill="rgba(0,113,227,.45)"/>
      <rect x="384" y="55" width="36" height="55" rx="4" fill="rgba(0,113,227,.4)"/>
      <rect x="426" y="75" width="36" height="35" rx="4" fill="rgba(0,113,227,.35)"/>
      <rect x="468" y="65" width="28" height="45" rx="4" fill="rgba(0,113,227,.3)"/>
      ${caption(340, 136, '分散：HHI更低')}
      ${caption(40, 152, 'HHI=权重平方和；越接近1越集中，单票黑天鹅冲击越大')}
      `,
      { label: '持仓集中度图解' },
    )
  },

  qfq() {
    return svg(
      `
      <rect x="0" y="0" width="${W}" height="${H}" rx="12" fill="#f5f7fa"/>
      <path d="M40 90 L160 70 L200 40 L200 100 L280 85 L360 60 L460 50" fill="none" stroke="#c0392b" stroke-width="1.8" stroke-dasharray="4 3"/>
      <path d="M40 100 L160 85 L280 70 L360 55 L460 48" fill="none" stroke="#0071e3" stroke-width="2.2"/>
      <line x1="200" y1="28" x2="200" y2="130" stroke="#86868b" stroke-width="1" stroke-dasharray="3 3"/>
      ${caption(206, 24, '除权日')}
      ${caption(48, 60, '不复权跳空', '#c0392b')}
      ${caption(48, 118, '前复权连续', '#0071e3')}
      ${caption(40, 152, '前复权固定最新价回推历史，适合看形态与均线')}
      `,
      { label: '前复权图解' },
    )
  },

  emotion_cycle() {
    return svg(
      `
      <rect x="0" y="0" width="${W}" height="${H}" rx="12" fill="#f5f7fa"/>
      <path d="M40 120 C90 118,120 90,160 70 C200 50,230 40,270 36 C310 40,340 55,380 85 C420 115,450 125,480 128" fill="none" stroke="#0071e3" stroke-width="2.2"/>
      ${caption(70, 110, '冰点')}
      ${caption(150, 58, '启动')}
      ${caption(250, 28, '高潮')}
      ${caption(360, 72, '分歧')}
      ${caption(440, 118, '退潮')}
      ${caption(40, 152, '看晋级率、炸板、赚钱效应定位阶段；勿在退潮期无纪律追高')}
      `,
      { label: '情绪周期图解' },
    )
  },

  win_payoff() {
    return svg(
      `
      <rect x="0" y="0" width="${W}" height="${H}" rx="12" fill="#f5f7fa"/>
      <line x1="48" y1="130" x2="480" y2="130" stroke="#d2d2d7"/>
      <line x1="48" y1="130" x2="48" y2="24" stroke="#d2d2d7"/>
      <path d="M70 110 L200 40" fill="none" stroke="#34c759" stroke-width="2"/>
      <path d="M70 110 L200 150" fill="none" stroke="#c0392b" stroke-width="2"/>
      ${caption(470, 146, '盈亏比')}
      ${caption(18, 32, '胜率')}
      <circle cx="150" cy="70" r="7" fill="#34c759"/>
      <circle cx="320" cy="95" r="7" fill="#0071e3"/>
      ${caption(158, 64, '高赔低胜')}
      ${caption(328, 90, '高胜低赔')}
      ${caption(40, 152, '期望≈胜率×均盈−败率×均亏；两条路线都能赚，关键看期望为正')}
      `,
      { label: '胜率与盈亏比图解' },
    )
  },

  beta() {
    return svg(
      `
      <rect x="0" y="0" width="${W}" height="${H}" rx="12" fill="#f5f7fa"/>
      <line x1="48" y1="130" x2="480" y2="130" stroke="#d2d2d7"/>
      <line x1="48" y1="130" x2="48" y2="24" stroke="#d2d2d7"/>
      <path d="M48 130 L460 50" fill="none" stroke="#0071e3" stroke-width="2"/>
      <path d="M48 130 L460 90" fill="none" stroke="#86868b" stroke-width="1.6" stroke-dasharray="4 3"/>
      <path d="M48 130 L300 40" fill="none" stroke="#c0392b" stroke-width="1.8"/>
      ${caption(470, 146, '基准')}
      ${caption(18, 32, '组合')}
      ${caption(400, 48, 'β>1')}
      ${caption(400, 88, 'β≈1')}
      ${caption(280, 36, 'β更高', '#c0392b')}
      ${caption(40, 152, 'β≈1跟基准；>1放大涨跌；想降波动可降仓或换低β标的')}
      `,
      { label: 'Beta图解' },
    )
  },
}

/**
 * 获取图解 SVG；未知 id 返回空字符串
 * @param {string} diagramId
 * @returns {string}
 */
export function getDiagramSvg(diagramId) {
  if (!diagramId) return ''
  const builder = BUILDERS[diagramId]
  if (!builder) return ''
  return builder()
}

/** 已注册的图解 id 列表 */
export function listDiagramIds() {
  return Object.keys(BUILDERS)
}
