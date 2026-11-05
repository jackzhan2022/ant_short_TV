# 平台运维监控 Implementation Plan

> For agentic workers: execute approved OpenSpec tasks with test-first changes and review at subsystem boundaries. The user approved implementation on 2026-11-05. No additional design approval is required.

**Goal:** 在当前后台实现可用的运维中心、持久化遥测和飞书告警闭环。

**Architecture:** 四个文件所有权互不重叠的部分：基础设施/查询/权限由主执行者负责；采集由telemetry部分负责；规则和通知由alerts部分负责；页面由frontend部分负责。共享接口先约定，再集成回归。新功能在隔离工作区开发，不复制无关角色配音文件。

**Tech Stack:** Java17/SpringBoot3/JdbcTemplate/Hikari/MySQL/H2，React19/AntDesign6/Umi/Vitest。

## Shared backend contracts

- `com.antshorttv.operations.OperationsDatabase`: owns a dedicated maximum-two-connection Hikari pool without registering another Spring DataSource/JdbcTemplate bean; public `JdbcTemplate jdbc()` and `TransactionTemplate transactions()`. Test constructor accepts an existing JdbcTemplate.
- `OperationsSettingsService`: public `Map<String,Object> current()`, `boolean telemetryEnabled()`, `boolean notificationsEnabled(Instant now)` and `String instanceId()`. Reads/writes versioned `ops_runtime_setting` row `config`; defaults include telemetryEnabled=true, notificationsEnabled=false, observationUntil=installation+24h, externalProbeConfigured=false, refreshSeconds=15, minuteRetentionDays=7, hourlyRetentionDays=90, eventRetentionDays=14, projectionRetentionDays=30, alertRetentionDays=90, notificationRetentionDays=30.
- `OperationsMetricSource`: implemented by query service, exposes `Sample sample(String metric, String dimensionsJson, int windowMinutes, Instant end)`. `Sample` contains nullable Double value, long samples, boolean complete. Unknown/unsupported metric or partial window must not produce a healthy sample.
- All timestamps in operations tables are UTC datetime(3); use Timestamp.from(Instant), not JVM-default LocalDateTime. JDBC reads accept both Timestamp and Connector/J LocalDateTime as UTC through OperationsTime.utc. Business source DATETIME is read with getObject(LocalDateTime.class) then converted with ZoneId.systemDefault(), independently from the observer's UTC connection session.
- Schema is owned by the main executor. Other subsystems query/write the declared columns below and request additions rather than editing the migration.

## Shared persistence contract

- `ops_event`: id, event_key(unique), occurred_at, source, code, location, message, stack, fingerprint, user_id, tenant_id, project_id, execution_id, request_id, trace_id, trusted_context, duration_ms, sql_count, sql_ms, attributes_json.
- `ops_metric_bucket`: id, bucket_at, granularity(MINUTE/HOUR), instance_id, family(HTTP/AI/RESOURCE), dimensions_key, dimensions_json, tenant_id,project_id,provider_id,model_id,scene,route,method, count_value, error_count, business_count, sum_ms, max_ms, histogram_json, values_json, updated_at; unique(bucket_at,granularity,instance_id,family,dimensions_key). Typed indexes bound provider/tenant/route/global scans.
- `ops_ai_call_projection`: call_log_id(primary), occurred_at, tenant_id, project_id, provider_id, model_id, scene, service_type, trace_id, execution_id, attempt_id, transport_outcome, business_outcome, status, error_code, duration_ms, prompt_tokens, completion_tokens, updated_at.
- `ops_execution_observation`: id, execution_id, execution_version, tenant_id, project_id, model_id, scene, task_type, status, phase, created_at, started_at, completed_at, observed_at, next_run_at, heartbeat_at, claim_expires_at, point_settlement_status, error_code, error_message, trace_id; unique(execution_id,execution_version).
- `ops_job_heartbeat`: id, instance_id, boot_id, job_code, enabled, schedule_type, interval_ms, schedule_expression, expected_at, started_at, finished_at, succeeded_at, error_at, duration_ms, consecutive_failures, error_message, updated_at; unique(instance_id,boot_id,job_code).
- `ops_runtime_setting`: setting_key(primary), value_json, version, updated_at. `config` row initialized by settings service; other rows contain telemetry cursors, per-family/per-instance finalized coverage ranges and per-window drop/overflow baselines. Known collector inventory never becomes healthy solely because an absent node ages out. HTTP/AI alerts require certified family ranges after queued aggregates commit; evaluator uses a 10-second closed-minute grace.
- `ops_job_lease`: job_key(primary), owner_id, fencing_token, expires_at, updated_at.
- `ops_alert_rule`: id, name, description, enabled, severity, metric, operator, threshold_value, recovery_threshold, window_minutes, sustained_minutes, minimum_samples, cooldown_minutes, dimensions_json, notify, version, created_at, updated_at.
- `ops_alert_state`: state_key(primary), rule_id, rule_version, status, breached_since, last_window_at, healthy_windows, active_event_id, last_notified_at, version, updated_at.
- `ops_alert_event`: id, event_key(unique), rule_id, rule_name, severity, status, dimensions_json, metric_value, threshold_value, sample_count, fired_at, resolved_at, acknowledged_at, acknowledged_by, silence_until, silence_reason, updated_at.
- `ops_alert_action`: id, event_id, action, actor_id, reason, until_at, created_at.
- `ops_notification_outbox`: id, notification_key(unique), event_id(nullable for digest), transition_type, message, status, attempts, next_attempt_at, lease_owner, lease_expires_at, delivered_at, last_error, created_at, updated_at.

## Shared HTTP contract

All APIs use `/api/platform/operations` and existing `{ success,data,errorCode,errorMessage }` envelopes. Lists return `{ items,total,nextCursor }`. API filters: `from`, `to`, `tenantId`, `projectId`, `providerId`, `modelId`, `scene`, `instanceId`, `cursor`, `pageSize` (20/100 max). Dates are ISO Instant. Provider/model/tenant selectors may use numeric inputs in first implementation; no expensive catalogue requests on every refresh.

- `GET /snapshot`: `{from,to,generatedAt,coverage,overview,trend,resources,queue,ai,pipeline,activeAlerts,externalProbeConfigured,health}`.
- overview: requestCount,technicalErrors,businessRejections,errorRate,p50Ms,p95Ms,p99Ms,qps,coveredSeconds,qpsDenominator; qps is an approximate average over observed RESOURCE bucket seconds. All rate units are 0–100 percent; nullable numerical observations stay null.
- trend: array of `{at,requests,errors,p95Ms}`; resources: array of latest `{instanceId,at,cpuPercent,heapUsedBytes,heapMaxBytes,threads,dbActive,dbIdle,dbPending,dbMax,redisStatus}`.
- queue: pending,runnable,retrying,running,oldestRunnableSeconds,expiredLeases,settlementPending; ai: calls,technicalFailures,businessFailures,rateLimited,timeouts,successRate,promptTokens,completionTokens,tokenCoverage.
- pipeline: queueDepth,dropped,dimensionOverflow,lastFlushAt,lastAggregateAt; health: `{status,reasons}` with NORMAL/WARNING/ERROR/NO_DATA.
- `GET /requests`: route-level aggregate items `{route,method,requests,errors,businessRejections,p50Ms,p95Ms,p99Ms,maxMs,sqlCount,sqlMs}`.
- `GET /ai`: lightweight call projection rows, plus `summary` with same AI summary fields; `GET /executions` for execution-version observations.
- `GET /jobs`, `/events`, `/events/{id}`: camelCase projections of corresponding table columns; event details need LOG_VIEW. `/events` also returns at most 20 fingerprint groups under the same time/tenant/project filters. `/jobs` is the current registry, retaining the last boot of lost nodes rather than hiding them outside the displayed hour.
- `GET /executions/{executionId}`: LOG_VIEW required; latest retained matching version context independent from selected observation time, plus attempts/calls within shared from/to, each at most 100, explicit PARTIAL/truncated and versionScope=LATEST_RETAINED_CONTEXT. Attempts use read-only allowlisted source columns without input/output summaries.
- Optional metric `execution.phaseAgeSeconds` requires an explicit phase and observes only current RUNNING execution-version STARTED attempts; DEFERRED/old versions/terminal tasks do not count. No universal stage timeout is seeded.
- `GET /alerts`, `/alerts/{id}/actions`, `/notifications`: camelCase alert/action/delivery rows. `POST /alerts/{id}/acknowledge` body `{reason}`; `POST /alerts/{id}/silence` body `{until,reason}`.
- `GET /rules`, `POST /rules`, `PUT /rules/{id}`: rule fields camelCase; save body includes version for updates. Deactivation uses enabled=false, no destructive delete.
- `GET /settings`: redacted config with version and channelConfigured/signatureConfigured; `PUT /settings`: versioned updates to allowlisted operational fields only.
- Permission codes: PLATFORM_OPERATIONS_VIEW, PLATFORM_OPERATIONS_LOG_VIEW, PLATFORM_OPERATIONS_RULE_EDIT, PLATFORM_OPERATIONS_ALERT_MANAGE, PLATFORM_OPERATIONS_SETTINGS_EDIT. Every controller rejects impersonation through CurrentPrincipal before permission evaluation.

## 1. Infrastructure/query/authentication owner

Files: `backend/.../operations/OperationsDatabase.java`, `OperationsSettingsService.java`, `OperationsQuery.java`, `OperationsQueryService.java`, `OperationsController.java`, `OperationsMetricSource.java`, `OperationsMaintenance.java`, new `V174__platform_operations_monitoring.sql`; tests `Operations*Test.java`.

- [x] First write H2 migration, permission, filtering, query-bound and setting-conflict tests; run `mvn -f backend/pom.xml -Dtest=OperationsPersistenceTest,OperationsQueryServiceTest,OperationsSettingsServiceTest test` and verify intended failures.
- [x] Implement schema/connection isolation/settings, then snapshot and bounded detail SQL; use parameterized queries and allowed dimensions only.
- [x] Implement server-side permission checks, audit and optimistic writes; test public/tenant/impersonation rejection via MockMvc.
- [x] Implement retention, independent external probe script/docs and acceptance workload.

## 2. Telemetry owner

Files: only `operations/telemetry/**`, focused `observability/**`, `MonitoringBuffer`, `AiCallLogWriter` hooks and telemetry tests. No migration, shared root contracts or frontend edits.

- [x] Test histogram merge, request cancellation/exclusion, full queue/DB outage, SQL nested scopes and projection correction before implementation.
- [x] Implement `OperationsTelemetry` public capture methods and pipeline snapshot; async writer, resource sampler, scheduled job aspect and task/call reconciliation.
- [x] Preserve old constructors/monitoring tests; when OperationsTelemetry is present route original events into persistent sink without requiring webhook enabled.
- [x] Verify targeted test classes after coordination to avoid concurrent Maven writes to target.

## 3. Alerts owner

Files: only `operations/alerts/**` and alert tests. Uses shared schema, OperationsDatabase/SettingsService/MetricSource; no controller or frontend edits.

- [x] Test one closed window evaluated twice, insufficient samples, restart, missing windows, recovery and stale lease before implementation.
- [x] Implement default rules, persistent evaluator, cooldown/silence/acknowledge, fenced job ownership, outbox and Feishu delivery attempts.
- [x] Expose service methods for root controller integration; no real webhook calls in tests. Implement persistent hourly digest ownership/source cutover while preserving default legacy behavior until new mode is enabled.
- [x] Report tested public methods and contract adjustments; root owns HTTP wiring.

## 4. Frontend owner

Files: `frontend/src/pages/operations/**`, route/access/menu changes and relevant tests only. No backend edits.

- [x] Query `npx antd info` for every used component before writing JSX; write refresh-race, API and permission rendering tests first.
- [x] Implement typed service, coordinated refresh Hook, page tabs, trends, operational lists, rule forms, alert actions, configuration and details.
- [x] Run Vitest focused tests, Biome, tsc and production build; test slow requests, hidden pages, optimistic conflicts and partial data.

## 5. Integration and acceptance

- [x] Run combined backend tests after sources compile, then broaden to existing monitoring, execution, observability and platform tests.
- [x] Run frontend lint, antd lint, focused tests and build against agreed contracts.
- [x] Fault injection and fixed-fixture benchmarks must report actual duration and results; no claim of production validation without deployment.
- [x] Review spec coverage then code correctness; fix findings, update OpenSpec checkboxes only for verified outcomes, publish development and deployment runbooks.

No merge, production deployment, paid model operation or real notification is included in the current local implementation execution.

V175 adds indexed business-source reconciliation cursors only. H2 migrations are exercised by full application startup tests; actual MySQL migration/EXPLAIN and production notification/probe activation remain deployment gates documented in `docs/operations-monitoring-runbook.md`.
