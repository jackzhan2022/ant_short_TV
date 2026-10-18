package com.antshorttv.workflowagent.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.antshorttv.support.RegistrationTestSupport;
import com.antshorttv.support.SessionTestSupport;
import com.antshorttv.workflowagent.agent.StoryboardAgentBootstrap;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "ai.workflow-agent.skill-root=target/tenant-workflow-skill-test")
@AutoConfigureMockMvc
class TenantWorkflowSkillControllerTest extends RegistrationTestSupport {
    private static final String PLANNING = "short-drama-storyboard-production";
    private static final AtomicLong MOBILE = new AtomicLong(13800004000L);
    @Autowired MockMvc mvc;
    @Autowired FileSkillRepository files;
    @Autowired WorkflowSkillService skills;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.antshorttv.rbac.RbacService rbac;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void seedSystemSkills() throws Exception {
        for (String code : StoryboardAgentBootstrap.SKILLS) {
            if (!files.exists(code)) files.create(code, Files.readString(Path.of("skills", code, "SKILL.md")));
        }
    }

    @Test
    void tenantCatalogRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/tenants/1/workflow-skills")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tenants/1/workflow-skills/" + PLANNING + "/template"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void managerCreatesIndependentTenantCopyFromLatestSystemTemplate() throws Exception {
        Actor owner = actor(null);
        WorkflowSkillView original = skills.detail(PLANNING);
        String latest = original.content() + "\n系统新发布的完整规则与素材占位说明\n";
        try {
            files.update(PLANNING, latest, original.revision());
            mvc.perform(get(path(owner) + "/" + PLANNING + "/template")
                    .with(SessionTestSupport.authenticated(owner.credential())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", is(latest)))
                .andExpect(jsonPath("$.data.name", is(original.name())))
                .andExpect(jsonPath("$.data.description", is(original.description())));
            mvc.perform(get(path(owner)).with(SessionTestSupport.authenticated(owner.credential())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()", is(0)));
            mvc.perform(get(path(owner) + "/" + PLANNING).with(SessionTestSupport.authenticated(owner.credential())))
                .andExpect(status().isNotFound());
            String edited = latest + "\n租户在系统规则基础上的修改\n";
            mvc.perform(put(path(owner) + "/" + PLANNING).with(SessionTestSupport.authenticated(owner.credential()))
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "content", edited, "expectedRevision", TenantWorkflowSkillService.NEW_REVISION))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", is(edited)))
                .andExpect(jsonPath("$.data.scope", is("TENANT")))
                .andExpect(jsonPath("$.data.priority", is(200)));
            assertThat(skills.detail(PLANNING).content()).isEqualTo(latest);
        } finally {
            files.update(PLANNING, original.content(), skills.detail(PLANNING).revision());
        }
    }

    @Test
    void ownerCustomizesUnifiedSkillWithTenantIsolationAndAudit() throws Exception {
        Actor a = actor(null), b = actor(null);
        String system = skills.detail(PLANNING).content();
        String first = save(a, PLANNING, "团队甲强调动作节奏", TenantWorkflowSkillService.NEW_REVISION);
        assertThat(skills.findTenantOverride(StoryboardAgentBootstrap.AGENT_CODE, a.tenantId(), PLANNING).content())
            .contains("团队甲强调动作节奏");
        assertThat(skills.findTenantOverride(StoryboardAgentBootstrap.AGENT_CODE, b.tenantId(), PLANNING)).isNull();
        assertThat(skills.findTenantOverride("short-drama-episode-summary", a.tenantId(), PLANNING)).isNull();
        assertThat(skills.findTenantOverride(StoryboardAgentBootstrap.AGENT_CODE, a.tenantId(), "short-drama-analysis-foundation")).isNull();
        assertThat(skills.detail(PLANNING).content()).isEqualTo(system);
        assertThat(jdbc.queryForObject("select updated_by from tenant_workflow_skill where tenant_id=? and skill_code=?",
            Long.class, a.tenantId(), PLANNING)).isEqualTo(a.userId());
        mvc.perform(get(path(a)).with(SessionTestSupport.authenticated(a.credential()))
                .param("agentCode", StoryboardAgentBootstrap.AGENT_CODE).param("scope", "TENANT"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()", is(1)))
            .andExpect(jsonPath("$.data[0].tenantId", is(a.tenantId().intValue())))
            .andExpect(jsonPath("$.data[0].tenantName", is("团队" + a.userId())));
        mvc.perform(get(path(b) + "/" + PLANNING).with(SessionTestSupport.authenticated(b.credential())))
            .andExpect(status().isNotFound());
        mvc.perform(get(path(a)).with(SessionTestSupport.authenticated(a.credential())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()", is(1)))
            .andExpect(jsonPath("$.data[0].scope", is("TENANT")))
            .andExpect(jsonPath("$.data[0].priority", is(200)));
        mvc.perform(get(path(a)).param("scope", "SYSTEM").with(SessionTestSupport.authenticated(a.credential())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()", is(0)));
        mvc.perform(get(path(a) + "/short-drama-analysis-foundation").with(SessionTestSupport.authenticated(a.credential())))
            .andExpect(status().isForbidden());
        mvc.perform(delete(path(a) + "/" + PLANNING).with(SessionTestSupport.authenticated(a.credential()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedRevision", first))))
            .andExpect(status().isOk());
        assertThat(skills.findTenantOverride(StoryboardAgentBootstrap.AGENT_CODE, a.tenantId(), PLANNING)).isNull();
    }

    @Test
    void ordinaryAndForeignMembersCannotReadOrMutateButActiveAdminCan() throws Exception {
        Actor owner = actor(null), member = actor(null);
        mvc.perform(get(path(owner)).with(SessionTestSupport.authenticated(member.credential())))
            .andExpect(status().isForbidden());
        mvc.perform(get(path(owner) + "/" + PLANNING + "/template")
                .with(SessionTestSupport.authenticated(member.credential())))
            .andExpect(status().isForbidden());
        jdbc.update("""
            insert into tenant_member (tenant_id,user_id,member_type,status,joined_at,created_at,updated_at)
            values (?,?,'MEMBER','ACTIVE',now(),now(),now())
            """, owner.tenantId(), member.userId());
        Long memberId = jdbc.queryForObject("select id from tenant_member where tenant_id=? and user_id=?",
            Long.class, owner.tenantId(), member.userId());
        Actor inTeam = new Actor(member.credential(), member.userId(), owner.tenantId());
        mvc.perform(get(path(inTeam) + "/" + PLANNING + "/template")
                .with(SessionTestSupport.authenticated(member.credential())))
            .andExpect(status().isForbidden());
        mvc.perform(get(path(inTeam)).with(SessionTestSupport.authenticated(member.credential())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()", is(0)));
        mvc.perform(put(path(inTeam) + "/" + PLANNING).with(SessionTestSupport.authenticated(member.credential()))
                .contentType(MediaType.APPLICATION_JSON).content(payload("denied", skills.detail(PLANNING).revision())))
            .andExpect(status().isForbidden());
        mvc.perform(delete(path(inTeam) + "/" + PLANNING).with(SessionTestSupport.authenticated(member.credential()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":\"x\"}"))
            .andExpect(status().isForbidden());
        jdbc.update("""
            insert into member_role(member_id,role_id,created_at)
            select ?, id, now() from `role` where tenant_id=? and code='ADMIN'
            """, memberId, owner.tenantId());
        String teamRevision = save(inTeam, PLANNING, "管理员定制", TenantWorkflowSkillService.NEW_REVISION);
        mvc.perform(get(path(inTeam) + "/" + PLANNING + "/template")
                .with(SessionTestSupport.authenticated(member.credential())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", is(skills.detail(PLANNING).content())));
        jdbc.update("update `role` set status='DISABLED' where tenant_id=? and code='ADMIN'", owner.tenantId());
        mvc.perform(get(path(inTeam) + "/" + PLANNING + "/template")
                .with(SessionTestSupport.authenticated(member.credential())))
            .andExpect(status().isForbidden());
        mvc.perform(put(path(inTeam) + "/" + PLANNING).with(SessionTestSupport.authenticated(member.credential()))
                .contentType(MediaType.APPLICATION_JSON).content(payload("denied", teamRevision)))
            .andExpect(status().isForbidden());
        mvc.perform(get(path(inTeam) + "/" + PLANNING).with(SessionTestSupport.authenticated(member.credential())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.containsString("管理员定制")));
        jdbc.update("update tenant_member set status='REMOVED' where id=?", memberId);
        mvc.perform(get(path(inTeam) + "/" + PLANNING + "/template")
                .with(SessionTestSupport.authenticated(member.credential())))
            .andExpect(status().is4xxClientError());
        mvc.perform(get(path(inTeam)).with(SessionTestSupport.authenticated(member.credential())))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void rejectsProtectedSkillsStaleWritesAndInvalidDocuments() throws Exception {
        Actor owner = actor(null);
        for (String code : new String[] {"short-drama-analysis-foundation", "short-drama-storyboard-material-reference", "short-drama-storyboard-planning", "short-drama-seedance-video-prompt", "custom-guide"}) {
            mvc.perform(get(path(owner) + "/" + code + "/template")
                    .with(SessionTestSupport.authenticated(owner.credential())))
                .andExpect(status().isForbidden());
            mvc.perform(put(path(owner) + "/" + code).with(SessionTestSupport.authenticated(owner.credential()))
                    .contentType(MediaType.APPLICATION_JSON).content(payload("invalid scope", "revision")))
                .andExpect(status().isForbidden());
        }
        String originalRevision = skills.detail(PLANNING).revision();
        String revision = save(owner, PLANNING, "first", TenantWorkflowSkillService.NEW_REVISION);
        for (String stale : new String[] {originalRevision, "wrong", TenantWorkflowSkillService.NEW_REVISION}) {
            mvc.perform(put(path(owner) + "/" + PLANNING).with(SessionTestSupport.authenticated(owner.credential()))
                    .contentType(MediaType.APPLICATION_JSON).content(payload("stale", stale)))
                .andExpect(status().isConflict());
            mvc.perform(delete(path(owner) + "/" + PLANNING).with(SessionTestSupport.authenticated(owner.credential()))
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedRevision", stale))))
                .andExpect(status().isConflict());
        }
        mvc.perform(put(path(owner) + "/" + PLANNING).with(SessionTestSupport.authenticated(owner.credential()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("content", "broken", "expectedRevision", revision))))
            .andExpect(status().isBadRequest());
        assertThat(skills.findTenantOverride(StoryboardAgentBootstrap.AGENT_CODE, owner.tenantId(), PLANNING).revision()).isEqualTo(revision);
    }

    @Test
    void platformCatalogCanFilterTenantRowsWithoutGrantingTenantEdit() throws Exception {
        Actor owner = actor(null), admin = actor("13800000999");
        save(owner, PLANNING, "平台只读检查", TenantWorkflowSkillService.NEW_REVISION);
        mvc.perform(delete("/api/platform/ai/workflow-skills/" + PLANNING)
                .with(SessionTestSupport.authenticated(admin.credential())))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode", is("WORKFLOW_SKILL_IN_USE")));
        mvc.perform(get("/api/platform/ai/workflow-skills").with(SessionTestSupport.authenticated(admin.credential()))
                .param("scope", "TENANT").param("agentCode", StoryboardAgentBootstrap.AGENT_CODE)
                .param("tenantId", owner.tenantId().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()", is(1)))
            .andExpect(jsonPath("$.data[0].scope", is("TENANT")));
        mvc.perform(put(path(owner) + "/" + PLANNING).with(SessionTestSupport.authenticated(admin.credential()))
                .contentType(MediaType.APPLICATION_JSON).content(payload("denied", "x")))
            .andExpect(status().isForbidden());
        jdbc.update("update tenant set status='DISABLED' where id=?", owner.tenantId());
        mvc.perform(get(path(owner)).with(SessionTestSupport.authenticated(owner.credential())))
            .andExpect(status().is4xxClientError());
    }

    private String save(Actor actor, String code, String body, String revision) throws Exception {
        MvcResult result = mvc.perform(put(path(actor) + "/" + code).with(SessionTestSupport.authenticated(actor.credential()))
                .contentType(MediaType.APPLICATION_JSON).content(payload(body, revision)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.scope", is("TENANT"))).andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.revision");
    }

    private String payload(String body, String revision) throws Exception {
        return json.writeValueAsString(Map.of("content", "---\nname: 团队分镜规则\ndescription: 团队定制\n---\n" + body, "expectedRevision", revision));
    }

    private String path(Actor actor) { return "/api/tenants/" + actor.tenantId() + "/workflow-skills"; }

    private Actor actor(String fixedMobile) throws Exception {
        String mobile = fixedMobile == null ? Long.toString(MOBILE.incrementAndGet()) : fixedMobile;
        MvcResult registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("mobile", mobile, "verificationCode", registrationVerificationCode(mvc, mobile),
                    "nickname", "Skill User", "password", "Password123"))))
            .andExpect(status().isOk()).andReturn();
        String credential = SessionTestSupport.sessionCredential(registered);
        Long userId = jdbc.queryForObject("select id from app_user where mobile=?", Long.class, mobile);
        MvcResult created = mvc.perform(post("/api/tenants").with(SessionTestSupport.authenticated(credential))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name", "团队" + userId, "type", "STUDIO"))))
            .andExpect(status().isOk()).andReturn();
        Number id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");
        rbac.initializeTenant(id.longValue());
        return new Actor(credential, userId, id.longValue());
    }

    private record Actor(String credential, Long userId, Long tenantId) { }
}
