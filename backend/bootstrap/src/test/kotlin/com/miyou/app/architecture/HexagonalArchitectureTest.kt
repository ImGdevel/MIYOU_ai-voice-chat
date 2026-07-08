package com.miyou.app.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.DisplayName
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
    @DisplayName("도메인 레이어는 Spring이나 Mongo, Micrometer 등의 외부 프레임워크에 의존해서는 안 된다")
    fun domainShouldNotDependOnSpringOrMongoOrMicrometer() {
        // 도메인 레이어는 프레임워크와 완전히 독립되어 순수 비즈니스 로직만 담고 있는지 검증.
        // ErrorCode는 HttpStatus를 직접 들고 있지 않고 ErrorCategory(순수 enum)만 노출하므로
        // exception 패키지도 예외 없이 이 규칙을 지킨다.
        noClasses()
            .that()
            .resideInAPackage("com.miyou.app.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "io.micrometer..")
            .because("도메인 레이어는 특정 프레임워크에 독립적이어야 합니다")
            .check(importedClasses)
    }

    @Test
    @DisplayName("애플리케이션 레이어는 인프라 레이어에 의존해서는 안 된다")
    fun applicationShouldNotDependOnInfrastructure() {
        // 애플리케이션 레이어는 인프라의 기술적 세부 구현(예: DB, 외부 API)에 의존하지 않고 오직 도메인과 포트에만 의존해야 함
        noClasses()
            .that()
            .resideInAPackage("com.miyou.app.application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.miyou.app.infrastructure..")
            .because("애플리케이션 레이어는 인프라 구현체에 의존해서는 안 됩니다")
            .check(importedClasses)
    }

    @Test
    @DisplayName("인프라 레이어는 애플리케이션 서비스 구현체에 의존해서는 안 된다")
    fun infrastructureShouldNotDependOnApplicationServices() {
        // 인프라 어댑터는 애플리케이션 유스케이스(인터페이스)를 호출하거나 영속성 포트를 구현해야 하며, 서비스 구현체에 직접 의존해선 안 됨
        noClasses()
            .that()
            .resideInAPackage("com.miyou.app.infrastructure..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.miyou.app.application..service..",
                "com.miyou.app.application..pipeline..",
            ).because("인프라 어댑터는 애플리케이션 서비스 구현체에 의존해서는 안 됩니다")
            .check(importedClasses)
    }

    @Test
    @DisplayName("RestController는 api 모듈에만 존재해야 한다")
    fun restControllersShouldOnlyResideInApi() {
        // HTTP API 엔드포인트를 노출하는 컨트롤러는 오직 진입점인 api 패키지 아래에만 위치하도록 제안
        classes()
            .that()
            .areAnnotatedWith(RestController::class.java)
            .should()
            .resideInAPackage("com.miyou.app.api..")
            .because("컨트롤러는 api 모듈 내에 위치해야 합니다")
            .check(importedClasses)
    }

    @Test
    @DisplayName("@Document 엔티티는 인프라 혹은 모니터링 모듈에만 위치해야 한다")
    fun documentAnnotationShouldOnlyBeInInfrastructureOrMonitoring() {
        // 데이터베이스 영속성용 엔티티 어노테이션(@Document)은 도메인이나 애플리케이션 계층에 침투해선 안 됨
        classes()
            .that()
            .areAnnotatedWith(Document::class.java)
            .should()
            .resideInAnyPackage("com.miyou.app.infrastructure..", "com.miyou.app.monitoring..")
            .because("@Document 어노테이션이 붙은 엔티티는 인프라 또는 독립된 모니터링 모듈에만 존재해야 합니다")
            .check(importedClasses)
    }

    @Test
    @DisplayName("모니터링 모듈은 다른 비즈니스 레이어에 의존해서는 안 된다")
    fun monitoringShouldNotDependOnBusinessLayers() {
        // 모니터링 모듈은 비즈니스 레이어와 격리된 횡단 관심사 공통 모듈이어야 함
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
            ).because("모니터링은 독립적이고 횡단적인 모듈이므로 다른 비즈니스 레이어의 내부 정보를 알아서는 안 됩니다")
            .check(importedClasses)
    }

    @Test
    @DisplayName("dialogue 도메인은 다른 도메인과 격리되어야 한다")
    fun domainDialogueShouldNotDependOnOtherDomains() = domainIsolationRule("dialogue")

    @Test
    @DisplayName("credit 도메인은 다른 도메인과 격리되어야 한다")
    fun domainCreditShouldNotDependOnOtherDomains() = domainIsolationRule("credit")

    @Test
    @DisplayName("memory 도메인은 다른 도메인과 격리되어야 한다")
    fun domainMemoryShouldNotDependOnOtherDomains() = domainIsolationRule("memory")

    @Test
    @DisplayName("mission 도메인은 다른 도메인과 격리되어야 한다")
    fun domainMissionShouldNotDependOnOtherDomains() = domainIsolationRule("mission")

    @Test
    @DisplayName("auth 도메인은 다른 도메인과 격리되어야 한다")
    fun domainAuthShouldNotDependOnOtherDomains() = domainIsolationRule("auth")

    @Test
    @DisplayName("cost 도메인은 다른 도메인과 격리되어야 한다")
    fun domainCostShouldNotDependOnOtherDomains() = domainIsolationRule("cost")

    @Test
    @DisplayName("retrieval 도메인은 다른 도메인과 격리되어야 한다")
    fun domainRetrievalShouldNotDependOnOtherDomains() = domainIsolationRule("retrieval")

    @Test
    @DisplayName("voice 도메인은 다른 도메인과 격리되어야 한다")
    fun domainVoiceShouldNotDependOnOtherDomains() = domainIsolationRule("voice")

    private fun domainIsolationRule(from: String) {
        val others = BUSINESS_DOMAINS.filter { it != from }
        noClasses()
            .that()
            .resideInAPackage("com.miyou.app.domain.$from..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(*others.map { "com.miyou.app.domain.$it.." }.toTypedArray())
            .because("비즈니스 도메인 간에는 서로 직접 의존해서는 안 됩니다")
            .check(importedClasses)
    }
}
