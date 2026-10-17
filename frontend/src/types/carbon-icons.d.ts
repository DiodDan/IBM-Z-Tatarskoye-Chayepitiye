// @carbon/icons-vue ships no type definitions. Each icon is imported from its own file
// (e.g. '@carbon/icons-vue/es/chip/20') so only the icons we use end up in the bundle.
declare module '@carbon/icons-vue/es/*' {
  import type { DefineComponent } from 'vue'
  const icon: DefineComponent<{ title?: string }>
  export default icon
}
