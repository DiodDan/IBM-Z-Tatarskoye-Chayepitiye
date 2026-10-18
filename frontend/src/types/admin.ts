import type { ConversationPreview, Message } from './api'

/** A conversation as the admin sees it: any user's, labelled with the owner's anonymous id. */
export interface AdminConversation extends ConversationPreview {
  userId: string
}

/** A message including the admin-only fields. */
export interface AdminMessage extends Message {
  /** User messages: classifier score 0..1; null while not classified. */
  promptRisk: number | null
  /** User messages: classifier explanation; null while not classified. */
  promptRiskReason: string | null
  /** Assistant messages: model response time; null for rows saved before timing existed. */
  responseTimeMs: number | null
}

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
