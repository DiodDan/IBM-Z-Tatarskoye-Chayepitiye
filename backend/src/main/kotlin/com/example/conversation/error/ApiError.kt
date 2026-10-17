package com.example.conversation.error

/** JSON body for every error response: {"code": "...", "message": "..."} */
data class ApiError(val code: String, val message: String)
