/** Display fallback for older stored replies whose follow-up suffix was not parsed by the backend. */
export function presentResponse(text: string, suggestions: string[] = []) {
  if (suggestions.length) return { text, suggestions }
  const trimmed = text.trimEnd()
  const start = trimmed.lastIndexOf('\n') + 1
  const suffixStart = trimmed.lastIndexOf('[[')
  if (suffixStart < start) return { text, suggestions }
  const line = trimmed.slice(suffixStart).trim()
  let fence: string | null = null
  for (const preceding of trimmed.slice(0, start).split('\n')) {
    const match = preceding.match(/^ {0,3}(`{3,}|~{3,})(.*)$/)
    if (!match) continue
    const run = match[1]!
    if (!fence) fence = run
    else if (run[0] === fence[0] && run.length >= fence.length && !match[2]?.trim()) fence = null
  }
  if (fence) return { text, suggestions }
  const explicit = line.match(/^\[\[\s*suggestions\s*\]\]\s*:?\s*(.*)$/i)
  const payload = explicit?.[1] ?? (line.startsWith('[[') && line.endsWith(']') && line.includes('|') ? line.slice(2) : null)
  if (payload == null) return { text, suggestions }
  const parsed = [...new Set(payload.split('|').map((item) => item.trim().replace(/^[\[\]"'*\-]+|[\[\]"'*\-]+$/g, '').trim())
    .filter((item) => item.length > 0 && Array.from(item).length <= 40))].slice(0, 3)
  const body = trimmed.slice(0, suffixStart).trim()
  return parsed.length && body ? { text: body, suggestions: parsed } : { text, suggestions }
}
