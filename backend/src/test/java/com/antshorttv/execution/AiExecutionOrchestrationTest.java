package com.antshorttv.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(properties = "ai.execution.dispatcher.enabled=false")
@Import(AiExecutionOrchestrationTest.HandlerConfiguration.class)
class AiExecutionOrchestrationTest {

    @Autowired
    private AiExecutionService executionService;

    @Autowired
    private AiExecutionWorker worker;

    @Autowired
    private AiExecutionDispatcher dispatcher;

    @Autowired
    private AiExecutionHandlerRegistry handlerRegistry;

    @Autowired
    private AiExecutionTaskMapper taskMapper;

    @Autowired
    private TransactionProbe transactionProbe;

    @Test
    void workerRunsProviderPhaseOutsideTransactionAndFinalizesSeparately() {
        AiExecutionTaskEntity task = executionService.create(command("worker-outside-transaction"));

        worker.run(task.id);

        AiExecutionTaskEntity completed = taskMapper.selectById(task.id);
        assertThat(transactionProbe.transactionActive.get()).isFalse();
        assertThat(completed.status).isEqualTo(AiExecutionStatus.SUCCEEDED.name());
        assertThat(completed.resultType).isEqualTo("TEST_RESULT");
        assertThat(completed.resultId).isEqualTo(9901L);
    }

    @Test
    void registryRejectsUnknownScenesAndDispatcherScanIsBounded() {
        executionService.create(command("bounded-scan-1"));
        executionService.create(command("bounded-scan-2"));
        executionService.create(command("bounded-scan-3"));

        List<Long> eligible = dispatcher.eligibleExecutionIds(LocalDateTime.now().plusSeconds(1), 2);

        assertThat(eligible).hasSize(2);
        assertThatThrownBy(() -> handlerRegistry.require("UNKNOWN_SCENE"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void taskControlsEnforceStateTransitions() {
        AiExecutionTaskEntity pending = executionService.create(command("cancel-pending"));
        AiExecutionTaskEntity canceled = executionService.cancel(pending.id);

        assertThat(canceled.status).isEqualTo(AiExecutionStatus.CANCELED.name());
        assertThatThrownBy(() -> executionService.retry(canceled.id))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_EXECUTION_STATUS_INVALID));
        assertThatThrownBy(() -> executionService.regenerate(
            canceled.id,
            "invalid-regeneration",
            "trace-invalid-regeneration"
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_EXECUTION_STATUS_INVALID));
    }

    private AiExecutionCreateCommand command(String key) {
        return new AiExecutionCreateCommand(
            9101L,
            9102L,
            9103L,
            "ORCHESTRATION_TEST",
            "TEXT_GENERATION",
            "TEST_RESOURCE",
            9104L,
            null,
            "SUBMIT",
            key,
            "trace-" + key,
            false,
            null
        );
    }

    @TestConfiguration
    static class HandlerConfiguration {
        @Bean
        TransactionProbe transactionProbe() {
            return new TransactionProbe();
        }

        @Bean
        AiExecutionHandler orchestrationTestHandler(TransactionProbe probe) {
            return new AiExecutionHandler() {
                @Override
                public String scene() {
                    return "ORCHESTRATION_TEST";
                }

                @Override
                public AiExecutionHandlerResult execute(AiExecutionContext context) {
                    probe.transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
                    return new AiExecutionHandlerResult("TEST_RESULT", 9901L);
                }
            };
        }
    }

    static class TransactionProbe {
        private final AtomicBoolean transactionActive = new AtomicBoolean(true);
    }
}
