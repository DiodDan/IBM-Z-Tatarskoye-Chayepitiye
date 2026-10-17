// Mirrors the API DTOs. Source of truth: initial-docs/openapi.json + initial-docs/backend-requirements.md.

export interface ConversationPreview {
  id: string
  title: string
  createdAt: string
  /**
   * In openapi.json but NOT in backend-requirements.md (the Kotlin backend omits it).
   * Optional so the app works either way; the team still needs to decide (see README).
   */
  updatedAt?: string
}

export interface ConversationList {
  conversationCount: number
  conversations: ConversationPreview[]
}

export type MessageRole = 'user' | 'assistant'

/**
 * Not sent by the backend in this phase (no safety filter yet).
 * Kept optional so the UI is ready when the safety check is added to the LLM gateway.
 */
export type MessageStatus = 'pending' | 'allowed' | 'blocked' | 'failed'

export interface Message {
  id: string
  role: MessageRole
  content: string
  createdAt: string
  status?: MessageStatus
}

export interface ConversationHistory {
  conversation: ConversationPreview
  messages: Message[]
}

export interface MessageExchange {
  conversation: ConversationPreview
  userMessage: Message
  assistantMessage: Message
}

export interface SendMessageRequest {
  content: string
}

export interface RenameConversationRequest {
  title: string
}

export interface ApiErrorBody {
  code: string
  message: string
}
