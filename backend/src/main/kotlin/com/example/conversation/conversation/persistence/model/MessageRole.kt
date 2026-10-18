package com.example.conversation.conversation.persistence.model

/** Stored as "USER"/"ASSISTANT" in the database, sent as "user"/"assistant" in JSON. */
enum class MessageRole {
    USER,
    ASSISTANT;

    val apiValue: String get() = name.lowercase()
}
