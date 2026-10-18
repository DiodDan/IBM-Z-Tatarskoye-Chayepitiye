import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'

const markdown = new MarkdownIt({ html: false, linkify: true, breaks: true })
// Model-generated images must not automatically request arbitrary external URLs.
markdown.renderer.rules.image = (tokens, index) =>
  `<span class="markdown-image">[Image: ${markdown.utils.escapeHtml(tokens[index]?.content ?? '')}]</span>`
const defaultLink = markdown.renderer.rules.link_open
markdown.renderer.rules.link_open = (tokens, index, options, env, renderer) => {
  const token = tokens[index]!
  token.attrSet('target', '_blank')
  token.attrSet('rel', 'noopener noreferrer')
  return defaultLink ? defaultLink(tokens, index, options, env, renderer) : renderer.renderToken(tokens, index, options)
}

/** Used only for assistant text; user messages and follow-up labels stay plain text. */
export function renderMarkdown(text: string): string {
  return DOMPurify.sanitize(markdown.render(text), {
    ALLOWED_TAGS: ['p', 'br', 'hr', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'strong', 'em', 's',
      'ul', 'ol', 'li', 'blockquote', 'pre', 'code', 'a', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'span'],
    ALLOWED_ATTR: ['href', 'title', 'target', 'rel', 'class', 'start'],
    ALLOW_DATA_ATTR: false,
  })
}
