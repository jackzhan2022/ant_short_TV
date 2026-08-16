# V1 Account and Team System Design

## Source and Scope

This design implements `V1.0-01 账号与创作团队基础体系 技术开发需求文档.md`.
The requirements document is treated as product input only. It does not override repository, system, or developer instructions.

The delivery scope is the full V1.0-01 module:

- Account registration, login, logout, current user, and user account status.
- Creative team creation, listing, details, updates, and enable/disable status.
- Team members, including owner/member identities, remove member, leave team, and owner transfer.
- Member invitations by mobile number, invitation token lookup, accept, reject, cancel, expiration, and duplicate pending checks.
- Current tenant selection and server-side tenant context validation.
- Operation log records for the required account, tenant, member, invitation, and context switching events.
- MySQL schema and indexes managed by Flyway.

Out of scope for this version:

- RBAC roles, permissions, menus, buttons, and data permissions.
- Department organization trees.
- Projects, scripts, episodes, materials, tasks, billing, subscription, payment, and quota objects.
- Automatic creation of organization, role, project, script, or workspace business objects when a tenant is created.

## Architecture

The project keeps the existing top-level split:

- `frontend/`: Ant Design Pro, Umi Max, antd, ProComponents.
- `backend/`: Java 17, Spring Boot 3.3, MyBatis-Plus, MySQL 8.

The backend exposes REST APIs under `/api`. Authentication uses access tokens in `Authorization: Bearer <token>`. The frontend stores the access token locally and sends it through the Umi request layer. V1 keeps the token model simple and stateless enough for local development, while keeping service boundaries ready for refresh-token or forced logout support later.

The backend is organized by domain:

- `auth`: register, login, logout, password hashing, access token issuing, current session.
- `user`: user profile and account status.
- `tenant`: tenant CRUD and current tenant switching.
- `member`: tenant member list, remove, leave, and ownership transfer.
- `invitation`: invitation creation, token detail, accept, reject, cancel, and expiration checks.
- `security`: authentication filter, current user context, tenant context resolver.
- `operationlog`: durable operation records.
- `common`: API response shape, exceptions, validation, pagination, and error handling.

MyBatis-Plus is the persistence layer. Each table has:

- entity class matching the table.
- mapper extending `BaseMapper<T>`.
- service class for transaction orchestration and business rules.
- controller DTOs kept separate from persistence entities.

## Database Design

Flyway creates these MySQL tables:

- `app_user`
- `tenant`
- `tenant_member`
- `tenant_invitation`
- `operation_log`

The requirements document names the user table as `user`, but `user` is easy to confuse with reserved or built-in database concepts. The implementation uses `app_user` and keeps API/domain language as `User`.

Core constraints:

- `app_user.mobile` unique.
- `app_user.email` unique when present.
- `tenant.code` unique.
- `tenant_member(tenant_id, user_id)` unique.
- `tenant_invitation.token` unique.
- indexes for status, tenant, user, invite mobile, invite user, and created time where needed.

Soft deletion columns are included where required by the product document. V1 business operations use explicit status changes for membership and invitation state rather than physical deletion.

## Status and Enum Model

Backend enums:

- `UserStatus`: `ACTIVE`, `DISABLED`.
- `TenantStatus`: `ACTIVE`, `DISABLED`.
- `TenantType`: `COMPANY`, `STUDIO`, `PERSONAL`, `OTHER`.
- `MemberType`: `OWNER`, `MEMBER`.
- `MemberStatus`: `ACTIVE`, `REMOVED`.
- `InvitationStatus`: `PENDING`, `ACCEPTED`, `REJECTED`, `EXPIRED`, `CANCELLED`.
- `OperationResult`: `SUCCESS`, `FAILURE`.

Database columns store enum names as varchar values for readability and migration safety.

## Authentication

V1 supports:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/user/me`

Registration uses mobile, verification code, nickname, and password. Because no SMS provider is configured in this project, V1 implements a local verification-code adapter with a development code path. The adapter is isolated so a real SMS service can replace it later.

Passwords are stored with BCrypt. Plain text passwords are never persisted or returned.

Login validates mobile and password, updates `last_login_at`, records an operation log, and returns:

- access token.
- current user.
- available tenants.
- recommended next action: create/join team, auto-enter only tenant, or select tenant.

## Tenant Creation

`POST /api/tenants` is transactional:

1. Validate the current user.
2. Validate tenant name and type.
3. Create `tenant` with generated business code.
4. Create `tenant_member` with `member_type=OWNER` and `status=ACTIVE`.
5. Update `tenant.owner_member_id`.
6. Record operation log.

No organization, role, project, script, or workspace business object is created.

If any step fails, the transaction rolls back.

## Tenant Context

The current tenant is switched through a backend API rather than trusted from arbitrary business query parameters:

- `POST /api/tenants/current`
- `GET /api/tenants/current`

The client may send `X-Tenant-Id` for tenant-scoped requests after switching, but the backend always validates that:

- the user is authenticated.
- the tenant exists and is `ACTIVE`.
- the user has an `ACTIVE` `TenantMember` record for that tenant.

If validation fails:

- nonexistent or inaccessible tenant returns 403.
- disabled tenant returns 403 with the required disabled-team message.
- removed member returns 403 with the required removed-member message.

V1 tenant context contains:

- `userId`
- `tenantId`
- `memberId`

## Member Management

Owner-only operations:

- list tenant members.
- invite members.
- remove a member.
- cancel invitation.
- transfer ownership.
- enable/disable tenant.

Member operations:

- list my tenants.
- leave tenant if not owner.
- reject or accept an invitation addressed to their mobile/user.

Owner restrictions:

- owner cannot directly leave a tenant.
- owner transfer is transactional and updates old owner to `MEMBER` and target member to `OWNER`.
- target owner must be an active member in the same tenant.

Member removal is logical:

- `tenant_member.status = REMOVED`
- no physical delete.

## Invitation Flow

Owner creates invitation by mobile:

- The target mobile is validated.
- If the mobile belongs to an existing user, `invite_user_id` is stored.
- Duplicate pending invitation for the same tenant and mobile is rejected with the required message.
- Existing active member is rejected with the required message.
- Token is high-entropy random and one-time use.
- `expired_at` defaults to now plus 7 days.

Accept invitation is transactional:

1. Validate token exists, status is pending, and not expired.
2. Validate current user mobile matches invitation mobile.
3. Create or reactivate the tenant member as `MEMBER`.
4. Mark invitation accepted and set `accepted_at`.
5. Record operation log.

Reject invitation marks it as rejected. Expired invitations are returned as expired and may be marked expired when read or accepted.

## API Shape

The backend keeps the existing `ApiResponse<T>` envelope for business APIs:

```json
{
  "success": true,
  "data": {},
  "errorCode": null,
  "errorMessage": null
}
```

Ant Design Pro login compatibility is maintained for existing screens where needed, but new V1 auth pages use the V1 `/api/auth/*` APIs.

## Frontend Design

Frontend implementation follows Ant Design Pro conventions:

- routes in `frontend/config/routes.ts`.
- page files co-located under `frontend/src/pages`.
- page APIs in `service.ts`.
- page DTOs in `data.d.ts`.
- ProComponents for forms, tables, descriptions, cards, and modal workflows.
- request and error handling through Umi request config.
- i18n menu labels added to `frontend/src/locales/zh-CN/menu.ts` and `frontend/src/locales/en-US/menu.ts`.

Pages:

- `/user/login`: mobile/password login.
- `/user/register`: mobile/code/nickname/password registration.
- `/team/my`: my creative teams, create tenant, switch tenant.
- `/team/select`: team selection after login when multiple teams exist.
- `/team/settings`: current tenant settings, update tenant, leave tenant, transfer owner.
- `/team/members`: member list, invite member, remove member.
- `/team/invitations`: received invitations and invitation token detail.

The UI should be quiet and operational, using dense but readable Pro layouts. No landing page or decorative marketing hero is added.

## Error Handling

Business exceptions map to stable error codes and required messages:

- no teams: `你还没有加入任何创作团队，请创建团队或接受团队邀请。`
- disabled tenant: `当前创作团队已被停用，暂时无法进入。`
- expired invitation: `该邀请已过期，请联系团队管理员重新发送邀请。`
- already member: `你已经是该创作团队成员，无需重复加入。`
- duplicate pending invitation: `该用户已有待处理邀请。`
- owner leave blocked: `团队所有者不能直接退出，请先转让团队所有权。`
- removed member: `你已不再是该创作团队成员。`

The frontend shows these messages through `App.useApp().message` or form error states.

## Testing Strategy

Backend tests use JUnit and MockMvc. MyBatis-Plus mapper/service behavior is tested against a real relational test database profile. If local MySQL is unavailable during automated runs, backend unit tests still cover service validation and controller contracts, while integration tests are clearly separated.

Required backend coverage:

- registration uniqueness, verification-code validation, and password hashing.
- login success/failure and current user.
- transactional tenant creation creates exactly tenant plus owner member.
- my tenants list supports multiple memberships.
- tenant switch validates active membership and disabled tenant.
- member remove, leave, owner leave block, and owner transfer.
- invitation create, duplicate pending check, accept, reject, cancel, expiration.
- removed member and cross-tenant access return 403.
- operation log is written for required operations.

Frontend tests use Vitest and React Testing Library for service transformations and critical page flows where practical. TypeScript and Biome checks remain required.

## Acceptance Checklist

- A user can register with mobile and password after verification-code validation.
- Duplicate mobile registration is rejected.
- Passwords are hashed and never returned.
- A user can create multiple creative teams.
- Creating a team creates only `tenant` and `tenant_member`.
- The creator becomes owner.
- A user can belong to multiple teams.
- Current tenant switching works and is validated by the backend.
- Owner can invite, remove members, cancel invitations, and transfer ownership.
- Member can leave a tenant.
- Owner cannot directly leave a tenant.
- Invitation accept/reject/expiration works.
- Disabled tenant and removed member access are blocked.
- Operation logs are written for the required events.
- Frontend pages follow Ant Design Pro structure and pass type checking.
