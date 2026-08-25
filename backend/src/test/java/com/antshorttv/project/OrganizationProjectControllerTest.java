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
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TenantMemberMapper tenantMemberMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsProjectWithDefaultRolesOwnerMemberAndAllowsProjectMemberAccessOnly() throws Exception {
        String ownerToken = registerUser("13800011002", "Project Owner");
        Long tenantId = createTenant(ownerToken, "项目访问团队");
        String memberToken = registerUser("13800011003", "Project Writer");
        Long memberUserId = userIdByMobile("13800011003");
        addTenantMember(tenantId, memberUserId);
        Long projectId = createProject(ownerToken, tenantId, memberUserId, "重生后我成了首富", "REBIRTH_CEO");

        mockMvc.perform(get("/api/projects/%d/roles".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(ownerToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].code", containsInAnyOrder("PROJECT_OWNER", "WRITER", "DIRECTOR", "PRODUCER", "MEMBER")));

        mockMvc.perform(get("/api/projects/%d/members".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(ownerToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].userId", is(memberUserId.intValue())));

        mockMvc.perform(get("/api/projects/%d".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(memberToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name", is("重生后我成了首富")));

        insertDefaultTextService(tenantId, userIdByMobile("13800011002"));
        MvcResult roleResult = mockMvc.perform(get("/api/projects/%d/roles".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(ownerToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andReturn();
        List<Integer> projectOwnerRoleIds = JsonPath.read(
            roleResult.getResponse().getContentAsString(),
            "$.data[?(@.code=='PROJECT_OWNER')].id"
        );
        Long projectOwnerRoleId = projectOwnerRoleIds.get(0).longValue();
        mockMvc.perform(get("/api/projects/%d/roles/%d/permissions".formatted(projectId, projectOwnerRoleId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(ownerToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].code", hasItems(
                "SCRIPT:VIEW",
                "SCRIPT:AI_GENERATE",
                "SCRIPT:AI_REWRITE",
                "AI_SERVICE:USE"
            )));
        grantTeamPoints(tenantId, 1);
        mockMvc.perform(post("/api/projects/%d/scripts/ai-generate".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(memberToken))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"storyIdea":"落魄千金雨夜回归豪门","genre":"逆袭","episodeCount":3,"duration":60}
                    """))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.status", is("PENDING")));

        mockMvc.perform(delete("/api/projects/%d/members/%d".formatted(projectId, memberUserId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(ownerToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/%d".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(memberToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("PROJECT_ACCESS_DENIED")));
    }

    @Test
    void createsProjectWithShortDramaInitializationMetadataAndKeepsLegacyCreateCompatible() throws Exception {
        String ownerToken = registerUser("13800011016", "Short Drama Owner");
        Long tenantId = createTenant(ownerToken, "短剧创作团队");
        Long ownerUserId = userIdByMobile("13800011016");

        MvcResult richResult = mockMvc.perform(post("/api/projects")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(ownerToken))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":"独立菜单短剧",
                      "code":"SHORT_DRAMA_CREATION",
                      "description":"从短剧创作入口创建",
                      "ownerId":%d,
                      "aspectRatio":"16:9",
                      "fileFormat":"SCRIPT",
                      "scriptType":"PREMIUM_DRAMA",
                      "breakdownStrength":"MEDIUM",
                      "coverSource":"FIRST_FRAME",
                      "visualStyle":"3D风格-高清真实渲染",
                      "initialScriptContent":"第一场，雨夜重逢。"
                    }
                    """.formatted(ownerUserId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.aspectRatio", is("16:9")))
            .andExpect(jsonPath("$.data.fileFormat", is("SCRIPT")))
            .andExpect(jsonPath("$.data.scriptType", is("PREMIUM_DRAMA")))
            .andExpect(jsonPath("$.data.breakdownStrength", is("MEDIUM")))
            .andExpect(jsonPath("$.data.coverSource", is("FIRST_FRAME")))
            .andExpect(jsonPath("$.data.visualStyle", is("3D风格-高清真实渲染")))
            .andExpect(jsonPath("$.data.initialScriptContent", is("第一场，雨夜重逢。")))
            .andReturn();

        Number createdId = JsonPath.read(richResult.getResponse().getContentAsString(), "$.data.id");
        mockMvc.perform(get("/api/projects/%d/script-workspace".formatted(createdId.longValue()))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(ownerToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.script.title", is("独立菜单短剧")))
            .andExpect(jsonPath("$.data.script.content", is("第一场，雨夜重逢。")))
            .andExpect(jsonPath("$.data.script.sourceType", is("MANUAL_EDIT")))
            .andExpect(jsonPath("$.data.versions", hasSize(1)))
            .andExpect(jsonPath("$.data.versions[0].content", is("第一场，雨夜重逢。")));

        mockMvc.perform(get("/api/projects/%d".formatted(createdId.longValue()))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(ownerToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.visualStyle", is("3D风格-高清真实渲染")));

        mockMvc.perform(post("/api/projects")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(ownerToken))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"旧入口项目","code":"LEGACY_SHORT_DRAMA","description":"旧请求","ownerId":%d}
                    """.formatted(ownerUserId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name", is("旧入口项目")));
    }

    @Test
    void rejectsProjectListWithoutTenantHeader() throws Exception {
        String ownerToken = registerUser("13800011017", "Tenant Header Owner");

        mockMvc.perform(get("/api/projects")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(ownerToken)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));
    }

    @Test
    void blocksCrossTenantAndCrossProjectAccess() throws Exception {
        String firstOwnerToken = registerUser("13800011004", "First Project Owner");
        Long firstTenantId = createTenant(firstOwnerToken, "第一项目租户");
        String firstMemberToken = registerUser("13800011005", "First Project Member");
        Long firstMemberUserId = userIdByMobile("13800011005");
        addTenantMember(firstTenantId, firstMemberUserId);
        Long firstProjectId = createProject(firstOwnerToken, firstTenantId, firstMemberUserId, "第一项目", "FIRST_PROJECT");

        String secondOwnerToken = registerUser("13800011006", "Second Project Owner");
        Long secondTenantId = createTenant(secondOwnerToken, "第二项目租户");
        Long secondOwnerUserId = userIdByMobile("13800011006");
        Long secondProjectId = createProject(secondOwnerToken, secondTenantId, secondOwnerUserId, "第二项目", "SECOND_PROJECT");

        mockMvc.perform(get("/api/projects/%d".formatted(firstProjectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(secondOwnerToken))
                .header("X-Tenant-Id", firstTenantId))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));

        mockMvc.perform(get("/api/projects/%d".formatted(secondProjectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(firstMemberToken))
                .header("X-Tenant-Id", firstTenantId))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("PROJECT_ACCESS_DENIED")));
    }

    @Test
    void archivedProjectRejectsMemberChanges() throws Exception {
        String ownerToken = registerUser("13800011007", "Archive Owner");
        Long tenantId = createTenant(ownerToken, "归档项目团队");
        Long ownerUserId = userIdByMobile("13800011007");
        Long projectId = createProject(ownerToken, tenantId, ownerUserId, "归档项目", "ARCHIVE_PROJECT");
        registerUser("13800011008", "Late Member");
        Long lateUserId = userIdByMobile("13800011008");
        addTenantMember(tenantId, lateUserId);

        mockMvc.perform(put("/api/projects/%d/status".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(ownerToken))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ARCHIVED\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/%d/members".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(ownerToken))
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
        Long projectId = createProject(tenantOwnerToken, tenantId, oldOwnerUserId, "负责人转移项目", "TRANSFER_OWNER_PROJECT");

        mockMvc.perform(put("/api/projects/%d/owner".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(tenantOwnerToken))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerId\":%d}".formatted(newOwnerUserId)))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/%d/members".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(oldOwnerToken))
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
        Long projectId = createProject(tenantOwnerToken, tenantId, projectOwnerUserId, "归档权限项目", "ARCHIVE_ROLE_PROJECT");

        MvcResult roleResult = mockMvc.perform(post("/api/projects/%d/roles".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(projectOwnerToken))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code":"CUSTOM_WRITER","name":"自定义编剧","permissionCodes":["PROJECT:VIEW"]}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        Number roleValue = JsonPath.read(roleResult.getResponse().getContentAsString(), "$.data.id");
        Long customRoleId = roleValue.longValue();

        mockMvc.perform(post("/api/projects/%d/members".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(projectOwnerToken))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":%d,\"roleId\":%d}".formatted(assignedUserId, customRoleId)))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/projects/%d/roles/%d".formatted(projectId, customRoleId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(projectOwnerToken))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode", is("ROLE_IN_USE")));

        mockMvc.perform(put("/api/projects/%d/status".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(tenantOwnerToken))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ARCHIVED\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/projects/%d/status".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(projectOwnerToken))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode", is("PROJECT_ARCHIVED")));
    }

    private Long createProject(String token, Long tenantId, Long ownerId, String name, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","code":"%s","description":"项目测试","ownerId":%d}
                    """.formatted(name, code, ownerId)))
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
        return com.antshorttv.support.SessionTestSupport.sessionCredential(result);
    }

    private Long createTenant(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tenants")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
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

    private void insertDefaultTextService(Long tenantId, Long userId) {
        jdbcTemplate.update("""
            update ai_service_config
               set is_default = false,
                   updated_at = now()
             where service_type = 'TEXT'
               and is_default = true
               and deleted_at is null
            """);
        jdbcTemplate.update("""
            insert into ai_service_config
              (tenant_id, provider, service_type, name, base_url, api_key_cipher, model, endpoint, priority, is_default, enabled, last_test_status, created_by, created_at, updated_at)
            values (?, 'OpenAI', 'TEXT', '默认文本服务', 'https://example.com/v1', 'cipher', 'gpt-4.1-mini', '/chat/completions', 100, true, true, 'SUCCESS', ?, now(), now())
            """, tenantId, userId);
        Long configId = jdbcTemplate.queryForObject("select id from ai_service_config where tenant_id = ? and service_type = 'TEXT' and deleted_at is null order by id desc limit 1", Long.class, tenantId);
        Long providerId = jdbcTemplate.queryForObject("select id from ai_provider where code = 'OpenAI' limit 1", Long.class);
        String modelCode = "test-project-text-" + tenantId;
        jdbcTemplate.update("update ai_model set is_default = false where service_type = 'TEXT'");
        jdbcTemplate.update("delete from ai_model_capability where model_id in (select id from ai_model where code = ?)", modelCode);
        jdbcTemplate.update("delete from ai_model where code = ?", modelCode);
        jdbcTemplate.update("insert into ai_model (provider_id, code, name, model_code, service_type, status, is_default, sort, legacy_service_config_id, created_at, updated_at) values (?, ?, 'Test Project Text', 'gpt-4.1-mini', 'TEXT', 'ENABLED', true, 100, ?, now(), now())", providerId, modelCode, configId);
        Long modelId = jdbcTemplate.queryForObject("select id from ai_model where code = ?", Long.class, modelCode);
        jdbcTemplate.update("insert into ai_model_capability (model_id, capability, status, created_at, updated_at) values (?, 'TEXT_GENERATION', 'ENABLED', now(), now())", modelId);
    }

    private void grantTeamPoints(Long tenantId, int amount) {
        jdbcTemplate.update("""
            insert into team_point_account
              (tenant_id, balance, total_granted, total_consumed, created_at, updated_at)
            values (?, ?, ?, 0, now(), now())
            """, tenantId, amount, amount);
    }

    private Long userIdByMobile(String mobile) {
        UserEntity user = userMapper.selectByMobile(mobile);
        return user.getId();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
