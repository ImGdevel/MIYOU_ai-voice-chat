package com.miyou.app.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.web.bind.annotation.RestController

class HexagonalArchitectureTest {
    companion object {
        private val importedClasses =
            ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.miyou.app")
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
    fun infrastructureOutboundShouldNotDependOnApplicationServices() {
        noClasses()
            .that()
            .resideInAPackage("com.miyou.app.infrastructure..")
            .and()
            .resideOutsideOfPackage("com.miyou.app.infrastructure.inbound..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.miyou.app.application..service..",
                "com.miyou.app.application..pipeline..",
            ).because("Infrastructure outbound adapters must not depend on application service implementations")
            .check(importedClasses)
    }

    @Test
    fun restControllersShouldOnlyResideInInfrastructureInboundWeb() {
        classes()
            .that()
            .areAnnotatedWith(RestController::class.java)
            .should()
            .resideInAPackage("com.miyou.app.infrastructure.inbound.web..")
            .because("Controllers must reside in infrastructure.inbound.web")
            .check(importedClasses)
    }

    @Test
    fun documentAnnotationShouldOnlyBeInInfrastructure() {
        classes()
            .that()
            .areAnnotatedWith(Document::class.java)
            .should()
            .resideInAPackage("com.miyou.app.infrastructure..")
            .because("@Document entities must reside in infrastructure layer")
            .check(importedClasses)
    }
}
