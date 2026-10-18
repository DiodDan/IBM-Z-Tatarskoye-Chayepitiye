import { describe, expect, it } from 'vitest'
import { renderMarkdown } from './markdown'

describe('assistant Markdown', () => {
  it('renders headings, emphasis, lists, tables and fenced code', () => {
    const html = renderMarkdown('## Heading\n\n**Bold** and `inline`.\n\n- First\n- Second\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\n```java\nif (x < 2) {}\n```')
    expect(html).toContain('<h2>Heading</h2>')
    expect(html).toContain('<strong>Bold</strong>')
    expect(html).toContain('<ul>')
    expect(html).toContain('<table>')
    expect(html).toContain('class="language-java"')
    expect(html).toContain('x &lt; 2')
  })
  it('does not execute HTML or create script links or external image loads', () => {
    const html = renderMarkdown('<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>\n\n[bad](javascript:alert(1))\n\n![picture](https://example.com/tracker.png)')
    const host = document.createElement('div'); host.innerHTML = html
    expect(host.querySelector('script, img, iframe, svg')).toBeNull()
    expect(host.querySelector('a[href^="javascript:"]')).toBeNull()
  })
  it('opens normal links safely', () => {
    const host = document.createElement('div'); host.innerHTML = renderMarkdown('[Docs](https://example.com)')
    expect(host.querySelector('a')?.getAttribute('rel')).toBe('noopener noreferrer')
    expect(host.querySelector('a')?.getAttribute('target')).toBe('_blank')
  })
})
