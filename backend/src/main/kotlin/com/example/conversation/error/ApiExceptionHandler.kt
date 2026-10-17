package com.example.conversation.error

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/** Turns every failure into an ApiError. Never echoes message text or prompts into logs. */
@RestControllerAdvice
class ApiExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApi(e: ApiException): ResponseEntity<ApiError> {
        if (e.status.is5xxServerError) {
            log.warn("Request failed: code={} cause={}", e.code, e.cause?.javaClass?.name)
        }
        return ResponseEntity.status(e.status).body(ApiError(e.code, e.message ?: e.code))
    }

    /** Malformed JSON, missing fields, malformed path UUIDs -> 400 (backend-requirements.md §4). */
    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        MethodArgumentNotValidException::class,
        HandlerMethodValidationException::class,
    )
    fun handleBadRequest(e: Exception): ResponseEntity<ApiError> =
        handleApi(ApiException.invalidRequest())

    @ExceptionHandler(Exception::class)
    fun handleOther(e: Exception): ResponseEntity<ApiError> {
        // Spring's own errors (unknown URL, wrong method, wrong content type) keep their real status.
        if (e is ErrorResponse) {
            val status = e.statusCode
            return ResponseEntity.status(status).body(ApiError("HTTP_${status.value()}", e.body.detail ?: "Request failed."))
        }
        log.error("Unexpected error", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError("INTERNAL_ERROR", "Unexpected server error."))
    }
}
