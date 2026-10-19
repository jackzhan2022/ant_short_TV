package com.antshorttv.workflowagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Retries only rolled-back save transactions; model invocation stays outside this boundary. */
@Service
public class StoryboardSaveService {
    private static final Logger LOG = LoggerFactory.getLogger(StoryboardSaveService.class);
    private static final int MAX_ATTEMPTS = 3;
    private final StoryboardToolDataService data;
    private final TransactionTemplate transactions;

    public StoryboardSaveService(StoryboardToolDataService data, PlatformTransactionManager manager) {
        this.data = data;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public JsonNode saveEpisodeStoryboards(ToolExecutionContext context, JsonNode payload) {
        for (int attempt = 1; ; attempt++) {
            context.requireBeforeDeadline();
            try {
                return transactions.execute(status -> data.saveEpisodeStoryboards(context, payload.deepCopy()));
            } catch (RuntimeException failure) {
                if (attempt >= MAX_ATTEMPTS || !isMysqlDeadlock(failure)
                    || Thread.currentThread().isInterrupted()) throw failure;
                context.requireBeforeDeadline();
                LOG.warn("Retrying storyboard save transaction after deadlock runId={} episodeId={} attempt={}/{}",
                    context.agentRunId(), context.episodeId(), attempt + 1, MAX_ATTEMPTS);
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextLong(25, 76) * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw failure;
                }
            }
        }
    }

    private boolean isMysqlDeadlock(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        var pending = new ArrayDeque<Throwable>();
        pending.add(failure);
        while (!pending.isEmpty()) {
            Throwable cause = pending.removeFirst();
            if (!seen.add(cause)) continue;
            if (cause instanceof SQLException sql) {
                if (sql.getErrorCode() == 1213) return true;
                if (sql.getNextException() != null) pending.addLast(sql.getNextException());
            }
            if (cause.getCause() != null) pending.addLast(cause.getCause());
        }
        return false;
    }
}
