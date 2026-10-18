import type { ModelAssessment, TrajectoryDetail } from '../types/model'
import { categoryLabels } from '../types/model'
import { request } from './http'
import { mockConversationService } from '../mocks/mock.service'
import type {
  AdminConversation,
  AdminConversationHistory,
  AdminConversationList,
  AdminMessage,
  AdminResponse,
  AdminResponseList,
  ResponseSort,
} from '../types/admin'

export interface AdminService {
  /** All conversations, or with `query` only those matching a pasted response number or text. */
  listConversations(query?: string): Promise<AdminConversationList>
  getConversation(id: string, signal?: AbortSignal): Promise<AdminConversationHistory>
  listResponses(sort: ResponseSort): Promise<AdminResponseList>
  getTrajectory(id: string): Promise<TrajectoryDetail>
}

/** Read-only admin API. No login yet: see AdminController.kt before deploying. */
const realAdminService: AdminService = {
  getTrajectory: (id) => request<TrajectoryDetail>(`/admin/trajectories/${id}`),
  listConversations: (query) =>
    request<AdminConversationList>(
      query?.trim() ? `/admin/conversations?q=${encodeURIComponent(query)}` : '/admin/conversations',
    ),
  getConversation: (id, signal) => request<AdminConversationHistory>(`/admin/conversations/${id}`, { signal }),
  listResponses: (sort) => request<AdminResponseList>(`/admin/responses?sort=${sort}`),
}

// ---------- `npm run dev:mock`: the mock chats, with demo risk data on user prompts ----------

const MOCK_USER = '00000000-0000-4000-8000-000000000000'
const MOCK_THRESHOLD = 0.5

function demoAssessment(number: number): ModelAssessment {
  const value = [0.12, 0.73, 0.34][number % 3] ?? 0.12
  const scores = { overall: value, categories: Object.fromEntries(Object.keys(categoryLabels).map((key, i) => [key, Math.max(0.01, value - i * 0.04)])), calibrationId: 'demo-uncalibrated', calibrationApplied: false }
  return { analysis: { status: 'completed', modelFingerprint: 'demo-model-fingerprint', atomic: scores,
    trajectory: { id: `demo-trajectory-${number}`, responseCount: 1, lastPosition: number, scores: null, associationScore: null },
    coverage: { status: 'complete', totalTokens: 80, processedTokens: 80, windowCount: 1 },
    windowPeak: null, errorCode: null, message: null },
    membership: { ordinal: 1, associationLogit: null, linkThreshold: null, status: 'active', updatedAt: new Date().toISOString(), expiresAt: new Date(Date.now() + 86400000).toISOString() } }
}

async function mockHistories() {
  const list = await mockConversationService.listConversations()
  let number = 0
  const histories = []
  for (const preview of [...list.conversations].reverse()) {
    const history = await mockConversationService.getConversation(preview.id)
    const messages: AdminMessage[] = history.messages.map((m, index) => ({
      ...m,
      promptRisk: m.role === 'user' ? ([0.28, 0.72, null][index % 3] ?? null) : null,
      promptRiskReason: m.role === 'user' && index % 3 !== 2 ? 'mock classifier reason' : null,
      responseTimeMs: m.role === 'assistant' ? 1200 : null,
      responseNumber: m.role === 'assistant' ? ++number : null,
      modelAssessment: m.role === 'assistant' ? demoAssessment(number) : null,
    }))
    histories.push({ conversation: { ...history.conversation, userId: MOCK_USER } as AdminConversation, messages })
  }
  return histories
}

const mockAdminService: AdminService = {
  async getTrajectory(id) {
    const histories = await mockHistories()
    for (const { conversation, messages } of histories) {
      const message = messages.find((m) => m.modelAssessment?.analysis.trajectory?.id === id)
      const a = message?.modelAssessment?.analysis
      if (message && a?.atomic && a.trajectory) return {
        id, status: 'active', modelFingerprint: 'demo-model-fingerprint', responseCount: 1,
        scores: a.atomic, updatedAt: message.createdAt,
        members: [{ messageId: message.id, responseNumber: message.responseNumber,
          conversationId: conversation.id, content: message.content, ordinal: 1, atomic: a.atomic,
          associationScore: null, associationLogit: null, linkThreshold: null }],
      }
    }
    throw new Error('Trajectory not found.')
  },
  async listConversations(query) {
    const histories = await mockHistories()
    const q = query?.trim().toLowerCase()
    const num = q?.match(/^(?:response\s*)?(?:№|no\.?|#)?\s*(\d+)$/)?.[1]
    const conversations = histories
      .filter((h) => !q || h.conversation.title.toLowerCase().includes(q) || h.messages.some((m) =>
        m.content.toLowerCase().includes(q) || (num !== undefined && m.responseNumber === Number(num))))
      .map((h) => ({
        ...h.conversation,
        matchedMessageId: q
          ? (h.messages.find((m) => m.content.toLowerCase().includes(q) || m.responseNumber === Number(num))?.id ?? null)
          : null,
      }))
      .reverse()
    return { conversationCount: conversations.length, conversations }
  },
  async getConversation(id) {
    const found = (await mockHistories()).find((h) => h.conversation.id === id)
    if (!found) throw new Error('Conversation not found.')
    return { ...found, riskThreshold: MOCK_THRESHOLD }
  },
  async listResponses(sort) {
    const responses: AdminResponse[] = []
    for (const { conversation, messages } of await mockHistories()) {
      messages.forEach((m, i) => {
        if (m.role !== 'assistant' || m.responseNumber === null) return
        const prompt = messages[i - 1]
        responses.push({
          responseNumber: m.responseNumber,
          conversationId: conversation.id,
          conversationTitle: conversation.title,
          userId: conversation.userId,
          messageId: m.id,
          prompt: prompt?.content ?? '',
          content: m.content,
          createdAt: m.createdAt,
          promptRisk: prompt?.promptRisk ?? null,
          promptRiskReason: prompt?.promptRiskReason ?? null,
          responseTimeMs: m.responseTimeMs,
          modelAssessment: m.modelAssessment,
        })
      })
    }
    const nullsLast = (a: number | null, b: number | null, dir: number) =>
      a === null ? (b === null ? 0 : 1) : b === null ? -1 : (a - b) * dir
    responses.sort((a, b) =>
      sort === 'risk-asc'
        ? nullsLast(a.modelAssessment?.analysis.atomic?.overall ?? null, b.modelAssessment?.analysis.atomic?.overall ?? null, 1)
        : sort === 'risk-desc'
          ? nullsLast(a.modelAssessment?.analysis.atomic?.overall ?? null, b.modelAssessment?.analysis.atomic?.overall ?? null, -1)
          : sort === 'reason'
            ? (a.promptRiskReason ?? '￿').localeCompare(b.promptRiskReason ?? '￿')
            : 0,
    )
    if (sort === 'newest') responses.sort((a, b) => b.responseNumber - a.responseNumber)
    return { responses, riskThreshold: MOCK_THRESHOLD }
  },
}

export const adminService: AdminService = import.meta.env.MODE === 'mock' ? mockAdminService : realAdminService
