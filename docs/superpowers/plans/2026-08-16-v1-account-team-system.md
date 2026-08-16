# V1 Account and Team System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the full V1.0-01 account and creative-team foundation using Java 17, Spring Boot, MyBatis-Plus, MySQL, and Ant Design Pro.

**Architecture:** Backend domains are split into auth, user, tenant, member, invitation, security, operationlog, and common packages. MyBatis-Plus handles persistence against Flyway-managed MySQL tables. Frontend pages follow Ant Design Pro route/page/service/data co-location and use ProComponents for operational workflows.

**Tech Stack:** Java 17, Spring Boot 3.3, MyBatis-Plus, MySQL 8, Flyway, Spring Security, BCrypt, JWT-style access tokens, JUnit/MockMvc, Umi Max, Ant Design Pro, antd, ProComponents, Vitest.

---

## File Structure

Backend files to create or modify:

- Modify: `backend/pom.xml` - add MyBatis-Plus, MySQL, Flyway, JWT/test dependencies.
- Modify: `backend/src/main/resources/application.yml` - configure MySQL, Flyway, MyBatis-Plus.
- Create: `backend/src/main/resources/db/migration/V1__account_team_schema.sql` - MySQL schema and indexes.
- Create: `backend/src/main/java/com/antshorttv/common/BusinessException.java`
- Create: `backend/src/main/java/com/antshorttv/common/ErrorCode.java`
- Create: `backend/src/main/java/com/antshorttv/common/GlobalExceptionHandler.java`
- Modify: `backend/src/main/java/com/antshorttv/common/ApiResponse.java`
- Create: `backend/src/main/java/com/antshorttv/security/AccessTokenService.java`
- Create: `backend/src/main/java/com/antshorttv/security/CurrentUser.java`
- Create: `backend/src/main/java/com/antshorttv/security/CurrentUserHolder.java`
- Create: `backend/src/main/java/com/antshorttv/security/AccessTokenAuthenticationFilter.java`
- Create: `backend/src/main/java/com/antshorttv/security/TenantContext.java`
- Create: `backend/src/main/java/com/antshorttv/security/TenantContextResolver.java`
- Modify: `backend/src/main/java/com/antshorttv/config/SecurityConfig.java`
- Create: `backend/src/main/java/com/antshorttv/user/UserEntity.java`
- Create: `backend/src/main/java/com/antshorttv/user/UserMapper.java`
- Create: `backend/src/main/java/com/antshorttv/user/UserStatus.java`
- Create: `backend/src/main/java/com/antshorttv/auth/AuthController.java`
- Create: `backend/src/main/java/com/antshorttv/auth/AuthService.java`
- Create: `backend/src/main/java/com/antshorttv/auth/RegisterRequest.java`
- Create: `backend/src/main/java/com/antshorttv/auth/LoginByMobileRequest.java`
- Create: `backend/src/main/java/com/antshorttv/auth/AuthSessionResponse.java`
- Create: `backend/src/main/java/com/antshorttv/auth/VerificationCodeService.java`
- Create: `backend/src/main/java/com/antshorttv/tenant/TenantEntity.java`
- Create: `backend/src/main/java/com/antshorttv/tenant/TenantMapper.java`
- Create: `backend/src/main/java/com/antshorttv/tenant/TenantService.java`
- Create: `backend/src/main/java/com/antshorttv/tenant/TenantController.java`
- Create: `backend/src/main/java/com/antshorttv/tenant/TenantStatus.java`
- Create: `backend/src/main/java/com/antshorttv/tenant/TenantType.java`
- Create: `backend/src/main/java/com/antshorttv/tenant/CreateTenantRequest.java`
- Create: `backend/src/main/java/com/antshorttv/tenant/UpdateTenantRequest.java`
- Create: `backend/src/main/java/com/antshorttv/tenant/TenantSummaryResponse.java`
- Create: `backend/src/main/java/com/antshorttv/tenant/SwitchTenantRequest.java`
- Create: `backend/src/main/java/com/antshorttv/member/TenantMemberEntity.java`
- Create: `backend/src/main/java/com/antshorttv/member/TenantMemberMapper.java`
- Create: `backend/src/main/java/com/antshorttv/member/TenantMemberService.java`
- Create: `backend/src/main/java/com/antshorttv/member/TenantMemberController.java`
- Create: `backend/src/main/java/com/antshorttv/member/MemberType.java`
- Create: `backend/src/main/java/com/antshorttv/member/MemberStatus.java`
- Create: `backend/src/main/java/com/antshorttv/member/TransferOwnerRequest.java`
- Create: `backend/src/main/java/com/antshorttv/invitation/TenantInvitationEntity.java`
- Create: `backend/src/main/java/com/antshorttv/invitation/TenantInvitationMapper.java`
- Create: `backend/src/main/java/com/antshorttv/invitation/TenantInvitationService.java`
- Create: `backend/src/main/java/com/antshorttv/invitation/TenantInvitationController.java`
- Create: `backend/src/main/java/com/antshorttv/invitation/InvitationStatus.java`
- Create: `backend/src/main/java/com/antshorttv/invitation/CreateInvitationRequest.java`
- Create: `backend/src/main/java/com/antshorttv/operationlog/OperationLogEntity.java`
- Create: `backend/src/main/java/com/antshorttv/operationlog/OperationLogMapper.java`
- Create: `backend/src/main/java/com/antshorttv/operationlog/OperationLogService.java`
- Replace or adapt: `backend/src/main/java/com/antshorttv/user/UserController.java` - keep Ant Design Pro compatibility or forward to new auth/user APIs.

Backend tests to create or modify:

- Create: `backend/src/test/java/com/antshorttv/auth/AuthControllerTest.java`
- Create: `backend/src/test/java/com/antshorttv/tenant/TenantControllerTest.java`
- Create: `backend/src/test/java/com/antshorttv/member/TenantMemberControllerTest.java`
- Create: `backend/src/test/java/com/antshorttv/invitation/TenantInvitationControllerTest.java`
- Create: `backend/src/test/java/com/antshorttv/security/TenantContextResolverTest.java`
- Modify: `backend/src/test/java/com/antshorttv/user/UserControllerTest.java`

Frontend files to create or modify:

- Modify: `frontend/config/routes.ts`
- Modify: `frontend/src/app.tsx`
- Modify: `frontend/src/requestErrorConfig.ts`
- Modify: `frontend/src/pages/user/login/index.tsx`
- Modify: `frontend/src/pages/user/register/index.tsx`
- Create: `frontend/src/services/account-team/types.ts`
- Create: `frontend/src/services/account-team/auth.ts`
- Create: `frontend/src/services/account-team/tenant.ts`
- Create: `frontend/src/services/account-team/member.ts`
- Create: `frontend/src/services/account-team/invitation.ts`
- Create: `frontend/src/pages/team/my/index.tsx`
- Create: `frontend/src/pages/team/my/service.ts`
- Create: `frontend/src/pages/team/my/data.d.ts`
- Create: `frontend/src/pages/team/select/index.tsx`
- Create: `frontend/src/pages/team/settings/index.tsx`
- Create: `frontend/src/pages/team/settings/service.ts`
- Create: `frontend/src/pages/team/members/index.tsx`
- Create: `frontend/src/pages/team/members/service.ts`
- Create: `frontend/src/pages/team/invitations/index.tsx`
- Create: `frontend/src/pages/team/invitations/service.ts`
- Modify: `frontend/src/locales/zh-CN/menu.ts`
- Modify: `frontend/src/locales/en-US/menu.ts`

## Task 1: Backend Dependencies and MySQL Schema

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/db/migration/V1__account_team_schema.sql`

- [ ] **Step 1: Write the failing schema smoke test**

Create `backend/src/test/java/com/antshorttv/schema/SchemaMigrationTest.java`:

```java
package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class SchemaMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void flywayCreatesAccountTeamTables() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer tableCount = jdbc.queryForObject("""
            select count(*)
            from information_schema.tables
            where table_schema = database()
              and table_name in ('app_user', 'tenant', 'tenant_member', 'tenant_invitation', 'operation_log')
            """, Integer.class);

        assertThat(tableCount).isEqualTo(5);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=SchemaMigrationTest test`

Expected: FAIL because Flyway/MySQL test configuration and tables do not exist yet.

- [ ] **Step 3: Add dependencies**

Add to `backend/pom.xml`:

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.9</version>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 4: Configure datasource**

Update `backend/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: ${MYSQL_URL:jdbc:mysql://localhost:3306/ant_short_tv?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}
    username: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver
  flyway:
    enabled: true
    locations: classpath:db/migration
  application:
    name: ant-short-tv-backend

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto
```

Create test profile `backend/src/test/resources/application.yml` with H2 MySQL compatibility for automated tests:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:ant_short_tv;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
    username: sa
    password:
    driver-class-name: org.h2.Driver
  flyway:
    enabled: true
    locations: classpath:db/migration
```

- [ ] **Step 5: Add Flyway migration**

Create all required tables in `backend/src/main/resources/db/migration/V1__account_team_schema.sql`.
The migration must include the unique keys and indexes from the design:

```sql
create table app_user (
  id bigint primary key auto_increment,
  mobile varchar(32) not null,
  email varchar(128) null,
  password_hash varchar(255) not null,
  nickname varchar(64) not null,
  avatar varchar(512) null,
  status varchar(32) not null,
  last_login_at datetime null,
  created_at datetime not null,
  updated_at datetime not null,
  deleted_at datetime null,
  unique key uk_app_user_mobile (mobile),
  unique key uk_app_user_email (email),
  index idx_app_user_status (status)
);

create table tenant (
  id bigint primary key auto_increment,
  code varchar(32) not null,
  name varchar(64) not null,
  type varchar(32) not null,
  logo varchar(512) null,
  description text null,
  status varchar(32) not null,
  owner_member_id bigint null,
  created_at datetime not null,
  updated_at datetime not null,
  deleted_at datetime null,
  unique key uk_tenant_code (code),
  index idx_tenant_status (status)
);

create table tenant_member (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  user_id bigint not null,
  member_type varchar(32) not null,
  status varchar(32) not null,
  joined_at datetime not null,
  invited_by bigint null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_tenant_member_tenant_user (tenant_id, user_id),
  index idx_tenant_member_user_id (user_id),
  index idx_tenant_member_tenant_id (tenant_id),
  index idx_tenant_member_status (status)
);

create table tenant_invitation (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  invite_mobile varchar(32) not null,
  invite_user_id bigint null,
  invited_by bigint not null,
  token varchar(128) not null,
  status varchar(32) not null,
  expired_at datetime not null,
  accepted_at datetime null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_tenant_invitation_token (token),
  index idx_tenant_invitation_tenant_id (tenant_id),
  index idx_tenant_invitation_invite_user_id (invite_user_id),
  index idx_tenant_invitation_invite_mobile (invite_mobile),
  index idx_tenant_invitation_status (status)
);

create table operation_log (
  id bigint primary key auto_increment,
  user_id bigint null,
  tenant_id bigint null,
  operation varchar(64) not null,
  target_id bigint null,
  result varchar(32) not null,
  ip varchar(64) null,
  user_agent varchar(512) null,
  created_at datetime not null,
  index idx_operation_log_user_id (user_id),
  index idx_operation_log_tenant_id (tenant_id),
  index idx_operation_log_operation (operation),
  index idx_operation_log_created_at (created_at)
);
```

- [ ] **Step 6: Run migration test**

Run: `mvn -Dtest=SchemaMigrationTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

Run:

```bash
git add backend/pom.xml backend/src/main/resources/application.yml backend/src/main/resources/db/migration/V1__account_team_schema.sql backend/src/test/resources/application.yml backend/src/test/java/com/antshorttv/schema/SchemaMigrationTest.java
git commit -m "feat: add account team database schema"
```

## Task 2: Common Errors, Entities, Mappers, and Operation Logs

**Files:**
- Create common exception classes.
- Create entity, enum, mapper files for user, tenant, member, invitation, and operation log.
- Create operation log service.

- [ ] **Step 1: Write failing mapper/entity test**

Create `backend/src/test/java/com/antshorttv/user/UserMapperTest.java`:

```java
package com.antshorttv.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void insertsAndFindsUserByMobile() {
        UserEntity user = new UserEntity();
        user.setMobile("13800000001");
        user.setPasswordHash("{bcrypt}hash");
        user.setNickname("张三");
        user.setStatus(UserStatus.ACTIVE.name());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userMapper.insert(user);

        UserEntity found = userMapper.selectByMobile("13800000001");
        assertThat(found.getId()).isNotNull();
        assertThat(found.getNickname()).isEqualTo("张三");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=UserMapperTest test`

Expected: FAIL because entity and mapper do not exist.

- [ ] **Step 3: Implement common and mapper foundation**

Implement:

```java
public enum ErrorCode {
    UNAUTHORIZED,
    FORBIDDEN,
    VALIDATION_ERROR,
    DUPLICATE_MOBILE,
    INVALID_CREDENTIALS,
    TENANT_DISABLED,
    MEMBER_REMOVED,
    OWNER_LEAVE_BLOCKED,
    INVITATION_EXPIRED,
    DUPLICATE_PENDING_INVITATION,
    ALREADY_TENANT_MEMBER,
    NOT_FOUND
}
```

`BusinessException` carries `ErrorCode` and message.
`GlobalExceptionHandler` converts it to `ApiResponse.fail(code, message)` with suitable HTTP status.

Add MyBatis mapper scanning to `AntShortTvApplication`:

```java
@MapperScan("com.antshorttv")
@SpringBootApplication
public class AntShortTvApplication {
    public static void main(String[] args) {
        SpringApplication.run(AntShortTvApplication.class, args);
    }
}
```

Each mapper extends `BaseMapper<Entity>`. Add targeted default methods using `LambdaQueryWrapper`, for example:

```java
default UserEntity selectByMobile(String mobile) {
    return selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getMobile, mobile));
}
```

- [ ] **Step 4: Run mapper test**

Run: `mvn -Dtest=UserMapperTest test`

Expected: PASS.

- [ ] **Step 5: Add operation log service test first**

Create `backend/src/test/java/com/antshorttv/operationlog/OperationLogServiceTest.java` that records `LOGIN` and asserts one row exists.

- [ ] **Step 6: Implement operation log service**

Implement `OperationLogService.record(Long userId, Long tenantId, String operation, Long targetId, String result, HttpServletRequest request)` and ensure it never blocks the main business operation because request metadata is optional.

- [ ] **Step 7: Commit**

Run:

```bash
git add backend/src/main/java backend/src/test/java/com/antshorttv/user/UserMapperTest.java backend/src/test/java/com/antshorttv/operationlog/OperationLogServiceTest.java
git commit -m "feat: add account team persistence foundation"
```

## Task 3: Authentication and Current User

**Files:**
- Create auth/security classes and tests.
- Modify `SecurityConfig`.
- Adapt `UserController` compatibility endpoints.

- [ ] **Step 1: Write failing auth controller tests**

Create `backend/src/test/java/com/antshorttv/auth/AuthControllerTest.java` with tests:

```java
@Test
void registersUserWithHashedPasswordAndRejectsDuplicateMobile() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"mobile":"13800000002","verificationCode":"123456","nickname":"李四","password":"Password123"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data.user.mobile", is("13800000002")));

    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"mobile":"13800000002","verificationCode":"123456","nickname":"李四","password":"Password123"}
                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode", is("DUPLICATE_MOBILE")));
}

@Test
void logsInByMobilePasswordAndReturnsAccessToken() throws Exception {
    registerUser("13800000003", "Password123", "王五");

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"mobile":"13800000003","password":"Password123"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.data.user.mobile", is("13800000003")));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -Dtest=AuthControllerTest test`

Expected: FAIL because `/api/auth/*` does not exist.

- [ ] **Step 3: Implement auth**

Implement `AuthService` with:

- `register(RegisterRequest, HttpServletRequest)`
- `login(LoginByMobileRequest, HttpServletRequest)`
- `logout(HttpServletRequest)`
- `currentUser()`

Use BCrypt through Spring `PasswordEncoder`.
Use `VerificationCodeService` with dev code `123456`.
Implement `AccessTokenService` with signed token containing user id and expiration.
Implement `AccessTokenAuthenticationFilter` to populate `CurrentUserHolder`.

- [ ] **Step 4: Run auth tests**

Run: `mvn -Dtest=AuthControllerTest test`

Expected: PASS.

- [ ] **Step 5: Update Ant Design Pro compatibility tests**

Modify `UserControllerTest` so `/api/currentUser` requires a valid token for real user data, while `/api/login/account` either delegates to `/api/auth/login` shape or is retained only for compatibility.

- [ ] **Step 6: Commit**

Run:

```bash
git add backend/src/main/java backend/src/test/java/com/antshorttv/auth/AuthControllerTest.java backend/src/test/java/com/antshorttv/user/UserControllerTest.java
git commit -m "feat: implement mobile authentication"
```

## Task 4: Tenant Creation, My Teams, and Current Tenant

**Files:**
- Create tenant service/controller/DTOs.
- Create tenant context resolver.

- [ ] **Step 1: Write failing tenant tests**

Create `backend/src/test/java/com/antshorttv/tenant/TenantControllerTest.java` with tests:

- creating tenant returns tenant and owner membership.
- creating tenant does not insert invitations or operation business objects other than log.
- my tenants returns multiple teams for the same user.
- switching tenant succeeds for active member.
- switching disabled tenant returns `TENANT_DISABLED`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -Dtest=TenantControllerTest test`

Expected: FAIL because tenant endpoints do not exist.

- [ ] **Step 3: Implement tenant service**

Endpoints:

- `POST /api/tenants`
- `GET /api/tenants/my`
- `GET /api/tenants/{id}`
- `PUT /api/tenants/{id}`
- `PUT /api/tenants/{id}/status`
- `POST /api/tenants/current`
- `GET /api/tenants/current`

Business rules:

- tenant name trimmed length is 2-50.
- tenant name cannot be blank or all special characters.
- tenant code is generated as uppercase `T` plus random alphanumeric characters.
- create tenant is `@Transactional`.
- only owner can update tenant status.

- [ ] **Step 4: Run tenant tests**

Run: `mvn -Dtest=TenantControllerTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add backend/src/main/java/com/antshorttv/tenant backend/src/main/java/com/antshorttv/security backend/src/test/java/com/antshorttv/tenant/TenantControllerTest.java
git commit -m "feat: implement tenant workspace management"
```

## Task 5: Members, Leave, Remove, and Ownership Transfer

**Files:**
- Create member service/controller/DTOs.
- Extend tenant/member mapper methods.

- [ ] **Step 1: Write failing member tests**

Create `backend/src/test/java/com/antshorttv/member/TenantMemberControllerTest.java` with tests:

- owner lists active members.
- owner removes normal member and member cannot access tenant after removal.
- member can leave tenant.
- owner cannot directly leave tenant and receives `OWNER_LEAVE_BLOCKED`.
- owner transfer updates old owner to member and target member to owner in one transaction.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -Dtest=TenantMemberControllerTest test`

Expected: FAIL because member endpoints do not exist.

- [ ] **Step 3: Implement member service**

Endpoints:

- `GET /api/tenants/{id}/members`
- `DELETE /api/tenants/{id}/members/{memberId}`
- `POST /api/tenants/{id}/members/leave`
- `POST /api/tenants/{id}/transfer-owner`

Use `TenantContextResolver.requireOwner(tenantId)` for owner-only operations.
Use transactions for owner transfer.

- [ ] **Step 4: Run member tests**

Run: `mvn -Dtest=TenantMemberControllerTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add backend/src/main/java/com/antshorttv/member backend/src/test/java/com/antshorttv/member/TenantMemberControllerTest.java
git commit -m "feat: implement tenant member management"
```

## Task 6: Invitations

**Files:**
- Create invitation service/controller/DTOs.
- Extend member/user mapper methods.

- [ ] **Step 1: Write failing invitation tests**

Create `backend/src/test/java/com/antshorttv/invitation/TenantInvitationControllerTest.java` with tests:

- owner invites registered mobile and receives token.
- non-owner cannot invite.
- duplicate pending invite returns `DUPLICATE_PENDING_INVITATION`.
- existing active member returns `ALREADY_TENANT_MEMBER`.
- token detail works.
- accept invitation creates member and marks invitation accepted.
- reject marks invitation rejected.
- cancel marks pending invitation cancelled.
- expired invitation returns `INVITATION_EXPIRED`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -Dtest=TenantInvitationControllerTest test`

Expected: FAIL because invitation endpoints do not exist.

- [ ] **Step 3: Implement invitation service**

Endpoints:

- `POST /api/tenants/{id}/invitations`
- `GET /api/invitations`
- `GET /api/invitations/{token}`
- `POST /api/invitations/{token}/accept`
- `POST /api/invitations/{token}/reject`
- `POST /api/invitations/{id}/cancel`

Generate tokens with `SecureRandom`.
Default expiration is 7 days.
Accept invitation is `@Transactional`.

- [ ] **Step 4: Run invitation tests**

Run: `mvn -Dtest=TenantInvitationControllerTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add backend/src/main/java/com/antshorttv/invitation backend/src/test/java/com/antshorttv/invitation/TenantInvitationControllerTest.java
git commit -m "feat: implement tenant invitations"
```

## Task 7: Frontend Services, Request Auth, and Routes

**Files:**
- Modify app/request config.
- Create account-team services and routes.
- Add menu locales.

- [ ] **Step 1: Inspect Ant Design APIs before UI coding**

Run:

```bash
npx antd info Button
npx antd info Form
npx antd info Modal
npx antd info Table
```

Use ProComponents APIs already present in the project for ProForm, ModalForm, StepsForm, ProTable, and ProDescriptions.

- [ ] **Step 2: Write failing frontend service tests**

Create `frontend/src/services/account-team/auth.test.ts` with tests for token storage and `loginByMobile`.

Run: `npm run test -- src/services/account-team/auth.test.ts`

Expected: FAIL because service files do not exist.

- [ ] **Step 3: Implement services**

Create:

- `frontend/src/services/account-team/types.ts`
- `frontend/src/services/account-team/auth.ts`
- `frontend/src/services/account-team/tenant.ts`
- `frontend/src/services/account-team/member.ts`
- `frontend/src/services/account-team/invitation.ts`

Each service uses `request` from `@umijs/max` and maps to the backend endpoints.

- [ ] **Step 4: Add auth request handling**

Update `frontend/src/app.tsx` request config to add `Authorization` and `X-Tenant-Id` headers from local storage.
Keep existing error handling integration.

- [ ] **Step 5: Add routes and locales**

Add routes:

- `/team/my`
- `/team/select`
- `/team/settings`
- `/team/members`
- `/team/invitations`

Add menu labels in Chinese and English locale files.

- [ ] **Step 6: Run frontend service tests and type check**

Run:

```bash
npm run test -- src/services/account-team/auth.test.ts
npm run tsc
```

Expected: PASS.

- [ ] **Step 7: Commit**

Run:

```bash
git add frontend/src/services/account-team frontend/src/app.tsx frontend/config/routes.ts frontend/src/locales/zh-CN/menu.ts frontend/src/locales/en-US/menu.ts
git commit -m "feat: add account team frontend services"
```

## Task 8: Frontend Pages

**Files:**
- Modify login/register pages.
- Create team pages.

- [ ] **Step 1: Write failing page smoke tests**

Create focused Vitest/RTL smoke tests for:

- login submits mobile/password.
- my teams page renders create button and team list.
- members page renders invite action for owner.

Run: `npm run test -- src/pages/team`

Expected: FAIL because pages do not exist.

- [ ] **Step 2: Implement login and register**

Update:

- `frontend/src/pages/user/login/index.tsx`
- `frontend/src/pages/user/register/index.tsx`

Use Ant Design Pro login form conventions and ProForm fields. On login:

- save access token.
- save current tenant when backend recommends a single tenant.
- redirect to `/team/my`, `/team/select`, or original redirect according to backend response.

- [ ] **Step 3: Implement team pages**

Create:

- `frontend/src/pages/team/my/index.tsx`
- `frontend/src/pages/team/select/index.tsx`
- `frontend/src/pages/team/settings/index.tsx`
- `frontend/src/pages/team/members/index.tsx`
- `frontend/src/pages/team/invitations/index.tsx`

Use:

- `ProTable` for members and invitations.
- `ModalForm` for create tenant and invite member.
- `ProForm` for tenant settings.
- `App.useApp()` for messages and modal confirmations.

- [ ] **Step 4: Run frontend tests**

Run:

```bash
npm run test -- src/pages/team
npm run tsc
npx antd lint ./src
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add frontend/src/pages/user frontend/src/pages/team
git commit -m "feat: implement account team frontend pages"
```

## Task 9: Final Integration and Verification

**Files:**
- Update `README.md` if new MySQL environment variables or startup steps are needed.

- [ ] **Step 1: Run backend tests**

Run:

```bash
mvn test
```

Expected: all backend tests pass.

- [ ] **Step 2: Run frontend checks**

Run:

```bash
npm run test
npm run tsc
npm run biome:lint
npx antd lint ./src
```

Expected: all frontend checks pass.

- [ ] **Step 3: Run services locally**

Backend:

```bash
cd backend
mvn spring-boot:run
```

Frontend:

```bash
cd frontend
npm run dev
```

Expected:

- backend listens on `http://localhost:8080`.
- frontend listens on `http://localhost:8000`.
- `/api/auth/register`, `/api/auth/login`, `/api/tenants`, and team pages can complete the V1.0-01 core flow.

- [ ] **Step 4: Manual acceptance smoke**

Verify:

- register user A.
- login user A.
- create tenant A1.
- create tenant A2.
- switch between A1 and A2.
- register user B.
- invite B to A1.
- B accepts invitation.
- A removes B.
- B cannot access A1.
- A transfers owner to another active member.
- owner direct leave is blocked.

- [ ] **Step 5: Commit docs and final fixes**

Run:

```bash
git add README.md backend frontend
git commit -m "test: verify account team system"
```

Skip this commit if no files changed after verification.

## Self-Review Checklist

- Every V1.0-01 required API has a task.
- MyBatis-Plus is the only main persistence framework.
- MySQL is the production target; H2 is only for automated local tests.
- Tenant creation creates only tenant and tenant member.
- Owner transfer and invitation accept are transactional.
- Current tenant is validated server-side.
- Ant Design Pro page structure is used for frontend implementation.
