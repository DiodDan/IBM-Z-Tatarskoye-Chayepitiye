import { ref, watch } from 'vue'
import { applyTheme, loadTheme, saveTheme, type Theme } from '../services/theme.service'

export function useTheme() {
  const theme = ref<Theme>(loadTheme())

  watch(
    theme,
    (value) => {
      applyTheme(value)
      saveTheme(value)
    },
    { immediate: true },
  )

  function setTheme(value: Theme) {
    theme.value = value
  }

  return { theme, setTheme }
}
