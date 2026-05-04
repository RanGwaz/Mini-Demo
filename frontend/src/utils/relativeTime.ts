const MINUTE_MS = 60 * 1000
const HOUR_MS = 60 * MINUTE_MS
const DAY_MS = 24 * HOUR_MS
const WEEK_MS = 7 * DAY_MS
const MONTH_MS = 30 * DAY_MS
const YEAR_MS = 365 * DAY_MS

type RelativeTimeOptions = {
  fallback?: string
}

export function formatRelativeTimeZh(iso?: string | null, options: RelativeTimeOptions = {}) {
  const fallback = options.fallback ?? '刚刚'
  if (!iso) return fallback
  const timestamp = new Date(iso).getTime()
  if (Number.isNaN(timestamp)) return fallback

  const diff = Math.max(0, Date.now() - timestamp)
  if (diff < MINUTE_MS) return '刚刚'
  if (diff < HOUR_MS) return `${Math.floor(diff / MINUTE_MS)}分钟前`
  if (diff < DAY_MS) return `${Math.floor(diff / HOUR_MS)}小时前`
  if (diff < WEEK_MS) return `${Math.floor(diff / DAY_MS)}天前`
  if (diff < MONTH_MS) return `${Math.floor(diff / WEEK_MS)}周前`
  if (diff < YEAR_MS) return `${Math.floor(diff / MONTH_MS)}个月前`
  return `${Math.floor(diff / YEAR_MS)}年前`
}
