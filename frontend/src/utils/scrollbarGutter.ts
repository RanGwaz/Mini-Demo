/**
 * 方案二：动态测量「经典滚动条轨道宽度」（非 overlay 时与布局占位一致）。
 * 与 CSS `scrollbar-gutter: stable` 叠加：stable 负责日常占位；本值可用于
 * 自定义锁滚（body overflow:hidden）时写 --doc-scrollbar-width 做 padding 补偿。
 *
 * 说明：macOS / 部分浏览器为 overlay 滚动条时测量结果为 0，此时应依赖 stable gutter，
 * 避免重复加 padding。
 */
export function measureClassicScrollbarWidth(): number {
  const outer = document.createElement('div')
  outer.style.visibility = 'hidden'
  outer.style.overflow = 'scroll'
  outer.style.width = '100px'
  outer.style.height = '100px'
  outer.style.position = 'absolute'
  outer.style.top = '-9999px'
  document.body.appendChild(outer)
  const inner = document.createElement('div')
  inner.style.width = '100%'
  inner.style.height = '200px'
  outer.appendChild(inner)
  const w = outer.offsetWidth - inner.clientWidth
  outer.remove()
  return w
}

/** 写入 :root CSS 变量，供需要时 `padding-right: var(--doc-scrollbar-width)` */
export function syncDocumentScrollbarCssVar(): void {
  const w = measureClassicScrollbarWidth()
  document.documentElement.style.setProperty('--doc-scrollbar-width', `${w}px`)
}

export function installScrollbarWidthVarSync(): void {
  const run = () => syncDocumentScrollbarCssVar()
  if (document.readyState === 'complete') {
    requestAnimationFrame(run)
  } else {
    window.addEventListener('load', () => requestAnimationFrame(run), { once: true })
  }
  window.addEventListener('resize', run, { passive: true })
}
