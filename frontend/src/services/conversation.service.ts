import { request } from './http'
import { mockConversationService } from '../mocks/mock.service'
import type {
  ConversationHistory,
  ConversationList,
  ConversationPreview,
  MessageExchange,
} from '../types/api'

export interface ConversationService {
  listConversations(): Promise<ConversationList>
  getConversation(id: string, signal?: AbortSignal): Promise<ConversationHistory>
  createConversation(content: string): Promise<MessageExchange>
  sendMessage(id: string, content: string): Promise<MessageExchange>
  renameConversation(id: string, title: string): Promise<ConversationPreview>
  deleteConversation(id: string): Promise<void>
}

const realConversationService: ConversationService = {
  listConversations: () => request<ConversationList>('/conversations'),

  getConversation: (id, signal) =>
    request<ConversationHistory>(`/conversations/${id}`, { signal }),

  createConversation: (content) =>
    request<MessageExchange>('/conversations', { method: 'POST', body: { content } }),

  sendMessage: (id, content) =>
    request<MessageExchange>(`/conversations/${id}/messages`, {
      method: 'POST',
      body: { content },
    }),

  renameConversation: (id, title) =>
    request<ConversationPreview>(`/conversations/${id}`, { method: 'PATCH', body: { title } }),

  deleteConversation: (id) => request<void>(`/conversations/${id}`, { method: 'DELETE' }),
}

// `npm run dev:mock` runs Vite in "mock" mode -> fake data, no backend needed.
export const conversationService: ConversationService =
  import.meta.env.MODE === 'mock' ? mockConversationService : realConversationService