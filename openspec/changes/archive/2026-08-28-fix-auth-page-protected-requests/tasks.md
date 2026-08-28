## 1. Frontend Authentication Route Isolation

- [x] 1.1 Add a regression test showing that `/user/login/` does not request authentication bootstrap.
- [x] 1.2 Normalize authentication-route matching in runtime initialization and preserve canonical login redirects.

## 2. AI Provider Request Guards

- [x] 2.1 Add Provider page coverage proving no Provider request starts without Provider-view access.
- [x] 2.2 Add Model page coverage proving no Provider request starts without Model-view access.
- [x] 2.3 Gate Provider and Model page data loading by their established platform access capabilities.

## 3. Security Error Encoding

- [x] 3.1 Add backend regression coverage for UTF-8 JSON 401 and 403 error responses.
- [x] 3.2 Explicitly configure UTF-8 before custom authentication and authorization handlers write JSON.

## 4. Verification

- [x] 4.1 Run focused frontend and backend regression suites.
- [x] 4.2 Run frontend type checking and linting required by the repository.
