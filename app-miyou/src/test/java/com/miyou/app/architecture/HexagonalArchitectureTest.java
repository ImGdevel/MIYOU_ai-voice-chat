package com.miyou.app.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class HexagonalArchitectureTest {

	private static final JavaClasses importedClasses = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages("com.miyou.app");

	@Test
	void domainShouldNotDependOnSpringOrMongoOrMicrometer() {
		ArchRule rule = noClasses()
			.that().resideInAPackage("com.miyou.app.domain..")
			.should().dependOnClassesThat()
			.resideInAnyPackage(
				"org.springframework..",
				"io.micrometer..")
			.because("Domain layer must be framework-agnostic");

		rule.check(importedClasses);
	}

	@Test
	void applicationShouldNotDependOnInfrastructure() {
		ArchRule rule = noClasses()
			.that().resideInAPackage("com.miyou.app.application..")
			.should().dependOnClassesThat()
			.resideInAPackage("com.miyou.app.infrastructure..")
			.because("Application layer must not depend on infrastructure implementations");

		rule.check(importedClasses);
	}

	@Test
	void infrastructureOutboundShouldNotDependOnApplicationServices() {
		ArchRule rule = noClasses()
			.that().resideInAPackage("com.miyou.app.infrastructure..")
			.and().resideOutsideOfPackage("com.miyou.app.infrastructure.inbound..")
			.should().dependOnClassesThat()
			.resideInAnyPackage(
				"com.miyou.app.application..service..",
				"com.miyou.app.application..pipeline..")
			.because(
				"Infrastructure outbound adapters must not depend on application service implementations");

		rule.check(importedClasses);
	}

	@Test
	void restControllersShouldOnlyResideInInfrastructureInboundWeb() {
		ArchRule rule = classes()
			.that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
			.should().resideInAPackage("com.miyou.app.infrastructure.inbound.web..")
			.because("Controllers must reside in infrastructure.inbound.web");

		rule.check(importedClasses);
	}

	@Test
	void documentAnnotationShouldOnlyBeInInfrastructure() {
		ArchRule rule = classes()
			.that().areAnnotatedWith(org.springframework.data.mongodb.core.mapping.Document.class)
			.should().resideInAPackage("com.miyou.app.infrastructure..")
			.because("@Document entities must reside in infrastructure layer");

		rule.check(importedClasses);
	}
}
