package com.antshorttv.observability;

public final class DatabaseQueryObservation {
    private static final ThreadLocal<Accumulator> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Integer> QUERY_DEPTH = ThreadLocal.withInitial(() -> 0);

    private DatabaseQueryObservation() {}

    public static Scope open() {
        Accumulator previous = CURRENT.get();
        Accumulator current=new Accumulator();CURRENT.set(current);
        return new Scope(previous,current);
    }

    public static QueryScope query() {
        int depth = QUERY_DEPTH.get();
        QUERY_DEPTH.set(depth + 1);
        return new QueryScope(depth, System.nanoTime());
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
        private final Accumulator previous;
        private final Accumulator current;
        private boolean closed;
        private Scope(Accumulator previous,Accumulator current) { this.previous = previous;this.current=current; }
        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) CURRENT.remove(); else {previous.count+=current.count;previous.durationNanos+=current.durationNanos;CURRENT.set(previous);}
        }
    }

    public static final class QueryScope implements AutoCloseable {
        private final int depth;
        private final long started;
        private boolean closed;
        private QueryScope(int depth, long started) { this.depth = depth; this.started = started; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            if (depth == 0) { QUERY_DEPTH.remove(); recordQuery(System.nanoTime() - started); }
            else QUERY_DEPTH.set(depth);
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
