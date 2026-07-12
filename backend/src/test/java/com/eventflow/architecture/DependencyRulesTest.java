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

    // ===== Matriz módulo→módulo (doc 10 §2/§3) =====

    /** identity no llama a nadie (fila identity: todo —). */
    @ArchTest
    static final ArchRule identity_calls_no_module =
            noClasses().that().resideInAPackage("com.eventflow.modules.identity..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.eventflow.modules.catalog..", "com.eventflow.modules.ticketing..",
                            "com.eventflow.modules.ordering..", "com.eventflow.modules.payments..",
                            "com.eventflow.modules.ledger..");

    /** payments y ledger no llaman a ningún módulo (filas payments/ledger: todo —). */
    @ArchTest
    static final ArchRule payments_and_ledger_call_no_module =
            noClasses().that().resideInAnyPackage(
                            "com.eventflow.modules.payments..", "com.eventflow.modules.ledger..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.eventflow.modules.identity..", "com.eventflow.modules.catalog..",
                            "com.eventflow.modules.ticketing..", "com.eventflow.modules.ordering..");

    /** ordering → catalog/ticketing/payments/ledger SOLO por fachadas de application (fila ordering). */
    @ArchTest
    static final ArchRule ordering_uses_only_facades =
            noClasses().that().resideInAPackage("com.eventflow.modules.ordering..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.eventflow.modules.catalog.domain..",
                            "com.eventflow.modules.catalog.infrastructure..",
                            "com.eventflow.modules.catalog.api..",
                            "com.eventflow.modules.ticketing.domain..",
                            "com.eventflow.modules.ticketing.infrastructure..",
                            "com.eventflow.modules.ticketing.api..",
                            "com.eventflow.modules.payments.domain..",
                            "com.eventflow.modules.payments.infrastructure..",
                            "com.eventflow.modules.ledger.domain..",
                            "com.eventflow.modules.ledger.infrastructure..",
                            "com.eventflow.modules.identity.domain..",
                            "com.eventflow.modules.identity.infrastructure..",
                            "com.eventflow.modules.identity.api..");

    /** catalog → ticketing está PROHIBIDO (celda —); a identity solo por su fachada (S¹). */
    @ArchTest
    static final ArchRule catalog_must_not_touch_ticketing_nor_identity_internals =
            noClasses().that().resideInAPackage("com.eventflow.modules.catalog..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.eventflow.modules.ticketing..",
                            "com.eventflow.modules.identity.domain..",
                            "com.eventflow.modules.identity.infrastructure..",
                            "com.eventflow.modules.identity.api..");

    /** ticketing → catalog solo por la fachada de application (S²); jamás internals. */
    @ArchTest
    static final ArchRule ticketing_uses_only_catalog_facade =
            noClasses().that().resideInAPackage("com.eventflow.modules.ticketing..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.eventflow.modules.catalog.domain..",
                            "com.eventflow.modules.catalog.infrastructure..",
                            "com.eventflow.modules.catalog.api..",
                            "com.eventflow.modules.identity.domain..",
                            "com.eventflow.modules.identity.infrastructure..",
                            "com.eventflow.modules.identity.api..");

    /** La matriz es acíclica por construcción (doc 10 §3 última regla). */
    @ArchTest
    static final ArchRule modules_are_free_of_cycles =
            com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices()
                    .matching("com.eventflow.modules.(*)..").should().beFreeOfCycles();
}
