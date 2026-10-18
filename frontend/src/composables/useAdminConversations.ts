import { computed, ref } from 'vue'
import { adminService } from '../services/admin.service'
import { ApiError } from '../services/http'
import type { AdminConversation, AdminMessage, AdminResponse, ResponseSort } from '../types/admin'

function errorText(err: unknown): string {
  if (err instanceof ApiError) return err.message
  if (err instanceof Error) return err.message
  return 'Something went wrong.'
}

/** Looks like a pasted response number ("Response №12", "№12", "#12", "12")? Same rule as the backend. */
const RESPONSE_NUMBER = /^(?:response\s*)?(?:№|no\.?|#)?\s*\d+$/i

/**
 * Read-only state for the admin page: every user's conversations, the app-wide responses feed,
 * search, and which conversation/message is highlighted.
 */
export function useAdminConversations() {
  const conversations = ref<AdminConversation[]>([])
  const activeConversationId = ref<string | null>(null)
  const messages = ref<AdminMessage[]>([])
  const riskThreshold = ref(0.5)

  const responses = ref<AdminResponse[]>([])
  const responseSort = ref<ResponseSort>('newest')

  /** The message highlighted in the middle chat ("See full response", or a search hit). */
  const highlightedMessageId = ref<string | null>(null)
  /** Bumped on every jump, so the sidebar flashes the conversation again even if it is already active. */
  const highlightPulse = ref(0)

  const searchQuery = ref('')
  const isLoadingList = ref(false)
  const isLoadingHistory = ref(false)
  const isLoadingResponses = ref(false)
  const listError = ref<string | null>(null)
  const conversationError = ref<string | null>(null)
  const responsesError = ref<string | null>(null)

  const totalConversations = ref(0)
  const conversationCount = computed(() => (searchQuery.value.trim() ? conversations.value.length : totalConversations.value))
  const activeConversation = computed(
    () => conversations.value.find((c) => c.id === activeConversationId.value) ?? activeSnapshot.value,
  )
  // Keeps the header filled when the open conversation is filtered out of the sidebar by a search.
  const activeSnapshot = ref<AdminConversation | null>(null)

  let historyController: AbortController | null = null
  let listRequest = 0

  async function loadConversations(query = searchQuery.value) {
    const requestId = ++listRequest
    isLoadingList.value = true
    listError.value = null
    try {
      const result = await adminService.listConversations(query)
      if (requestId !== listRequest) return // a newer search already answered
      conversations.value = result.conversations
      if (!query.trim()) {
        totalConversations.value = result.conversationCount
        // Open the newest conversation so the page isn't empty.
        const first = result.conversations[0]
        if (!activeConversationId.value && first) openConversation(first.id)
        return
      }
      // A pasted response number, or text that matches exactly one conversation: open it right at the match.
      const hit = result.conversations[0]
      if (hit && (result.conversations.length === 1 || RESPONSE_NUMBER.test(query.trim()))) {
        openConversation(hit.id, hit.matchedMessageId ?? null)
      }
    } catch (err) {
      if (requestId === listRequest) listError.value = errorText(err)
    } finally {
      if (requestId === listRequest) isLoadingList.value = false
    }
  }

  let searchTimer: number | undefined
  function search(query: string) {
    searchQuery.value = query
    window.clearTimeout(searchTimer)
    searchTimer = window.setTimeout(() => loadConversations(query), 250)
  }

  async function loadResponses(sort = responseSort.value) {
    responseSort.value = sort
    isLoadingResponses.value = true
    responsesError.value = null
    try {
      const result = await adminService.listResponses(sort)
      if (sort !== responseSort.value) return
      responses.value = result.responses
      riskThreshold.value = result.riskThreshold
    } catch (err) {
      responsesError.value = errorText(err)
    } finally {
      isLoadingResponses.value = false
    }
  }

  /** Open a conversation (from the sidebar, a search hit, or a response card) and optionally highlight a message. */
  async function openConversation(id: string, highlightMessageId: string | null = null) {
    highlightedMessageId.value = highlightMessageId
    if (highlightMessageId) highlightPulse.value++
    if (id === activeConversationId.value && messages.value.length) return

    historyController?.abort()
    const controller = new AbortController()
    historyController = controller

    activeConversationId.value = id
    messages.value = []
    conversationError.value = null
    isLoadingHistory.value = true
    try {
      const history = await adminService.getConversation(id, controller.signal)
      if (controller.signal.aborted) return
      messages.value = history.messages
      riskThreshold.value = history.riskThreshold
      activeSnapshot.value = history.conversation
      conversations.value = conversations.value.map((c) =>
        c.id === id ? { ...history.conversation, matchedMessageId: c.matchedMessageId } : c,
      )
    } catch (err) {
      if (controller.signal.aborted) return
      conversationError.value = errorText(err)
    } finally {
      if (historyController === controller) isLoadingHistory.value = false
    }
  }

  /** "See full response": open its conversation, highlight it there and in the sidebar. */
  function showResponse(response: AdminResponse) {
    openConversation(response.conversationId, response.messageId)
  }

  /** Reload everything (new chats from users appear only after this or a page reload). */
  function refresh() {
    loadConversations()
    loadResponses()
    const id = activeConversationId.value
    if (id) {
      activeConversationId.value = null
      openConversation(id, highlightedMessageId.value)
    }
  }

  return {
    conversations,
    conversationCount,
    activeConversationId,
    activeConversation,
    messages,
    riskThreshold,
    responses,
    responseSort,
    highlightedMessageId,
    highlightPulse,
    searchQuery,
    isLoadingList,
    isLoadingHistory,
    isLoadingResponses,
    listError,
    conversationError,
    responsesError,
    loadConversations,
    loadResponses,
    search,
    openConversation,
    showResponse,
    refresh,
  }
}
