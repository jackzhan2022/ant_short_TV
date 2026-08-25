package com.antshorttv.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import java.time.LocalDateTime;
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
class TenantMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TenantMemberMapper tenantMemberMapper;

    @Test
    void ownerListsAndRemovesNormalMember() throws Exception {
        String ownerToken = registerUser("13800000201", "Owner");
        Long tenantId = createTenant(ownerToken, "成员测试团队");
        String memberToken = registerUser("13800000202", "Member");
        Long memberId = addMember(tenantId, "13800000202");

        mockMvc.perform(get("/api/tenants/%d/members".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(ownerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(2)));

        mockMvc.perform(delete("/api/tenants/%d/members/%d".formatted(tenantId, memberId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(ownerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)));

        assertThat(tenantMemberMapper.selectById(memberId).getStatus()).isEqualTo(MemberStatus.REMOVED.name());

        mockMvc.perform(get("/api/tenants/%d".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(memberToken)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("MEMBER_REMOVED")))
            .andExpect(jsonPath("$.errorMessage", is("你已不再是该创作团队成员。")));
    }

    @Test
    void memberCanLeaveTenantButOwnerCannotLeaveDirectly() throws Exception {
        String ownerToken = registerUser("13800000203", "Owner");
        Long tenantId = createTenant(ownerToken, "退出测试团队");
        String memberToken = registerUser("13800000204", "Member");
        Long memberId = addMember(tenantId, "13800000204");

        mockMvc.perform(post("/api/tenants/%d/members/leave".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(memberToken)))
            .andExpect(status().isOk());
        assertThat(tenantMemberMapper.selectById(memberId).getStatus()).isEqualTo(MemberStatus.REMOVED.name());

        mockMvc.perform(post("/api/tenants/%d/members/leave".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(ownerToken)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("OWNER_LEAVE_BLOCKED")))
            .andExpect(jsonPath("$.errorMessage", is("团队所有者不能直接退出，请先转让团队所有权。")));
    }

    @Test
    void ownerTransfersOwnershipToAnotherActiveMember() throws Exception {
        String ownerToken = registerUser("13800000205", "Owner");
        Long tenantId = createTenant(ownerToken, "转让测试团队");
        registerUser("13800000206", "Target");
        Long targetMemberId = addMember(tenantId, "13800000206");

        mockMvc.perform(post("/api/tenants/%d/transfer-owner".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetMemberId\":%d}".formatted(targetMemberId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.memberType", is("MEMBER")));

        TenantMemberEntity target = tenantMemberMapper.selectById(targetMemberId);
        assertThat(target.getMemberType()).isEqualTo(MemberType.OWNER.name());
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
                    {"name":"%s","type":"STUDIO","description":"成员管理测试"}
                    """.formatted(name)))
            .andExpect(status().isOk())
            .andReturn();
        Number value = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
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
}
