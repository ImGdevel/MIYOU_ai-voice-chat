package com.miyou.app.infrastructure.common.template

import com.miyou.app.domain.dialogue.port.TemplateLoaderPort
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.util.StreamUtils
import java.io.IOException
import java.nio.charset.StandardCharsets

@Component
class FileBasedPromptTemplate : TemplateLoaderPort {
    private val templateExtensions: List<String> = listOf(".md", ".txt")

    override fun load(templateName: String): String = load(templateName, emptyMap())

    fun load(
        templateName: String,
        variables: Map<String, String>,
    ): String {
        try {
            val resource = resolveResource(templateName)
            val template = StreamUtils.copyToString(resource.inputStream, StandardCharsets.UTF_8)

            var result = template
            for ((key, value) in variables) {
                result = result.replace("{{$key}}", value)
            }
            return result
        } catch (e: IOException) {
            throw RuntimeException("Failed to load template: $templateName", e)
        }
    }

    private fun resolveResource(templateName: String): ClassPathResource {
        for (ext in templateExtensions) {
            val resource = ClassPathResource("templates/$templateName$ext")
            if (resource.exists()) {
                return resource
            }
        }
        throw IOException("Template not found for name: $templateName")
    }
}
