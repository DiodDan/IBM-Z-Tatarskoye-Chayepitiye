export type MessageRole = 'user' | 'assistant'

export type MessageStatus = 'allowed' | 'blocked' | 'failed' | 'pending'

export interface ConversationPreview {
  id: string
  title: string
  createdAt: string
  updatedAt?: string
}

export interface ConversationList {
  conversationCount: number
  conversations: ConversationPreview[]
}

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

export interface ApiErrorBody {
  code: string
  message: string
}