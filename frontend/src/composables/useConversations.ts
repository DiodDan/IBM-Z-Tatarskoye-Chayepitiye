import { computed, ref } from 'vue'
import { conversationService } from '../services/conversation.service'
import { ApiError } from '../services/http'
import type { ConversationPreview, Message } from '../types/api'

function errorText(err: unknown): string {
  if (err instanceof ApiError) return err.message
  if (err instanceof Error) return err.message
  return 'Something went wrong.'
}

/** Newest first. Uses updatedAt if the backend sends it, otherwise createdAt (Kotlin backend). */
function sortPreviews(list: ConversationPreview[]): ConversationPreview[] {
  const key = (c: ConversationPreview) => c.updatedAt ?? c.createdAt
  return [...list].sort((a, b) => key(b).localeCompare(key(a)) || a.id.localeCompare(b.id))
}

/** All conversation state lives here. Create it ONCE in the view and pass it down. */
export function useConversations() {
  const conversations = ref<ConversationPreview[]>([])
  const activeConversationId = ref<string | null>(null)
  const messages = ref<Message[]>([])

  const isLoadingList = ref(false)
  const isLoadingHistory = ref(false)
  const isSending = ref(false)
  const isMutating = ref(false)

  const listError = ref<string | null>(null)
  const conversationError = ref<string | null>(null)
  const sendError = ref<string | null>(null)

  const conversationCount = computed(() => conversations.value.length)
  const activeConversation = computed(
    () => conversations.value.find((c) => c.id === activeConversationId.value) ?? null,
  )

  let historyController: AbortController | null = null

  function upsertPreview(preview: ConversationPreview) {
    const others = conversations.value.filter((c) => c.id !== preview.id)
    conversations.value = sortPreviews([preview, ...others])
  }

  async function loadConversations() {
    isLoadingList.value = true
    listError.value = null
    try {
      const result = await conversationService.listConversations()
      conversations.value = sortPreviews(result.conversations)
    } catch (err) {
      listError.value = errorText(err)
    } finally {
      isLoadingList.value = false
    }
  }

  function startNewConversation() {
    historyController?.abort()
    activeConversationId.value = null
    messages.value = []
    conversationError.value = null
    sendError.value = null
  }

  async function selectConversation(id: string) {
    if (id === activeConversationId.value) return
    historyController?.abort()
    const controller = new AbortController()
    historyController = controller

    activeConversationId.value = id
    messages.value = []
    conversationError.value = null
    sendError.value = null
    isLoadingHistory.value = true
    try {
      const history = await conversationService.getConversation(id, controller.signal)
      if (controller.signal.aborted) return // user already clicked something else
      messages.value = history.messages
      upsertPreview(history.conversation)
    } catch (err) {
      if (controller.signal.aborted) return
      conversationError.value = errorText(err)
    } finally {
      if (historyController === controller) isLoadingHistory.value = false
    }
  }

  /** Returns true on success so the composer knows whether to clear its text. */
  async function sendMessage(rawContent: string): Promise<boolean> {
    const content = rawContent.trim()
    if (!content || isSending.value) return false

    sendError.value = null
    isSending.value = true

    // Temporary bubble so the user sees their message straight away.
    const tempMessage: Message = {
      id: 'temp-' + Date.now(),
      role: 'user',
      content,
      createdAt: new Date().toISOString(),
      status: 'pending',
    }
    messages.value = [...messages.value, tempMessage]

    try {
      const exchange = activeConversationId.value
        ? await conversationService.sendMessage(activeConversationId.value, content)
        : await conversationService.createConversation(content)

      messages.value = [
        ...messages.value.filter((m) => m.id !== tempMessage.id),
        exchange.userMessage,
        exchange.assistantMessage,
      ]
      activeConversationId.value = exchange.conversation.id
      upsertPreview(exchange.conversation)
      return true
    } catch (err) {
      messages.value = messages.value.filter((m) => m.id !== tempMessage.id)
      sendError.value = errorText(err)
      return false
    } finally {
      isSending.value = false
    }
  }

  async function renameConversation(id: string, rawTitle: string) {
    const title = rawTitle.trim()
    if (!title) return
    isMutating.value = true
    listError.value = null
    try {
      upsertPreview(await conversationService.renameConversation(id, title))
    } catch (err) {
      listError.value = errorText(err)
    } finally {
      isMutating.value = false
    }
  }

  async function deleteConversation(id: string) {
    isMutating.value = true
    listError.value = null
    try {
      await conversationService.deleteConversation(id)
      conversations.value = conversations.value.filter((c) => c.id !== id)
      if (activeConversationId.value === id) startNewConversation()
    } catch (err) {
      listError.value = errorText(err)
    } finally {
      isMutating.value = false
    }
  }

  return {
    conversations,
    conversationCount,
    activeConversationId,
    activeConversation,
    messages,
    isLoadingList,
    isLoadingHistory,
    isSending,
    isMutating,
    listError,
    conversationError,
    sendError,
    loadConversations,
    startNewConversation,
    selectConversation,
    sendMessage,
    renameConversation,
    deleteConversation,
  }
}
