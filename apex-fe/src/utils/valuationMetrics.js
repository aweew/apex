const PE_FIELDS = [
  ['peDynamic', '动'],
  ['peStatic', '静'],
  ['peTtm', 'TTM'],
]

export function availablePeMetrics(row = {}) {
  return PE_FIELDS.flatMap(([key, label]) => {
    const value = Number(row[key])
    return Number.isFinite(value) && value > 0
      ? [{ key, label, value: (Math.round((value + Number.EPSILON) * 10) / 10).toFixed(1) }]
      : []
  })
}
