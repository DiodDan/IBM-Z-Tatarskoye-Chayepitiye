package com.example.conversation.spa

import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.TimeUnit

@RestController
class SpaForwardController {

    private val staticRoot = "META-INF/resources/"

    @GetMapping("/{*path}")
    fun serve(request: HttpServletRequest): ResponseEntity<Resource> {
        val path = request.requestURI.removePrefix("/")

        if (isBackendPath(path)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }

        if (path.isNotEmpty() && path.substringAfterLast('/').contains('.')) {
            val asset = ClassPathResource(staticRoot + path)
            if (asset.exists() && asset.isReadable) {
                val mediaType = MediaTypeFactory.getMediaType(asset)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM)
                return ResponseEntity.ok()
                    .contentType(mediaType)
                    .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                    .body(asset)
            }
        }

        val index = ClassPathResource("${staticRoot}index.html")
        if (!index.exists()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "index.html not found")
        }
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .cacheControl(CacheControl.noCache())
            .body(index)
    }

    private fun isBackendPath(path: String): Boolean =
        path == "api" || path.startsWith("api/") ||
                path == "actuator" || path.startsWith("actuator/") ||
                path == "error" || path.startsWith("error/")
}