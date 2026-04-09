package com.miyou.app.domain.dialogue.port

interface TemplateLoaderPort {
    fun load(templateName: String): String
}
