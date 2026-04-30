const STORAGE_KEY = 'imagesocial-theme'

export function initAppTheme() {
  const saved = localStorage.getItem(STORAGE_KEY)
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
  const dark = saved === 'dark' || (saved !== 'light' && prefersDark)
  applyTheme(dark)
}

export function applyTheme(dark: boolean) {
  const root = document.documentElement
  root.classList.toggle('dark', dark)
  root.setAttribute('data-theme', dark ? 'dark' : 'light')
  localStorage.setItem(STORAGE_KEY, dark ? 'dark' : 'light')
}

export function toggleAppTheme() {
  applyTheme(!document.documentElement.classList.contains('dark'))
}

export function isDarkTheme() {
  return document.documentElement.classList.contains('dark')
}
