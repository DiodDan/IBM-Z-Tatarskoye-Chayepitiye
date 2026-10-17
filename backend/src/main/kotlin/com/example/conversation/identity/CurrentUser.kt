package com.example.conversation.identity

import java.util.UUID

/** The anonymous user of this request: the validated UUID from the user_id cookie. */
data class CurrentUser(val externalId: UUID)
