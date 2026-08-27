## Context

The model billing page uses `ProFormDateTimePicker`, which supplies local date-time strings formatted as `YYYY-MM-DD HH:mm:ss`. The generated billing client forwards these values unchanged. Spring/Jackson binds the request fields to Java `LocalDateTime`, whose deployed contract accepts ISO local date-time strings such as `YYYY-MM-DDTHH:mm:ss`; deserialization therefore returns HTTP 400 before controller service logic runs.

Both cost-price and point-price publication share the page-level `publish` function and the same date-time fields. Backend controller tests already demonstrate successful publication with ISO values.

## Goals / Non-Goals

**Goals:**

- Submit effective start and optional end times in ISO local date-time format for both price types.
- Keep normalization local to the model billing API boundary and cover it with regression tests.
- Preserve already-normalized ISO values and absent optional end times.

**Non-Goals:**

- Changing backend date-time parsing or the generated OpenAPI client.
- Introducing timezone conversion or UTC offsets; the API contract remains local date-time.
- Changing form display formatting, billing lifecycle rules, permissions, or error presentation.

## Decisions

1. Normalize immediately before calling the generated publication clients. A small page-local function replaces the separator space between a valid date and time with `T`. This fixes both price types without altering unrelated requests.

   Alternatives considered: backend acceptance of multiple formats would broaden a stable API contract; global request transformation could mutate unrelated string fields; editing the generated client violates repository rules and would be overwritten by regeneration.

2. Preserve local-time semantics. The transformation changes syntax only and does not parse through `Date`, apply a timezone, or shift the selected wall-clock time.

3. Test observable request payloads for cost-price and point-price publication. Tests SHALL verify start time, optional end time, and idempotence for an existing `T` separator.

## Risks / Trade-offs

- [Risk] A broad space replacement could modify unexpected text → Mitigation: normalize only the two known date-time fields and only the date/time separator.
- [Risk] Future picker output may include milliseconds or already use `T` → Mitigation: retain the suffix and leave already-normalized values unchanged.
- [Risk] The local fix does not make other pages conform automatically → Mitigation: keep this change scoped to the reproduced model billing defect; address other pages through their own tested API boundaries.

## Migration Plan

Deploy the frontend-only change without a database migration. After deployment, publish far-future cost and point price test versions, verify they appear as pending, revoke them, and verify the revoked lifecycle state. Rollback consists of restoring the prior frontend bundle; no persisted schema or API contract changes require reversal.

## Open Questions

None.
