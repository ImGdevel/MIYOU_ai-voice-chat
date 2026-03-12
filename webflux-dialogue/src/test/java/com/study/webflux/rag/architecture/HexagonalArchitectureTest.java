package com.study.webflux.rag.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class HexagonalArchitectureTest {

	private static final JavaClasses importedClasses = new ClassFileImporter()
		.importPackages("com.study.webflux.rag");

	@Test
	void domainShouldNotDependOnSpringOrMongoOrMicrometer() {
		ArchRule rule = noClasses()
			.that().resideInAPackage("com.study.webflux.rag.domain..")
			.should().dependOnClassesThat()
			.resideInAnyPackage(
				"org.springframework.stereotype..",
				"org.springframework.data.mongodb..",
				"org.springframework.data.annotation..",
				"io.micrometer..")
			.because("Domain layer must be framework-agnostic");

		rule.check(importedClasses);
	}

	@Test
	void applicationShouldNotDependOnInfrastructure() {
		ArchRule rule = noClasses()
			.that().resideInAPackage("com.study.webflux.rag.application..")
			.should().dependOnClassesThat()
			.resideInAPackage("com.study.webflux.rag.infrastructure..")
			.because("Application layer must not depend on infrastructure implementations");

		rule.check(importedClasses);
	}

	@Test
	void infrastructureOutboundShouldNotDependOnApplicationServices() {
		ArchRule rule = noClasses()
			.that().resideInAPackage("com.study.webflux.rag.infrastructure..")
			.and().resideOutsideOfPackage("com.study.webflux.rag.infrastructure.inbound..")
			.should().dependOnClassesThat()
			.resideInAnyPackage(
				"com.study.webflux.rag.application..service..",
				"com.study.webflux.rag.application..pipeline..")
			.because(
				"Infrastructure outbound adapters must not depend on application service implementations");

		rule.check(importedClasses);
	}

	@Test
	void restControllersShouldOnlyResideInInfrastructureInboundWeb() {
		ArchRule rule = classes()
			.that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
			.should().resideInAPackage("com.study.webflux.rag.infrastructure.inbound.web..")
			.because("Controllers must reside in infrastructure.inbound.web");

		rule.check(importedClasses);
	}

	@Test
	void documentAnnotationShouldOnlyBeInInfrastructure() {
		ArchRule rule = classes()
			.that().areAnnotatedWith(org.springframework.data.mongodb.core.mapping.Document.class)
			.should().resideInAPackage("com.study.webflux.rag.infrastructure..")
			.because("@Document entities must reside in infrastructure layer");

		rule.check(importedClasses);
	}
}
