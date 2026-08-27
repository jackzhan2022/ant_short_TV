# Model Billing Date-Time Submission Fix

## Problem

The model billing forms submit Ant Design's local date-time value as
`YYYY-MM-DD HH:mm:ss`. The backend request contract uses Java `LocalDateTime`
and accepts ISO local date-time values such as `YYYY-MM-DDTHH:mm:ss`. Both cost
price and point price publication therefore reach the backend but fail with
HTTP 400 before entering the billing service.

## Design

Normalize `effectiveFrom` and the optional `effectiveTo` at the model billing
page's API boundary. Replace the single separator space between the date and
time with `T`; leave already normalized ISO values unchanged. Use the same
normalization for cost price and point price requests.

Do not change the generated API client, global request interceptor, backend
JSON configuration, or the values shown by the form.

## Error Handling

Existing request error handling remains unchanged. The fix prevents the known
date-time deserialization error; any other backend validation error continues
to use the application's standard error reporting.

## Verification

Add frontend regression coverage proving that both publication requests send
ISO local date-times, including an optional end time. Run the focused model
billing tests and frontend type checking. Retain the backend controller test as
evidence that the API accepts ISO values and supports publish/query/revoke.

After deployment, publish far-future cost and point price versions for the
internal test model, verify their pending lifecycle state, revoke both, and
verify the revoked state.
