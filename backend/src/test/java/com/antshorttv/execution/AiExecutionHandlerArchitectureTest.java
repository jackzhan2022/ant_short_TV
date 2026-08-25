package com.antshorttv.execution;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;

@AnalyzeClasses(packages = "com.antshorttv", importOptions = ImportOption.DoNotIncludeTests.class)
class AiExecutionHandlerArchitectureTest {

    private static final Set<String> FORBIDDEN_CALL_LOG_TYPES = Set.of(
        "com.antshorttv.ai.AiCallLogWriter",
        "com.antshorttv.ai.AiInvocationLogRequest",
        "com.antshorttv.ai.AiCallLogService",
        "com.antshorttv.ai.AiVideoCallLogEntity"
    );

    @ArchTest
    static final ArchRule handlersUseUnifiedProviderTransport = noClasses()
        .that().areAssignableTo(AiExecutionHandler.class)
        .and().areNotAssignableFrom(AiExecutionHandler.class)
        .should().dependOnClassesThat().resideInAnyPackage(
            "java.net..",
            "org.springframework.web.client..",
            "org.springframework.web.reactive.function.client..",
            "okhttp3.."
        )
        .because("execution handlers must contact providers through AiInvocationService")
        .allowEmptyShould(true);

    @ArchTest
    static final ArchRule handlersDoNotWriteOrSearchCallLogs = noClasses()
        .that().areAssignableTo(AiExecutionHandler.class)
        .and().areNotAssignableFrom(AiExecutionHandler.class)
        .should().dependOnClassesThat(DescribedPredicate.describe(
            "call-log persistence or latest-log lookup types",
            AiExecutionHandlerArchitectureTest::isCallLogPersistenceType
        ))
        .because("execution handlers must use the call-log ID returned by AiInvocationService")
        .allowEmptyShould(true);

    private static boolean isCallLogPersistenceType(JavaClass type) {
        return type.getPackageName().startsWith("org.springframework.jdbc")
            || FORBIDDEN_CALL_LOG_TYPES.contains(type.getName())
            || type.getSimpleName().matches(".*CallLog.*Mapper");
    }
}
