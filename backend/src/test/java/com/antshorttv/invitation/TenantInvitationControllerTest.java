package com.antshorttv.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.member.MemberStatus;
import com.antshorttv.member.MemberType;
import com.antshorttv.member.TenantMemberEntity;
import com.antshorttv.member.TenantMemberMapper;
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
class TenantInvitationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantInvitationMapper tenantInvitationMapper;

    @Autowired
    private TenantMemberMapper tenantMemberMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    void ownerInvitesRegisteredMobileAndDuplicatePendingIsRejected() throws Exception {
        String ownerToken = registerUser("13800000301", "Owner");
        Long tenantId = createTenant(ownerToken, "邀请测试团队A");
        registerUser("13800000302", "Invitee");

        MvcResult invite = invite(ownerToken, tenantId, "13800000302");
        String token = com.jayway.jsonpath.JsonPath.read(invite.getResponse().getContentAsString(), "$.data.token");
        assertThat(token).isNotBlank();

        mockMvc.perform(post("/api/tenants/%d/invitations".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mobile\":\"13800000302\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode", is("DUPLICATE_PENDING_INVITATION")))
            .andExpect(jsonPath("$.errorMessage", is("该用户已有待处理邀请。")));
    }

    @Test
    void nonOwnerCannotInviteAndExistingMemberCannotBeInvited() throws Exception {
        String ownerToken = registerUser("13800000303", "Owner");
        Long tenantId = createTenant(ownerToken, "邀请测试团队B");
        String memberToken = registerUser("13800000304", "Member");
        addMember(tenantId, "13800000304");

        mockMvc.perform(post("/api/tenants/%d/invitations".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mobile\":\"13800000305\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/tenants/%d/invitations".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mobile\":\"13800000304\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode", is("ALREADY_TENANT_MEMBER")))
            .andExpect(jsonPath("$.errorMessage", is("你已经是该创作团队成员，无需重复加入。")));
    }

    @Test
    void inviteeAcceptsInvitationAndBecomesMember() throws Exception {
        String ownerToken = registerUser("13800000306", "Owner");
        Long tenantId = createTenant(ownerToken, "邀请测试团队C");
        String inviteeToken = registerUser("13800000307", "Invitee");
        String token = readToken(invite(ownerToken, tenantId, "13800000307"));

        mockMvc.perform(get("/api/invitations/%s".formatted(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("PENDING")));

        mockMvc.perform(post("/api/invitations/%s/accept".formatted(token))
                .header(HttpHeaders.AUTHORIZATION, bearer(inviteeToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("ACCEPTED")));

        TenantInvitationEntity invitation = tenantInvitationMapper.selectByToken(token);
        TenantMemberEntity member = tenantMemberMapper.selectByTenantIdAndUserId(tenantId, invitation.getInviteUserId());
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED.name());
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE.name());
        assertThat(member.getMemberType()).isEqualTo(MemberType.MEMBER.name());
    }

    @Test
    void inviteeRejectsAndOwnerCancelsPendingInvitation() throws Exception {
        String ownerToken = registerUser("13800000308", "Owner");
        Long tenantId = createTenant(ownerToken, "邀请测试团队D");
        String rejectToken = registerUser("13800000309", "Reject");
        registerUser("13800000310", "Cancel");

        String rejectedToken = readToken(invite(ownerToken, tenantId, "13800000309"));
        mockMvc.perform(post("/api/invitations/%s/reject".formatted(rejectedToken))
                .header(HttpHeaders.AUTHORIZATION, bearer(rejectToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("REJECTED")));

        MvcResult cancelInvite = invite(ownerToken, tenantId, "13800000310");
        Number invitationId = com.jayway.jsonpath.JsonPath.read(cancelInvite.getResponse().getContentAsString(), "$.data.id");
        mockMvc.perform(post("/api/invitations/%d/cancel".formatted(invitationId.longValue()))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("CANCELLED")));
    }

    @Test
    void ownerListsTenantInvitationsAndMemberCannotListThem() throws Exception {
        String ownerToken = registerUser("13800000313", "Owner");
        Long tenantId = createTenant(ownerToken, "邀请测试团队F");
        String memberToken = registerUser("13800000314", "Member");
        addMember(tenantId, "13800000314");
        invite(ownerToken, tenantId, "13800000315");

        mockMvc.perform(get("/api/tenants/%d/invitations".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].inviteMobile", is("13800000315")))
            .andExpect(jsonPath("$.data[0].status", is("PENDING")));

        mockMvc.perform(get("/api/tenants/%d/invitations".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
            .andExpect(status().isForbidden());
    }

    @Test
    void expiredInvitationCannotBeAccepted() throws Exception {
        String ownerToken = registerUser("13800000311", "Owner");
        Long tenantId = createTenant(ownerToken, "邀请测试团队E");
        String inviteeToken = registerUser("13800000312", "Invitee");
        String token = readToken(invite(ownerToken, tenantId, "13800000312"));

        TenantInvitationEntity invitation = tenantInvitationMapper.selectByToken(token);
        invitation.setExpiredAt(LocalDateTime.now().minusDays(1));
        tenantInvitationMapper.updateById(invitation);

        mockMvc.perform(post("/api/invitations/%s/accept".formatted(token))
                .header(HttpHeaders.AUTHORIZATION, bearer(inviteeToken)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode", is("INVITATION_EXPIRED")))
            .andExpect(jsonPath("$.errorMessage", is("该邀请已过期，请联系团队管理员重新发送邀请。")));
    }

    private MvcResult invite(String ownerToken, Long tenantId, String mobile) throws Exception {
        return mockMvc.perform(post("/api/tenants/%d/invitations".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mobile\":\"%s\"}".formatted(mobile)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token", not("")))
            .andReturn();
    }

    private String readToken(MvcResult result) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private String registerUser(String mobile, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"%s","verificationCode":"123456","nickname":"%s","password":"Password123"}
                    """.formatted(mobile, nickname)))
            .andExpect(status().isOk())
            .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private Long createTenant(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tenants")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","type":"STUDIO","description":"邀请管理测试"}
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
