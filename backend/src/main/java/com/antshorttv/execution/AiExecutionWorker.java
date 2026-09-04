package com.antshorttv.execution;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiExecutionWorker {
    private final AiExecutionService executionService;
    private final AiExecutionClaimService claimService;
    private final AiExecutionHandlerRegistry handlerRegistry;
    private final AiExecutionLeaseKeeper leaseKeeper;
    private final Duration claimTimeout;

    public AiExecutionWorker(
        AiExecutionService executionService,
        AiExecutionClaimService claimService,
        AiExecutionHandlerRegistry handlerRegistry,
        AiExecutionLeaseKeeper leaseKeeper,
        @Value("${ai.execution.claim-timeout:PT10M}") Duration claimTimeout
    ) {
        this.executionService = executionService;
        this.claimService = claimService;
        this.handlerRegistry = handlerRegistry;
        this.leaseKeeper = leaseKeeper;
        this.claimTimeout = claimTimeout;
    }

    public void run(Long executionId) {
        String claimToken = UUID.randomUUID().toString();
        AiExecutionClaim claim = claimService.claim(executionId, claimToken, LocalDateTime.now(), claimTimeout);
        if (claim == null) {
            return;
        }
        AiExecutionTaskEntity task = executionService.requireTask(executionId);
        AiExecutionHandler handler = handlerRegistry.require(task.scene);
        try (AiExecutionLeaseKeeper.Lease lease = leaseKeeper.keepAlive(claim)) {
            try {
                handler.validate(task);
                AiExecutionHandlerResult result = handler.execute(new AiExecutionContext(task, claim));
                lease.assertOwned();
                claimService.markSucceeded(
                    executionId,
                    claim.attemptId(),
                    claimToken,
                    result.resultType(),
                    result.resultId(),
                    LocalDateTime.now()
                );
            } catch (AiExecutionClaimLostException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                lease.assertOwned();
                claimService.markFailed(
                    executionId,
                    claim.attemptId(),
                    claimToken,
                    "HANDLER_FAILURE",
                    exception.getMessage(),
                    handler.retryPolicy(),
                    LocalDateTime.now()
                );
            }
        }
    }
}
