export function numberText(value?: number | null, digits = 2): string {
  return value === null || value === undefined ? '--' : Number(value).toFixed(digits)
}

export function percentText(value?: number | null): string {
  return value === null || value === undefined ? '--' : `${value > 0 ? '+' : ''}${numberText(value)}%`
}

export function changeClass(value?: number | null): string {
  return Number(value) < 0 ? 'down' : 'up'
}
