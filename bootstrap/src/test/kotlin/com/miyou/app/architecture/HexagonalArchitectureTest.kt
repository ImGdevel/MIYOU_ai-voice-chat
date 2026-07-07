package com.miyou.app.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.web.bind.annotation.RestController

class HexagonalArchitectureTest {
    companion object {
        private val importedClasses =
            ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.miyou.app")

        private val BUSINESS_DOMAINS =
            listOf("dialogue", "credit", "memory", "mission", "auth", "cost", "retrieval", "voice")
    }

    @Test
    fun domainShouldNotDependOnSpringOrMongoOrMicrometer() {
        noClasses()
            .that()
            .resideInAPackage("com.miyou.app.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "io.micrometer..")
            .because("Domain layer must be framework-agnostic")
            .check(importedClasses)
    }

    @Test
    fun applicationShouldNotDependOnInfrastructure() {
        noClasses()
            .that()
            .resideInAPackage("com.miyou.app.application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.miyou.app.infrastructure..")
            .because("Application layer must not depend on infrastructure implementations")
            .check(importedClasses)
    }

    @Test
    fun infrastructureShouldNotDependOnApplicationServices() {
        noClasses()
            .that()
            .resideInAPackage("com.miyou.app.infrastructure..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.miyou.app.application..service..",
                "com.miyou.app.application..pipeline..",
            ).because("Infrastructure adapters must not depend on application service implementations")
            .check(importedClasses)
    }

    @Test
    fun restControllersShouldOnlyResideInApi() {
        classes()
            .that()
            .areAnnotatedWith(RestController::class.java)
            .should()
            .resideInAPackage("com.miyou.app.api..")
            .because("Controllers must reside in the api module")
            .check(importedClasses)
    }

    @Test
    fun documentAnnotationShouldOnlyBeInInfrastructureOrMonitoring() {
        classes()
            .that()
            .areAnnotatedWith(Document::class.java)
            .should()
            .resideInAnyPackage("com.miyou.app.infrastructure..", "com.miyou.app.monitoring..")
            .because("@Document entities must reside in infrastructure or the self-contained monitoring module")
            .check(importedClasses)
    }

    @Test
    fun monitoringShouldNotDependOnBusinessLayers() {
        noClasses()
            .that()
            .resideInAPackage("com.miyou.app.monitoring..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.miyou.app.domain..",
                "com.miyou.app.application..",
                "com.miyou.app.infrastructure..",
                "com.miyou.app.api..",
            ).because("monitoring is a self-contained cross-cutting module - it must not know about business layers")
            .check(importedClasses)
    }

    @Test
    @Disabled(
        "dialogue의 파이프라인 포트(TtsPort/PromptTemplatePort/DialoguePipelineUseCase)가 " +
            "voice.AudioFormat/Voice, retrieval.RetrievalContext를 오케스트레이션 목적으로 직접 참조 - " +
            "단순 원시값 치환이 아닌 인터페이스 재설계 필요, 트래킹: " +
            "https://github.com/ImGdevel/MIYOU_ai-voice-chat/issues/86",
    )
    fun domainDialogueShouldNotDependOnOtherDomains() = domainIsolationRule("dialogue")

    @Test
    fun domainCreditShouldNotDependOnOtherDomains() = domainIsolationRule("credit")

    @Test
    fun domainMemoryShouldNotDependOnOtherDomains() = domainIsolationRule("memory")

    @Test
    fun domainMissionShouldNotDependOnOtherDomains() = domainIsolationRule("mission")

    @Test
    fun domainAuthShouldNotDependOnOtherDomains() = domainIsolationRule("auth")

    @Test
    fun domainCostShouldNotDependOnOtherDomains() = domainIsolationRule("cost")

    @Test
    @Disabled(
        "retrieval.RetrievalPort.retrieveMemories가 memory.MemoryRetrievalResult를 반환 - " +
            "단순 원시값 치환이 아닌 인터페이스 재설계 필요, 트래킹: " +
            "https://github.com/ImGdevel/MIYOU_ai-voice-chat/issues/86",
    )
    fun domainRetrievalShouldNotDependOnOtherDomains() = domainIsolationRule("retrieval")

    @Test
    fun domainVoiceShouldNotDependOnOtherDomains() = domainIsolationRule("voice")

    private fun domainIsolationRule(from: String) {
        val others = BUSINESS_DOMAINS.filter { it != from }
        noClasses()
            .that()
            .resideInAPackage("com.miyou.app.domain.$from..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(*others.map { "com.miyou.app.domain.$it.." }.toTypedArray())
            .because("Business domains must not depend on each other")
            .check(importedClasses)
    }
}
