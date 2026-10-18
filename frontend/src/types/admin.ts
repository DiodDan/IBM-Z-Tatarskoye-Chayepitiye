import type { ConversationPreview, Message } from './api'

/** A conversation as the admin sees it: any user's, labelled with the owner's anonymous id. */
export interface AdminConversation extends ConversationPreview {
  userId: string
  /** Search results only: the message that matched, so the chat can open right at it. */
  matchedMessageId?: string | null
}

/** A message including the admin-only fields. */
export interface AdminMessage extends Message {
  /** User messages: classifier score 0..1; null while not classified. */
  promptRisk: number | null
  /** User messages: classifier explanation; null while not classified. */
  promptRiskReason: string | null
  /** Assistant messages: model response time; null for rows saved before timing existed. */
  responseTimeMs: number | null
  /** Assistant messages: app-wide "Response №"; null for user messages. */
  responseNumber: number | null
}

/** One AI reply in the Responses panel (from any conversation). */
export interface AdminResponse {
  responseNumber: number
  conversationId: string
  conversationTitle: string
  userId: string
  messageId: string
  prompt: string
  content: string
  createdAt: string
  promptRisk: number | null
  promptRiskReason: string | null
  responseTimeMs: number | null
}

export type ResponseSort = 'newest' | 'reason' | 'risk-asc' | 'risk-desc'

export interface AdminConversationList {
  conversationCount: number
  conversations: AdminConversation[]
}

export interface AdminConversationHistory {
  conversation: AdminConversation
  messages: AdminMessage[]
  /** Prompts with promptRisk >= this are high risk. */
  riskThreshold: number
}

export interface AdminResponseList {
  responses: AdminResponse[]
  riskThreshold: number
}
