// Fake backend for building the UI before the Java backend is ready.
// Data lives in memory and resets when you refresh the page.
import type { ConversationService } from '../services/conversation.service'
import { ApiError } from '../services/http'
import { newId } from '../services/identity.service'
import type { ConversationPreview, Message, MessageExchange } from '../types/api'

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))
const now = () => new Date().toISOString()

const conversations = new Map<string, { preview: ConversationPreview; messages: Message[] }>()

function makeTitle(content: string): string {
  return Array.from(content.replace(/\s+/g, ' ').trim()).slice(0, 80).join('')
}

function makeMessage(role: Message['role'], content: string, status: Message['status'] = 'allowed'): Message {
  return { id: newId(), role, content, createdAt: now(), status }
}

/** Type a message containing "bomb" or "kill" to see what a blocked reply looks like. */
function fakeReply(userText: string): Message {
  if (/\b(bomb|kill)\b/i.test(userText)) {
    return makeMessage('assistant', '', 'blocked')
  }
  return makeMessage('assistant', `(mock reply) You said: "${userText}"`)
}

function sorted(): ConversationPreview[] {
  return [...conversations.values()]
    .map((c) => c.preview)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || a.id.localeCompare(b.id))
}

function find(id: string) {
  const conversation = conversations.get(id)
  if (!conversation) throw new ApiError(404, 'CONVERSATION_NOT_FOUND', 'Conversation not found.')
  return conversation
}

// One example conversation so the sidebar isn't empty.
const seedId = newId()
conversations.set(seedId, {
  preview: { id: seedId, title: 'Example conversation', createdAt: now() },
  messages: [
    makeMessage('user', 'Hi! What can you do?'),
    makeMessage('assistant', 'This is mock data. Send a message to see the chat working.'),
  ],
})

export const mockConversationService: ConversationService = {
  async listConversations() {
    await delay(200)
    const list = sorted()
    return { conversationCount: list.length, conversations: list }
  },

  async getConversation(id) {
    await delay(200)
    const c = find(id)
    return { conversation: { ...c.preview }, messages: [...c.messages] }
  },

  async createConversation(content): Promise<MessageExchange> {
    await delay(800)
    const id = newId()
    const userMessage = makeMessage('user', content)
    const assistantMessage = fakeReply(content)
    const preview = { id, title: makeTitle(content), createdAt: now() }
    conversations.set(id, { preview, messages: [userMessage, assistantMessage] })
    return { conversation: { ...preview }, userMessage, assistantMessage }
  },

  async sendMessage(id, content): Promise<MessageExchange> {
    await delay(800)
    const c = find(id)
    const userMessage = makeMessage('user', content)
    const assistantMessage = fakeReply(content)
    c.messages.push(userMessage, assistantMessage)
    return { conversation: { ...c.preview }, userMessage, assistantMessage }
  },

  async renameConversation(id, title) {
    await delay(200)
    const c = find(id)
    c.preview.title = title.trim()
    return { ...c.preview }
  },

  async deleteConversation(id) {
    await delay(200)
    find(id)
    conversations.delete(id)
  },
}
