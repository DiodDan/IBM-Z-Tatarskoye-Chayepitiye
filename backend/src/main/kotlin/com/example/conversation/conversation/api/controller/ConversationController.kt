package com.example.conversation.conversation.api.controller

import com.example.conversation.conversation.api.mapper.ConversationApiMapper

import com.example.conversation.conversation.api.dto.ConversationHistoryResponse
import com.example.conversation.conversation.api.dto.ConversationListResponse
import com.example.conversation.conversation.api.dto.ConversationPreviewResponse
import com.example.conversation.conversation.api.dto.MessageExchangeResponse
import com.example.conversation.conversation.api.dto.RenameConversationRequest
import com.example.conversation.conversation.api.dto.SendMessageRequest
import com.example.conversation.conversation.application.command.ConversationCommandService
import com.example.conversation.conversation.application.query.ConversationQueryService
import com.example.conversation.identity.CurrentUser
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** The 6 endpoints. `user` comes from the user_id cookie (CurrentUserArgumentResolver). */
@RestController
@RequestMapping("/api/conversations")
class ConversationController(
    private val queries: ConversationQueryService,
    private val commands: ConversationCommandService,
    private val mapper: ConversationApiMapper,
) {

    @GetMapping
    fun list(user: CurrentUser): ConversationListResponse =
        mapper.toList(queries.list(user.externalId))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(user: CurrentUser, @RequestBody body: SendMessageRequest): MessageExchangeResponse =
        mapper.toExchange(commands.create(user.externalId, body.content))

    @GetMapping("/{conversationId}")
    fun get(user: CurrentUser, @PathVariable conversationId: UUID): ConversationHistoryResponse =
        mapper.toHistory(queries.conversationSnapshot(user.externalId, conversationId))

    @PostMapping("/{conversationId}/messages")
    fun send(
        user: CurrentUser,
        @PathVariable conversationId: UUID,
        @RequestBody body: SendMessageRequest,
    ): MessageExchangeResponse =
        mapper.toExchange(commands.send(user.externalId, conversationId, body.content))

    @PatchMapping("/{conversationId}")
    fun rename(
        user: CurrentUser,
        @PathVariable conversationId: UUID,
        @RequestBody body: RenameConversationRequest,
    ): ConversationPreviewResponse =
        mapper.toPreview(commands.rename(user.externalId, conversationId, body.title))

    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(user: CurrentUser, @PathVariable conversationId: UUID) {
        commands.delete(user.externalId, conversationId)
    }
}
