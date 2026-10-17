# Conversation SPA — v1 architecture

## Scope and decisions

Vue 3 and TypeScript SPA with one screen: conversation sidebar, New conversation button, light/dark toggle, message history, and composer. Anonymous UUID identity, full JSON replies, creation on first message, manual rename and delete. No streaming, attachments, model selection, quotas, pagination, message editing, regeneration, or automatic retries in v1.

Proposed implementation defaults: Vite, Vue Single File Components with `<script setup lang="ts">`, native fetch, and one shared conversation composable. No router or state library is needed for one screen. The selected conversation lives in memory; refreshing returns to the empty composer with the sidebar loaded. Deep links can be added later.

## Source structure

```text
src/
  main.ts
  App.vue
  views/
    ConversationView.vue
  components/
    ConversationSidebar.vue
    ConversationListItem.vue
    ConversationPanel.vue
    MessageList.vue
    MessageBubble.vue
    MessageComposer.vue
    ThemeToggle.vue
  services/
    http.ts
    identity.service.ts
    conversation.service.ts
    theme.service.ts
  composables/
    useConversations.ts
    useTheme.ts
  types/
    api.ts
  styles/
    base.css
    themes.css
```

`App.vue` renders the view. The view connects application state to components and composes the responsive layout. Components receive typed props and emit typed events; they do not call API services. Composer text and inline rename input can be component-local state. On narrow screens the sidebar collapses into a drawer within the same view.

`useConversations` owns previews, active conversation, messages, loading states, mutation state, and errors. Instantiate it once in the view and pass state/actions down. It calls the services and coordinates updates. `useTheme` owns the current theme and applies it to the document.

Services own side effects: HTTP, identity cookie, and theme storage. They have no component dependencies. `types/api.ts` mirrors the OpenAPI DTOs; the OpenAPI file is the API contract. Generate these types during implementation if desired, rather than maintaining incompatible duplicate contracts.

## API boundary

Base URL: `/api`, served from the same origin as the SPA.

| Service method | Request | Result |
| --- | --- | --- |
| listConversations() | GET /conversations | conversationCount, conversations |
| getConversation(id) | GET /conversations/{id} | conversation, messages |
| createConversation(content) | POST /conversations | conversation, userMessage, assistantMessage |
| sendMessage(id, content) | POST /conversations/{id}/messages | conversation, userMessage, assistantMessage |
| renameConversation(id, title) | PATCH /conversations/{id} | updated preview |
| deleteConversation(id) | DELETE /conversations/{id} | 204, no body |

Both message endpoints receive `{ "content": "..." }`. Rename receives `{ "title": "..." }`. The browser sends only the identity cookie for the list request. Existing-conversation requests also specify the conversation ID. The client never sends conversation history or AI credentials; the backend loads context and calls the AI provider.

A preview contains `id`, `title`, `createdAt`, and `updatedAt`. No message history is downloaded for the sidebar. Count is the number of conversations returned. List ordering is updatedAt descending, then id ascending. History contains all persisted user and assistant messages in their original sequence.

## Identity

Before the first API request, `identity.service.ts` reads `user_id`. If absent or invalid, create a UUID v4 using `crypto.randomUUID()` and store it as a host-only cookie with `Path=/; SameSite=Lax; Max-Age=31536000; Secure` in production HTTPS. Local HTTP development omits Secure. Because JavaScript creates this cookie, it cannot be HttpOnly. If cookies cannot be stored, show an initialization error rather than issuing requests with changing identities.

Use fetch with `credentials: 'same-origin'`; do not manually construct a Cookie header. An unknown valid UUID gets an empty list and count zero. The backend creates ownership records lazily when the first conversation is saved. Clearing or expiring the cookie loses access to prior conversations. This anonymous UUID is the access credential, not a public user identifier.

Every backend query and mutation must check ownership using the cookie. Return 404 for missing and other-user conversations. Use HTTPS, no-store response caching, JSON mutation bodies, and same-origin validation on mutations. Do not enable credentialed cross-origin access for v1.

## State and user flows

State includes `conversations`, `activeConversationId: string | null`, `messages`, `isLoadingList`, `isLoadingHistory`, `isSending`, `isMutating`, and contextual error messages. Derive the displayed count from the complete conversation list after mutations; the initial API count must match the list length.

1. Startup: initialize identity, apply stored theme, fetch count and previews. A new user sees an empty list and composer.
2. New conversation: clear selection, message history, and draft locally. Make no API request and do not add an empty sidebar item.
3. Select conversation: load its full history and show a loading state. Cancel or ignore obsolete history requests when selection changes, so a slow response cannot replace the current selection.
4. First send: trim and validate text, call POST /conversations, and show the draft as a temporary pending user bubble plus an AI loading indicator. On success, use the server IDs and messages, select the new conversation, insert its preview, and clear the draft.
5. Subsequent send: call the conversation message endpoint. On success append both returned messages and update/reorder the preview. The title stays unchanged.
6. Rename: edit the title inline, submit PATCH, then replace and reorder the preview. Update selected conversation metadata if necessary.
7. Delete: submit DELETE and remove the preview only after success. If selected, return to the empty composer.

For a simple v1, disable sending, conversation switching, New conversation, rename, and delete while an AI reply is pending. Keep the theme toggle usable. Disable relevant controls during other mutations. Read-only history loads can be cancelled when selection changes.

Backend creation and message sending persist the user/assistant pair only after successful AI generation. The initial title is the first 80 Unicode code points of the first message after collapsing whitespace and trimming. This is a deterministic default, not an extra AI request. IDs and timestamps are server-assigned. Rename and successful sends update updatedAt; title generation happens only once.

The backend permits one generation at a time per conversation and rejects conflicting sends, renames, or deletes with 409. Keep this lock through persistence so AI context and stored sequence agree.

## Loading, errors, and rendering

`http.ts` sets Accept and JSON Content-Type where appropriate, decodes successful JSON, handles 204 without parsing a body, and converts non-2xx errors into a typed service error. Display errors near the affected list, conversation, or composer. On a failed send, remove the temporary bubble and retain the input text. Do not implement automatic retries or a dedicated retry workflow now.

A lost connection after the server commits can leave the result uncertain. Exactly-once delivery and recovery after a lost response are explicitly deferred; reloading history/list shows persisted results. Configure backend and proxy request timeouts to accommodate complete AI generation.

Render messages as plain text with preserved line breaks and Vue text interpolation. Markdown rendering is outside v1. Scroll to the latest message when history loads and after a successful send. Use accessible button names, keyboard submission, Shift+Enter for a newline, and avoid sending while an input method composition is active.

## Theme

Persist `light` or `dark` in localStorage under `theme`. With no saved preference, use the operating system preference initially. Apply `data-theme` to the document root and implement colors with CSS variables. Theme requires no API endpoint. Storage failures fall back to an in-memory preference.

## Backend responsibilities

Minimal storage: conversations (`id`, `user_id`, `title`, `created_at`, `updated_at`) and messages (`id`, `conversation_id`, `sequence`, `role`, `content`, `created_at`). Enforce a unique sequence within each conversation. Delete messages with their conversation. No separate user profile or stats table is required; count owned conversations. AI provider secrets and system instructions stay on the server.

## Acceptance checklist

- First visit creates one identity cookie and loads an empty list for a new UUID.
- Refresh preserves browser identity and saved conversations.
- New conversation alone makes no network mutation.
- First send returns one saved conversation and two messages.
- Selecting a conversation loads only its own full history.
- Sending a follow-up uses backend history and updates the sidebar ordering.
- Rename and delete update the list and count correctly.
- Changing the cookie cannot expose another user's conversations by ID.
- Theme survives refresh; switching conversations cannot display an obsolete history response.

API representation reference: [Swagger cookie authentication](https://swagger.io/docs/specification/v3_0/authentication/cookie-authentication/). The accompanying openapi.json uses OpenAPI 3.0.3 and represents the cookie as an apiKey security scheme in cookie.
