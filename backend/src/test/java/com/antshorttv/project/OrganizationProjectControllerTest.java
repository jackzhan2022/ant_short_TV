package com.antshorttv.project;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItems;
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
import com.jayway.jsonpath.JsonPath;
import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class OrganizationProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TenantMemberMapper tenantMemberMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void managesOrganizationTreeAndEnforcesDeleteRulesAndLevelLimit() throws Exception {
        String ownerToken = registerUser("13800011001", "Org Owner");
        Long tenantId = createTenant(ownerToken, "组织项目团队");
        Long rootId = createOrganization(ownerToken, tenantId, null, "制作中心", "PRODUCTION");
        Long childId = createOrganization(ownerToken, tenantId, rootId, "编剧组", "WRITER_GROUP");

        mockMvc.perform(get("/api/organizations")
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].children[0].id", is(childId.intValue())));

        mockMvc.perform(delete("/api/organizations/%d".formatted(rootId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode", is("ORGANIZATION_HAS_CHILDREN")));

        Long level3 = createOrganization(ownerToken, tenantId, childId, "三级", "L3");
        Long level4 = createOrganization(ownerToken, tenantId, level3, "四级", "L4");
        Long level5 = createOrganization(ownerToken, tenantId, level4, "五级", "L5");
        mockMvc.perform(post("/api/organizations")
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"parentId":%d,"name":"六级","code":"L6","sort":1}
                    """.formatted(level5)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode", is("ORGANIZATION_LEVEL_EXCEEDED")));
    }

    @Test
    void createsProjectWithDefaultRolesOwnerMemberAndAllowsProjectMemberAccessOnly() throws Exception {
        String ownerToken = registerUser("13800011002", "Project Owner");
        Long tenantId = createTenant(ownerToken, "项目访问团队");
        String memberToken = registerUser("13800011003", "Project Writer");
        Long memberUserId = userIdByMobile("13800011003");
        addTenantMember(tenantId, memberUserId);
        Long organizationId = createOrganization(ownerToken, tenantId, null, "项目组", "PROJECT_GROUP");

        Long projectId = createProject(ownerToken, tenantId, organizationId, memberUserId, "重生后我成了首富", "REBIRTH_CEO");

        mockMvc.perform(get("/api/projects/%d/roles".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].code", containsInAnyOrder("PROJECT_OWNER", "WRITER", "DIRECTOR", "PRODUCER", "MEMBER")));

        mockMvc.perform(get("/api/projects/%d/members".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].userId", is(memberUserId.intValue())));

        mockMvc.perform(get("/api/projects/%d".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name", is("重生后我成了首富")));

        MvcResult roleResult = mockMvc.perform(get("/api/projects/%d/roles".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andReturn();
        List<Integer> projectOwnerRoleIds = JsonPath.read(
            roleResult.getResponse().getContentAsString(),
            "$.data[?(@.code=='PROJECT_OWNER')].id"
        );
        Long projectOwnerRoleId = projectOwnerRoleIds.get(0).longValue();
        mockMvc.perform(get("/api/projects/%d/roles/%d/permissions".formatted(projectId, projectOwnerRoleId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].code", hasItems(
                "SCRIPT:VIEW",
                "SCRIPT:AI_GENERATE",
                "SCRIPT:AI_REWRITE",
                "AI_SERVICE:USE"
            )));
        HttpServer server = startTextServer("落魄千金雨夜回归豪门，模型生成正文。");
        try {
            createTextServiceConfig(ownerToken, tenantId, "http://127.0.0.1:%d".formatted(server.getAddress().getPort()), "/chat/completions");

            mockMvc.perform(post("/api/projects/%d/scripts/ai-generate".formatted(projectId))
                    .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                    .header("X-Tenant-Id", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"storyIdea":"落魄千金雨夜回归豪门","genre":"逆袭","episodeCount":3,"duration":60}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.script.content", containsString("落魄千金雨夜回归豪门")));
        } finally {
            server.stop(0);
        }

        mockMvc.perform(delete("/api/projects/%d/members/%d".formatted(projectId, memberUserId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/%d".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("PROJECT_ACCESS_DENIED")));
    }

    @Test
    void blocksCrossTenantAndCrossProjectAccess() throws Exception {
        String firstOwnerToken = registerUser("13800011004", "First Project Owner");
        Long firstTenantId = createTenant(firstOwnerToken, "第一项目租户");
        String firstMemberToken = registerUser("13800011005", "First Project Member");
        Long firstMemberUserId = userIdByMobile("13800011005");
        addTenantMember(firstTenantId, firstMemberUserId);
        Long firstProjectId = createProject(firstOwnerToken, firstTenantId, null, firstMemberUserId, "第一项目", "FIRST_PROJECT");

        String secondOwnerToken = registerUser("13800011006", "Second Project Owner");
        Long secondTenantId = createTenant(secondOwnerToken, "第二项目租户");
        Long secondOwnerUserId = userIdByMobile("13800011006");
        Long secondProjectId = createProject(secondOwnerToken, secondTenantId, null, secondOwnerUserId, "第二项目", "SECOND_PROJECT");

        mockMvc.perform(get("/api/projects/%d".formatted(firstProjectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(secondOwnerToken))
                .header("X-Tenant-Id", firstTenantId))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));

        mockMvc.perform(get("/api/projects/%d".formatted(secondProjectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(firstMemberToken))
                .header("X-Tenant-Id", firstTenantId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode", is("PROJECT_NOT_FOUND")));
    }

    @Test
    void archivedProjectRejectsMemberChanges() throws Exception {
        String ownerToken = registerUser("13800011007", "Archive Owner");
        Long tenantId = createTenant(ownerToken, "归档项目团队");
        Long ownerUserId = userIdByMobile("13800011007");
        Long projectId = createProject(ownerToken, tenantId, null, ownerUserId, "归档项目", "ARCHIVE_PROJECT");
        registerUser("13800011008", "Late Member");
        Long lateUserId = userIdByMobile("13800011008");
        addTenantMember(tenantId, lateUserId);

        mockMvc.perform(put("/api/projects/%d/status".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ARCHIVED\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/%d/members".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"userId":%d}
                    """.formatted(lateUserId)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode", is("PROJECT_ARCHIVED")));
    }

    @Test
    void transferredProjectOwnerLosesProjectOwnerRoleAndCannotManageMembers() throws Exception {
        String tenantOwnerToken = registerUser("13800011009", "Transfer Tenant Owner");
        Long tenantId = createTenant(tenantOwnerToken, "负责人转移团队");
        String oldOwnerToken = registerUser("13800011010", "Old Project Owner");
        Long oldOwnerUserId = userIdByMobile("13800011010");
        addTenantMember(tenantId, oldOwnerUserId);
        registerUser("13800011011", "New Project Owner");
        Long newOwnerUserId = userIdByMobile("13800011011");
        addTenantMember(tenantId, newOwnerUserId);
        registerUser("13800011012", "Transfer Late Member");
        Long lateUserId = userIdByMobile("13800011012");
        addTenantMember(tenantId, lateUserId);
        Long projectId = createProject(tenantOwnerToken, tenantId, null, oldOwnerUserId, "负责人转移项目", "TRANSFER_OWNER_PROJECT");

        mockMvc.perform(put("/api/projects/%d/owner".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(tenantOwnerToken))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerId\":%d}".formatted(newOwnerUserId)))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/%d/members".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(oldOwnerToken))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":%d}".formatted(lateUserId)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
    }

    @Test
    void projectRoleCannotUnarchiveProjectOrDeleteRoleStillAssignedToMember() throws Exception {
        String tenantOwnerToken = registerUser("13800011013", "Archive Tenant Owner");
        Long tenantId = createTenant(tenantOwnerToken, "归档与角色团队");
        String projectOwnerToken = registerUser("13800011014", "Archive Project Owner");
        Long projectOwnerUserId = userIdByMobile("13800011014");
        addTenantMember(tenantId, projectOwnerUserId);
        registerUser("13800011015", "Role Assigned Member");
        Long assignedUserId = userIdByMobile("13800011015");
        addTenantMember(tenantId, assignedUserId);
        Long projectId = createProject(tenantOwnerToken, tenantId, null, projectOwnerUserId, "归档权限项目", "ARCHIVE_ROLE_PROJECT");

        MvcResult roleResult = mockMvc.perform(post("/api/projects/%d/roles".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(projectOwnerToken))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code":"CUSTOM_WRITER","name":"自定义编剧","dataScope":"PROJECT","permissionCodes":["PROJECT:VIEW"]}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        Number roleValue = JsonPath.read(roleResult.getResponse().getContentAsString(), "$.data.id");
        Long customRoleId = roleValue.longValue();

        mockMvc.perform(post("/api/projects/%d/members".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(projectOwnerToken))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":%d,\"roleId\":%d}".formatted(assignedUserId, customRoleId)))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/projects/%d/roles/%d".formatted(projectId, customRoleId))
                .header(HttpHeaders.AUTHORIZATION, bearer(projectOwnerToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode", is("ROLE_IN_USE")));

        mockMvc.perform(put("/api/projects/%d/status".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(tenantOwnerToken))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ARCHIVED\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/projects/%d/status".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(projectOwnerToken))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode", is("PROJECT_ARCHIVED")));
    }

    private Long createOrganization(String token, Long tenantId, Long parentId, String name, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/organizations")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"parentId":%s,"name":"%s","code":"%s","sort":1}
                    """.formatted(parentId == null ? "null" : parentId, name, code)))
            .andExpect(status().isOk())
            .andReturn();
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return value.longValue();
    }

    private Long createProject(String token, Long tenantId, Long organizationId, Long ownerId, String name, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"organizationId":%s,"name":"%s","code":"%s","description":"项目测试","ownerId":%d}
                    """.formatted(organizationId == null ? "null" : organizationId, name, code, ownerId)))
            .andExpect(status().isOk())
            .andReturn();
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return value.longValue();
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
                    {"name":"%s","type":"STUDIO","description":"V1.0-03测试"}
                    """.formatted(name)))
            .andExpect(status().isOk())
            .andReturn();
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return value.longValue();
    }

    private void addTenantMember(Long tenantId, Long userId) {
        TenantMemberEntity member = new TenantMemberEntity();
        member.setTenantId(tenantId);
        member.setUserId(userId);
        member.setMemberType(MemberType.MEMBER.name());
        member.setStatus(MemberStatus.ACTIVE.name());
        member.setJoinedAt(LocalDateTime.now());
        member.setCreatedAt(LocalDateTime.now());
        member.setUpdatedAt(LocalDateTime.now());
        tenantMemberMapper.insert(member);
    }

    private void createTextServiceConfig(String token, Long tenantId, String baseUrl, String endpoint) throws Exception {
        mockMvc.perform(post("/api/tenants/%d/ai-service-configs".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":"项目权限测试文本服务",
                      "serviceType":"TEXT",
                      "provider":"OpenAI",
                      "baseUrl":"%s",
                      "apiKey":"sk-test-1234",
                      "model":"gpt-test",
                      "endpoint":"%s",
                      "priority":100,
                      "isDefault":true,
                      "enabled":true
                    }
                    """.formatted(baseUrl, endpoint)))
            .andExpect(status().isOk());
    }

    private HttpServer startTextServer(String content) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] body = openAiResponse(content).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private String openAiResponse(String content) {
        String escaped = content
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
        return """
            {"choices":[{"message":{"content":"%s"}}]}
            """.formatted(escaped);
    }

    private Long userIdByMobile(String mobile) {
        UserEntity user = userMapper.selectByMobile(mobile);
        return user.getId();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
