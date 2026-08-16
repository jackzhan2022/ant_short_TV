package com.antshorttv.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.antshorttv.invitation.TenantInvitationMapper;
import com.antshorttv.invitation.TenantInvitationEntity;
import com.antshorttv.member.MemberStatus;
import com.antshorttv.member.MemberType;
import com.antshorttv.member.TenantMemberEntity;
import com.antshorttv.member.TenantMemberMapper;
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
class TenantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantMemberMapper tenantMemberMapper;

    @Autowired
    private TenantInvitationMapper tenantInvitationMapper;

    @Test
    void createsTenantWithOwnerMemberOnly() throws Exception {
        String token = registerUser("13800000101", "张三");

        MvcResult result = createTenant(token, "星河影视", "COMPANY");
        Long tenantId = readLong(result, "$.data.id");
        Long memberId = readLong(result, "$.data.memberId");

        TenantMemberEntity member = tenantMemberMapper.selectById(memberId);
        assertThat(member.getTenantId()).isEqualTo(tenantId);
        assertThat(member.getMemberType()).isEqualTo(MemberType.OWNER.name());
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE.name());
        assertThat(tenantInvitationMapper.selectCount(
            new LambdaQueryWrapper<TenantInvitationEntity>().eq(TenantInvitationEntity::getTenantId, tenantId))).isZero();
    }

    @Test
    void listsMultipleTenantsForCurrentUser() throws Exception {
        String token = registerUser("13800000102", "李四");
        createTenant(token, "星河影视", "COMPANY");
        createTenant(token, "蓝海传媒", "STUDIO");

        mockMvc.perform(get("/api/tenants/my").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[0].memberType", is("OWNER")));
    }

    @Test
    void switchesCurrentTenantAndRejectsDisabledTenant() throws Exception {
        String token = registerUser("13800000103", "王五");
        MvcResult result = createTenant(token, "停用测试团队", "OTHER");
        Long tenantId = readLong(result, "$.data.id");

        mockMvc.perform(post("/api/tenants/current")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":%d}".formatted(tenantId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.tenantId", is(tenantId.intValue())));

        mockMvc.perform(put("/api/tenants/%d/status".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DISABLED\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/tenants/current")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":%d}".formatted(tenantId)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("TENANT_DISABLED")))
            .andExpect(jsonPath("$.errorMessage", is("当前创作团队已被停用，暂时无法进入。")));
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

    private MvcResult createTenant(String token, String name, String type) throws Exception {
        return mockMvc.perform(post("/api/tenants")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","type":"%s","logo":"https://example.com/logo.png","description":"短剧制作团队"}
                    """.formatted(name, type)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.name", is(name)))
            .andReturn();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private Long readLong(MvcResult result, String path) throws Exception {
        Number value = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }
}
