export type Theme = 'light' | 'dark'

const STORAGE_KEY = 'theme'

export function loadTheme(): Theme {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'light' || saved === 'dark') return saved
  } catch {
    /* storage unavailable */
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function saveTheme(theme: Theme): void {
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    /* storage unavailable — keep in memory only */
  }
}

export function applyTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme
}
