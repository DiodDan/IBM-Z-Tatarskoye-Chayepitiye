import { request } from './http'
import { mockConversationService } from '../mocks/mock.service'
import type { AdminConversationHistory, AdminConversationList } from '../types/admin'

export interface AdminService {
  listConversations(): Promise<AdminConversationList>
  getConversation(id: string, signal?: AbortSignal): Promise<AdminConversationHistory>
}

/** Read-only admin API. No login yet: see AdminController.kt before deploying. */
const realAdminService: AdminService = {
  listConversations: () => request<AdminConversationList>('/admin/conversations'),
  getConversation: (id, signal) => request<AdminConversationHistory>(`/admin/conversations/${id}`, { signal }),
}

const MOCK_USER = '00000000-0000-4000-8000-000000000000'

/** `npm run dev:mock`: the mock chats, with demo risk data on user prompts. */
const mockAdminService: AdminService = {
  async listConversations() {
    const list = await mockConversationService.listConversations()
    return { ...list, conversations: list.conversations.map((c) => ({ ...c, userId: MOCK_USER })) }
  },
  async getConversation(id) {
    const history = await mockConversationService.getConversation(id)
    return {
      conversation: { ...history.conversation, userId: MOCK_USER },
      riskThreshold: 0.5,
      messages: history.messages.map((m, index) => ({
        ...m,
        promptRisk: m.role === 'user' ? [0.28, 0.72, null][index % 3] ?? null : null,
        promptRiskReason: m.role === 'user' && index % 3 !== 2 ? 'mock classifier reason' : null,
        responseTimeMs: m.role === 'assistant' ? 1200 : null,
      })),
    }
  },
}

export const adminService: AdminService = import.meta.env.MODE === 'mock' ? mockAdminService : realAdminService
