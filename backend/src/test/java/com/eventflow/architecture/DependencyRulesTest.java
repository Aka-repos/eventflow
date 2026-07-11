package com.eventflow.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Reglas de docs/engineering/10-dependency-rules.md — parte obligatoria de la suite (CI). */
@AnalyzeClasses(packages = "com.eventflow")
class DependencyRulesTest {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_spring_web =
            noClasses().that().resideInAPackage("..modules..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.web..", "org.springframework.http..",
                            "org.springframework.stereotype..");

    @ArchTest
    static final ArchRule api_must_not_depend_on_infrastructure =
            noClasses().that().resideInAPackage("..modules..api..")
                    .should().dependOnClassesThat().resideInAPackage("..modules..infrastructure..");

    @ArchTest
    static final ArchRule infrastructure_must_not_depend_on_api =
            noClasses().that().resideInAPackage("..modules..infrastructure..")
                    .should().dependOnClassesThat().resideInAPackage("..modules..api..");

    @ArchTest
    static final ArchRule shared_must_not_depend_on_modules =
            noClasses().that().resideInAPackage("com.eventflow.shared..")
                    .should().dependOnClassesThat().resideInAPackage("com.eventflow.modules..");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_dtos =
            noClasses().that().resideInAPackage("..modules..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..api.dto..");
}
