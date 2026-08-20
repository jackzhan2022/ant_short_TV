package com.antshorttv.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.member.MemberStatus;
import com.antshorttv.member.MemberType;
import com.antshorttv.member.TenantMemberEntity;
import com.antshorttv.member.TenantMemberMapper;
import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class RbacControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TenantMemberMapper tenantMemberMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Test
    void initializesDefaultRolesWhenListingTenantRoles() throws Exception {
        String ownerToken = registerUser("13800009001", "RBAC Owner");
        Long tenantId = createTenant(ownerToken, "RBAC初始化团队");

        mockMvc.perform(get("/api/tenants/%d/roles".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(3)))
            .andExpect(jsonPath("$.data[*].code", containsInAnyOrder("OWNER", "ADMIN", "MEMBER")))
            .andExpect(jsonPath("$.data[0].code", is("OWNER")))
            .andExpect(jsonPath("$.data[0].memberCount", is(1)));

        mockMvc.perform(get("/api/tenants/%d/roles".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(3)));
    }

    @Test
    void reactivatesDeletedSystemRoleDuringTenantInitialization() throws Exception {
        String ownerToken = registerUser("13800009015", "Deleted Role Owner");
        Long tenantId = createTenant(ownerToken, "恢复系统角色团队");
        RoleEntity deletedAdminRole = new RoleEntity();
        deletedAdminRole.setTenantId(tenantId);
        deletedAdminRole.setCode("ADMIN");
        deletedAdminRole.setName("Deleted Admin");
        deletedAdminRole.setDescription("曾被删除的系统角色");
        deletedAdminRole.setRoleType(RoleType.SYSTEM.name());
        deletedAdminRole.setStatus(RoleStatus.ACTIVE.name());
        deletedAdminRole.setIsDefault(true);
        deletedAdminRole.setCreatedAt(LocalDateTime.now());
        deletedAdminRole.setUpdatedAt(LocalDateTime.now());
        deletedAdminRole.setDeletedAt(LocalDateTime.now());
        roleMapper.insert(deletedAdminRole);

        mockMvc.perform(get("/api/tenants/%d/roles".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].code", containsInAnyOrder("OWNER", "ADMIN", "MEMBER")));

        RoleEntity activeAdminRole = roleMapper.selectActiveByTenantIdAndCode(tenantId, "ADMIN");
        assertThat(activeAdminRole).isNotNull();
        assertThat(activeAdminRole.getDeletedAt()).isNull();
        assertThat(activeAdminRole.getName()).isEqualTo("Admin");
    }

    @Test
    void createsRoleUpdatesPermissionsAndReturnsCurrentPermissionUnion() throws Exception {
        String ownerToken = registerUser("13800009002", "Role Owner");
        Long tenantId = createTenant(ownerToken, "权限并集团队");
        String memberToken = registerUser("13800009003", "Writer");
        Long memberId = addMember(tenantId, "13800009003");

        Long writerRoleId = createRole(ownerToken, tenantId, "SCRIPT_WRITER", "短剧编剧", List.of("SCRIPT:VIEW", "SCRIPT:CREATE"));
        Long projectRoleId = createRole(ownerToken, tenantId, "PROJECT_MANAGER", "项目管理员", List.of("PROJECT:VIEW", "PROJECT:EDIT"));

        mockMvc.perform(put("/api/tenants/%d/members/%d/roles".formatted(tenantId, memberId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"roleIds":[%d,%d]}
                    """.formatted(writerRoleId, projectRoleId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].code", containsInAnyOrder("SCRIPT_WRITER", "PROJECT_MANAGER")));

        mockMvc.perform(get("/api/auth/permissions")
                .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.permissions", hasItem("SCRIPT:VIEW")))
            .andExpect(jsonPath("$.data.permissions", hasItem("SCRIPT:CREATE")))
            .andExpect(jsonPath("$.data.permissions", hasItem("PROJECT:VIEW")))
            .andExpect(jsonPath("$.data.permissions", hasItem("PROJECT:EDIT")));
    }

    @Test
    void disablesRoleAndRemovesItsPermissionsImmediately() throws Exception {
        String ownerToken = registerUser("13800009004", "Disable Owner");
        Long tenantId = createTenant(ownerToken, "角色停用团队");
        String memberToken = registerUser("13800009005", "Editor");
        Long memberId = addMember(tenantId, "13800009005");
        Long roleId = createRole(ownerToken, tenantId, "SCRIPT_EDITOR", "剧本编辑", List.of("SCRIPT:EDIT"));

        mockMvc.perform(put("/api/tenants/%d/members/%d/roles".formatted(tenantId, memberId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleIds\":[%d]}".formatted(roleId)))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/tenants/%d/roles/%d/status".formatted(tenantId, roleId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DISABLED\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/permissions")
                .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.permissions", hasSize(0)));

        mockMvc.perform(put("/api/tenants/%d/members/%d/roles".formatted(tenantId, memberId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleIds\":[%d]}".formatted(roleId)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode", is("ROLE_DISABLED")));
    }

    @Test
    void blocksSystemRoleDeletionAndAssignedCustomRoleDeletion() throws Exception {
        String ownerToken = registerUser("13800009006", "Delete Owner");
        Long tenantId = createTenant(ownerToken, "角色删除团队");
        Long ownerRoleId = findRoleId(ownerToken, tenantId, "OWNER");

        mockMvc.perform(delete("/api/tenants/%d/roles/%d".formatted(tenantId, ownerRoleId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("OWNER_ROLE_DELETE_BLOCKED")));

        String memberToken = registerUser("13800009007", "Assigned Member");
        Long memberId = addMember(tenantId, "13800009007");
        Long roleId = createRole(ownerToken, tenantId, "ASSIGNED_ROLE", "已分配角色", List.of("PROJECT:VIEW"));

        mockMvc.perform(put("/api/tenants/%d/members/%d/roles".formatted(tenantId, memberId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleIds\":[%d]}".formatted(roleId)))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/tenants/%d/roles/%d".formatted(tenantId, roleId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode", is("ROLE_IN_USE")));

        assertThat(memberToken).isNotBlank();
    }

    @Test
    void blocksCrossTenantRoleAndMemberAccess() throws Exception {
        String firstOwnerToken = registerUser("13800009008", "First Owner");
        Long firstTenantId = createTenant(firstOwnerToken, "第一租户");
        Long firstRoleId = createRole(firstOwnerToken, firstTenantId, "FIRST_ONLY", "第一租户角色", List.of("PROJECT:VIEW"));

        String secondOwnerToken = registerUser("13800009009", "Second Owner");
        Long secondTenantId = createTenant(secondOwnerToken, "第二租户");
        String secondMemberToken = registerUser("13800009010", "Second Member");
        Long secondMemberId = addMember(secondTenantId, "13800009010");

        mockMvc.perform(get("/api/tenants/%d/roles/%d".formatted(firstTenantId, firstRoleId))
                .header(HttpHeaders.AUTHORIZATION, bearer(secondOwnerToken)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));

        mockMvc.perform(put("/api/tenants/%d/members/%d/roles".formatted(firstTenantId, secondMemberId))
                .header(HttpHeaders.AUTHORIZATION, bearer(firstOwnerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleIds\":[%d]}".formatted(firstRoleId)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode", is("NOT_FOUND")));

        mockMvc.perform(get("/api/auth/permissions")
                .header(HttpHeaders.AUTHORIZATION, bearer(secondMemberToken))
                .header("X-Tenant-Id", firstTenantId))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
    }

    @Test
    void permissionCheckUsesPathTenantBeforeTenantHeaderForTenantScopedApis() throws Exception {
        String firstOwnerToken = registerUser("13800009013", "Path Tenant Owner");
        Long firstTenantId = createTenant(firstOwnerToken, "路径租户");
        String actorToken = registerUser("13800009014", "Header Tenant Admin");
        addMember(firstTenantId, "13800009014");
        Long secondTenantId = createTenant(actorToken, "请求头租户");

        mockMvc.perform(post("/api/tenants/%d/roles".formatted(firstTenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(actorToken))
                .header("X-Tenant-Id", secondTenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code":"SHOULD_NOT_CREATE","name":"不应创建","description":"越权测试","permissionCodes":["PROJECT:VIEW"]}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
    }

    @Test
    void initializesRbacIdempotentlyUnderConcurrentRequests() throws Exception {
        String ownerToken = registerUser("13800009011", "Concurrent Owner");
        Long tenantId = createTenant(ownerToken, "并发初始化团队");
        registerUser("13800009012", "Concurrent Member");
        Long memberId = addMember(tenantId, "13800009012");
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8);
        List<java.util.concurrent.Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            futures.add(executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(get("/api/tenants/%d/roles".formatted(tenantId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            }));
            futures.add(executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(get("/api/tenants/%d/members/%d/roles".formatted(tenantId, memberId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            }));
        }

        start.countDown();
        for (java.util.concurrent.Future<Integer> future : futures) {
            assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        }
        executor.shutdownNow();
    }

    private Long createRole(String token, Long tenantId, String code, String name, List<String> permissionCodes) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tenants/%d/roles".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code":"%s","name":"%s","description":"测试角色","permissionCodes":%s}
                    """.formatted(code, name, toJsonArray(permissionCodes))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code", is(code)))
            .andReturn();
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return value.longValue();
    }

    private Long findRoleId(String token, Long tenantId, String code) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/tenants/%d/roles".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andReturn();
        List<Number> values = JsonPath.read(result.getResponse().getContentAsString(), "$.data[?(@.code=='%s')].id".formatted(code));
        return values.get(0).longValue();
    }

    private String registerUser(String mobile, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"%s","verificationCode":"123456","nickname":"%s","password":"Password123"}
                    """.formatted(mobile, nickname)))
            .andExpect(status().isOk())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private Long createTenant(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tenants")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","type":"STUDIO","description":"RBAC测试"}
                    """.formatted(name)))
            .andExpect(status().isOk())
            .andReturn();
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return value.longValue();
    }

    private Long addMember(Long tenantId, String mobile) {
        UserEntity user = userMapper.selectByMobile(mobile);
        TenantMemberEntity member = new TenantMemberEntity();
        member.setTenantId(tenantId);
        member.setUserId(user.getId());
        member.setMemberType(MemberType.MEMBER.name());
        member.setStatus(MemberStatus.ACTIVE.name());
        member.setJoinedAt(LocalDateTime.now());
        member.setCreatedAt(LocalDateTime.now());
        member.setUpdatedAt(LocalDateTime.now());
        tenantMemberMapper.insert(member);
        return member.getId();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String toJsonArray(List<String> values) {
        return values.stream()
            .map(value -> "\"" + value + "\"")
            .toList()
            .toString();
    }
}
