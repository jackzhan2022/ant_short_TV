# AI image publication dispatch

Image generation and result publication use separate executors. A slow image
provider cannot occupy the threads needed to publish a completed display image.
The existing image handler, claim token, atomic publication transaction, and
accounting idempotency checks remain responsible for completing each task.

The publication scan selects image executions whose linked, non-deleted image
task is `SETTLING`, `RENDERING`, or `SUCCESS`. Matching includes execution,
business, tenant, and project IDs. This also picks up previously deferred tasks
without a migration or another provider call. Other executions use the original
generation executor.

Publication claims do not consume tenant or model generation limits. Generation
claims still enforce those limits, excluding running publications from their
counts. Provider concurrency limits remain unchanged.

Each process deduplicates execution IDs while they are queued or running. The
database claim still protects execution across instances. Full executor queues
reject admission without acquiring a claim; the next scheduled scan retries the
pending task. Scheduler threads never run rejected provider calls themselves.

| Setting | Default |
| --- | --- |
| `AI_EXECUTION_WORKER_THREADS` | 4 |
| `AI_EXECUTION_QUEUE_CAPACITY` | 100 |
| `AI_IMAGE_PUBLICATION_WORKER_THREADS` | 2 |
| `AI_IMAGE_PUBLICATION_QUEUE_CAPACITY` | 32 |

During deployment, drain the old generation executor before restarting the
backend. Stop admitting work and remove only queued, unclaimed runnables; allow
active provider calls to finish naturally. Their durable pending executions are
resumed by the new dispatcher. Do not stop a backend with active provider calls
or mark unfinished tasks successful directly in the database.

Verify that old `RENDERING` tasks with ready display objects become successful
while new generation calls remain in flight, that publication runs on
`ai-image-publication-*` threads, and that existing successful provider calls are
not submitted again.
