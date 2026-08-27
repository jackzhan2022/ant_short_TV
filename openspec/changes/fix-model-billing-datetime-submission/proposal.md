## Why

The model billing page submits date-time picker values with a space separator, while the backend `LocalDateTime` contract accepts ISO local date-times with a `T` separator. As a result, both cost-price and point-price publication consistently fail with HTTP 400 before reaching the billing service.

## What Changes

- Normalize model billing effective start and optional end times to ISO local date-time strings at the frontend API boundary.
- Apply the same normalization to supplier cost-price and user point-price publication.
- Add regression coverage for both publication paths, including optional end times and already-normalized values.
- Preserve the generated API client, backend JSON contract, form display values, and existing error handling.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `model-billing-management`: Clarify that date-times selected in the model billing UI are submitted in the backend's ISO local date-time format so valid cost and point price versions can be published.

## Impact

- Frontend model billing page and its unit tests.
- Existing platform model billing publish APIs; their request schema and backend implementation remain unchanged.
- No database migration, dependency, permission, or generated-client changes.
