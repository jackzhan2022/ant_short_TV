package com.antshorttv.observability;

public final class DatabaseQueryObservation {
    private static final ThreadLocal<Accumulator> CURRENT = new ThreadLocal<>();

    private DatabaseQueryObservation() {}

    public static Scope open() {
        CURRENT.set(new Accumulator());
        return new Scope();
    }

    public static void recordQuery(long durationNanos) {
        Accumulator accumulator = CURRENT.get();
        if (accumulator != null) accumulator.add(durationNanos);
    }

    public static long[] snapshot() {
        Accumulator accumulator = CURRENT.get();
        return accumulator == null ? new long[] {0L, 0L} : new long[] {accumulator.count, accumulator.durationNanos};
    }

    public static final class Scope implements AutoCloseable {
        @Override
        public void close() {
            CURRENT.remove();
        }
    }

    private static final class Accumulator {
        private long count;
        private long durationNanos;

        private void add(long durationNanos) {
            count++;
            this.durationNanos += Math.max(0L, durationNanos);
        }
    }
}
