import { computed, ref } from 'vue'
import { adminService } from '../services/admin.service'
import { ApiError } from '../services/http'
import type { AdminConversation, AdminMessage } from '../types/admin'

function errorText(err: unknown): string {
  if (err instanceof ApiError) return err.message
  if (err instanceof Error) return err.message
  return 'Something went wrong.'
}

/** Read-only state for the admin page: every user's conversations plus risk data. */
export function useAdminConversations() {
  const conversations = ref<AdminConversation[]>([])
  const activeConversationId = ref<string | null>(null)
  const messages = ref<AdminMessage[]>([])
  const riskThreshold = ref(0.5)

  const isLoadingList = ref(false)
  const isLoadingHistory = ref(false)
  const listError = ref<string | null>(null)
  const conversationError = ref<string | null>(null)

  const conversationCount = computed(() => conversations.value.length)
  const activeConversation = computed(
    () => conversations.value.find((c) => c.id === activeConversationId.value) ?? null,
  )

  let historyController: AbortController | null = null

  async function loadConversations() {
    isLoadingList.value = true
    listError.value = null
    try {
      const result = await adminService.listConversations()
      conversations.value = result.conversations
      // Open the newest conversation so the page isn't empty.
      const first = result.conversations[0]
      if (!activeConversationId.value && first) selectConversation(first.id)
    } catch (err) {
      listError.value = errorText(err)
    } finally {
      isLoadingList.value = false
    }
  }

  async function selectConversation(id: string) {
    if (id === activeConversationId.value) return
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
      conversations.value = conversations.value.map((c) => (c.id === id ? history.conversation : c))
    } catch (err) {
      if (controller.signal.aborted) return
      conversationError.value = errorText(err)
    } finally {
      if (historyController === controller) isLoadingHistory.value = false
    }
  }

  return {
    conversations,
    conversationCount,
    activeConversationId,
    activeConversation,
    messages,
    riskThreshold,
    isLoadingList,
    isLoadingHistory,
    listError,
    conversationError,
    loadConversations,
    selectConversation,
  }
}
