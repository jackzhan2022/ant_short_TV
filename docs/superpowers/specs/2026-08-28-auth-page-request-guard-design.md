# Authentication Page Request Guard

## Goal

Opening an authentication page must not call authenticated APIs. Authentication failure responses must preserve Chinese error messages.

## Scope

1. Treat `/user/login/` as the same public authentication route as `/user/login`.
2. Do not load AI provider data from the Provider or Model pages until the matching platform access is available.
3. Return JSON authentication and authorization failures as UTF-8.

## Design

The frontend will use one authentication-route predicate that accepts an optional trailing slash. `getInitialState` will skip bootstrap for all authentication routes matched by that predicate.

The Provider and Model pages will check their existing access capabilities before starting provider-related requests. If bootstrap has not established a signed-in user and permissions, the page returns an empty table result or skips the effect, so it cannot issue a protected request during a redirect.

The Spring Security JSON entry point and access-denied handler will set the response character encoding to UTF-8 before writing the JSON body.

## Error Handling

The backend remains authoritative: unauthenticated protected requests still return 401 and unauthorized requests still return 403. The frontend change prevents those requests from being emitted by authentication-page redirect transitions; it does not weaken API protection.

## Verification

- Frontend tests cover `/user/login/` without a bootstrap call.
- Provider and Model page tests prove no provider request occurs without the relevant access capability.
- Backend tests verify 401 and 403 responses declare UTF-8 and preserve their Chinese messages.
