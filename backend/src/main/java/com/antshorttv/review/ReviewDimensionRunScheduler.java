package com.antshorttv.review;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

final class ReviewDimensionRunScheduler {

    <T, R> List<R> execute(List<T> dimensions, int maxConcurrency, Function<T, R> operation) {
        if (dimensions.isEmpty()) return List.of();
        List<R> results = new ArrayList<>(dimensions.size());
        results.add(operation.apply(dimensions.get(0)));
        if (dimensions.size() == 1) return List.copyOf(results);

        int workers = Math.min(Math.max(1, maxConcurrency), dimensions.size() - 1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<Future<R>> futures = new ArrayList<>();
        try {
            for (int index = 1; index < dimensions.size(); index++) {
                T dimension = dimensions.get(index);
                futures.add(executor.submit(() -> operation.apply(dimension)));
            }
            for (Future<R> future : futures) results.add(await(future));
            return List.copyOf(results);
        } finally {
            executor.shutdownNow();
        }
    }

    private <R> R await(Future<R> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待审核维度执行时被中断。", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("审核维度执行失败。", cause);
        }
    }
}
