# Production Task Details MySQL Audit

Date: 2026-09-13

The project `env` MySQL configuration was used through the existing MySQL JDBC driver. Credentials were not printed. All diagnostic and benchmark statements used a read-only connection and a 5-second `MAX_EXECUTION_TIME`; no business rows, task contents, media URLs, or user data were exported.

## Inventory

- MySQL: `8.0.46-0ubuntu0.24.04.3`
- Database: `ant_short_tv`
- Latest Flyway migration: `112`, successful
- Active sessions: `21`
- Existing task data includes image, analysis, review, script operation, storyboard batch, and video decomposition samples.

## Lightweight Query Baseline

The current `ProductionTaskSources` queries were exported with `ExportQueries.java`. Each query ran with `EXPLAIN ANALYZE`, then seven serial reads. Values below are client-side milliseconds and result-row counts.

| Query | Median ms | Max ms | Rows |
|---|---:|---:|---:|
| children | 22 | 71 | 1 |
| count | 19 | 52 | 1 |
| detail | 21 | 325 | 1 |
| filtered | 32 | 40 | 20 |
| list-last | 25 | 61 | 20 |
| list-mine | 23 | 65 | 20 |
| list-team | 27 | 78 | 20 |
| summary | 16 | 18 | 5 |

## Concurrent Read Baseline

Using the exported team-list query, 24 read-only JDBC requests ran through eight workers against the same MySQL database. Each connection set `MAX_EXECUTION_TIME=5000` and did not issue business writes, execution calls, or billing calls.

- Median request time: `196ms`
- Maximum request time: `1634ms`
- Wall-clock time: `2309ms`

The production-task list remained bounded to its normal 20-row page. This establishes the current detail-browsing database-read baseline; application deployment and rollback retain the compatible application-only process in `production-task-details-runbook.md` because this change has no schema migration.

## Remaining Acceptance

This is partial evidence only. It does not cover the new content, collection, text-continuation or media endpoints, response-size limits, concurrent detail browsing, business submission/production-stage latency and throughput budgets, timeout degradation, or a release/rollback exercise. Those checks remain required before task 6.4 and OpenSpec archival can be completed.
