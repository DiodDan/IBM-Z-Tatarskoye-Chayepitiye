package com.example.conversation.identity

import com.example.conversation.error.ApiException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.UUID

/**
 * Lets controllers take a `CurrentUser` parameter. Reads the user_id cookie and rejects
 * anything that is not a UUID v4 with 401 before any business logic runs.
 */
@Component
class CurrentUserArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == CurrentUser::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): CurrentUser {
        val request = webRequest.getNativeRequest(HttpServletRequest::class.java)
        val value = request?.cookies?.firstOrNull { it.name == COOKIE_NAME }?.value
        if (value == null || !UUID_V4.matches(value)) throw ApiException.invalidUserId()
        return CurrentUser(UUID.fromString(value))
    }

    companion object {
        const val COOKIE_NAME = "user_id"
        private val UUID_V4 =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
    }
}
