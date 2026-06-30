package com.funjson.metaagent.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.funjson.metaagent.job.application.port.out.JobCompletionStore;
import com.funjson.metaagent.job.application.port.out.JobRunStore;
import com.funjson.metaagent.prompt.application.PromptRegistry;
import com.funjson.metaagent.prompt.domain.PromptUseCase;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自动验证 Application Port、Domain 纯净性和 MyBatis Adapter 边界。
 */
class LayerDependencyTest {

    private static final JavaClasses CLASSES =
            new ClassFileImporter()
                    .withImportOption(
                            new ImportOption.DoNotIncludeTests())
                    .importPackages(
                    "com.funjson.metaagent");

    @Test
    void applicationApiAndDomainMustNotDependOnInfrastructure() {
        noClasses()
                .that()
                .resideInAnyPackage(
                        "..application..",
                        "..api..",
                        "..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..")
                .because("上层必须只依赖 Port 和领域合同")
                .check(CLASSES);
    }

    @Test
    void domainMustRemainFrameworkIndependent() {
        noClasses()
                .that()
                .resideInAnyPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta..",
                        "..application..",
                        "..api..",
                        "..infrastructure..")
                .because("领域状态机和策略必须可脱离框架测试")
                .check(CLASSES);
    }

    @Test
    void mybatisMappersMustStayInsideInfrastructure() {
        classes()
                .that()
                .areAnnotatedWith(Mapper.class)
                .should()
                .resideInAnyPackage(
                        "..infrastructure.persistence.mybatis..")
                .because("SQL Mapper 只能存在于 MyBatis Adapter")
                .check(CLASSES);
    }

    @Test
    void onlyJobApplicationMayDependOnUpperStateWritePorts() {
        noClasses()
                .that()
                .resideInAnyPackage(
                        "..loop.application..",
                        "..task.application..",
                        "..capability.application..",
                        "..recovery.application..",
                        "..provider.application..",
                        "..conversation.application..")
                .should()
                .dependOnClassesThat()
                .areAssignableTo(JobRunStore.class)
                .orShould()
                .dependOnClassesThat()
                .areAssignableTo(JobCompletionStore.class)
                .because("只有 Job 层可以锁定或修改 Job/Task 上层状态")
                .check(CLASSES);
    }

    @Test
    void coreModulesMustNotReverseDependencyDirection() {
        noClasses()
                .that()
                .resideInAnyPackage(
                        "..loop.application..",
                        "..loop.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..task..",
                        "..job..",
                        "..control..")
                .because("Loop 是最下层执行内核，不能反向依赖上层")
                .check(CLASSES);

        noClasses()
                .that()
                .resideInAnyPackage(
                        "..task.application..",
                        "..task.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..job..",
                        "..control..")
                .because("Task 不能反向依赖 Job 或 Control")
                .check(CLASSES);

        noClasses()
                .that()
                .resideInAnyPackage(
                        "..job.application..",
                        "..job.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..control..")
                .because("Job 不能反向依赖 Control")
                .check(CLASSES);
    }

    @Test
    void conversationAndIntentMustRemainIndependentOfExecutionModules() {
        noClasses()
                .that()
                .resideInAnyPackage(
                        "..conversation..",
                        "..intent..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..control..",
                        "..job..",
                        "..task..",
                        "..loop..")
                .because("Conversation 和 Intent 必须保持单一职责")
                .check(CLASSES);
    }

    @Test
    void stereotypesMustStayInTheirArchitecturalLayers() {
        classes()
                .that()
                .areAnnotatedWith(RestController.class)
                .should()
                .resideInAnyPackage("..web.api..")
                .because("HTTP Controller 统一属于 Web Adapter")
                .check(CLASSES);
        classes()
                .that()
                .areAnnotatedWith(Service.class)
                .should()
                .resideInAnyPackage("..application..")
                .check(CLASSES);
        classes()
                .that()
                .areAnnotatedWith(Repository.class)
                .should()
                .resideInAnyPackage("..infrastructure..")
                .check(CLASSES);
    }

    @Test
    void providerAdaptersMustNotOwnPromptSelection() {
        noClasses()
                .that()
                .resideInAnyPackage("..provider.infrastructure..")
                .should()
                .dependOnClassesThat()
                .areAssignableTo(PromptRegistry.class)
                .orShould()
                .dependOnClassesThat()
                .areAssignableTo(PromptUseCase.class)
                .because("Provider Adapter 只能传输已渲染 Prompt")
                .check(CLASSES);
    }

    @Test
    void topLevelCoreModulesMustProvideReadme() {
        java.nio.file.Path sourceRoot = java.nio.file.Path.of(
                "src/main/java/com/funjson/metaagent");
        for (String module : java.util.List.of(
                "conversation",
                "intent",
                "clarification",
                "control",
                "context",
                "file",
                "job",
                "task",
                "loop",
                "runtime",
                "capability",
                "tool",
                "websearch",
                "weather",
                "recovery",
                "observability",
                "profile",
                "web")) {
            org.assertj.core.api.Assertions.assertThat(
                    sourceRoot.resolve(module).resolve("README.md"))
                    .as(module + " module README")
                    .exists()
                    .isRegularFile();
        }
    }
}
