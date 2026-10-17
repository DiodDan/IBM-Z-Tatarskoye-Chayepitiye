package com.example.conversation.config

import com.example.conversation.error.ApiException
import com.example.conversation.identity.CurrentUserArgumentResolver
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.net.URI

@Configuration
class WebConfig(
    private val currentUserArgumentResolver: CurrentUserArgumentResolver,
    @Value("\${app.web.check-origin:true}") private val checkOrigin: Boolean,
) : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(currentUserArgumentResolver)
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(ApiInterceptor(checkOrigin)).addPathPatterns("/api/**")
    }

    /**
     * - Cache-Control: no-store on every API response.
     * - Same-origin check: a mutation with an Origin header must come from the same host:port.
     *   Behind nginx, forward the original Host with `proxy_set_header Host $http_host;`.
     */
    private class ApiInterceptor(private val checkOrigin: Boolean) : HandlerInterceptor {
        private val mutatingMethods = setOf("POST", "PUT", "PATCH", "DELETE")

        override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
            response.setHeader("Cache-Control", "no-store")

            if (checkOrigin && request.method in mutatingMethods) {
                val origin = request.getHeader("Origin") ?: return true // same-origin fetches may omit it
                val originAuthority = runCatching { URI(origin).authority }.getOrNull()
                val host = request.getHeader("Host")
                if (originAuthority == null || host == null || !originAuthority.equals(host, ignoreCase = true)) {
                    throw ApiException.forbiddenOrigin()
                }
            }
            return true
        }
    }
}
