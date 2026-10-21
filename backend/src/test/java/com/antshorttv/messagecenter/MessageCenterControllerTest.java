package com.antshorttv.messagecenter;

import static com.antshorttv.support.SessionTestSupport.authenticated;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.antshorttv.support.SessionTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MessageCenterControllerTest extends com.antshorttv.support.RegistrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MessageCenterService service;

    @Test
    void enforcesSenderAndRecipientPermissionsAndPersistsInboxActions() throws Exception {
        mvc.perform(get("/api/messages")).andExpect(status().isUnauthorized());
        var admin = register("13800000999");
        var alice = register("13800020801");
        var bob = register("13800020802");
        String body = json.writeValueAsString(Map.of("category", "PRODUCT", "title", "产品上线通知",
            "content", "消息中心现已上线。", "allUsers", false, "recipientIds", List.of(alice.id(), bob.id())));
        mvc.perform(post("/api/messages/send").with(authenticated(alice.token()))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(get("/api/messages/recipients").with(authenticated(alice.token())))
            .andExpect(status().isForbidden());
        var sent = mvc.perform(post("/api/messages/send").with(authenticated(admin.token()))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.recipientCount", is(2))).andReturn();
        String id = JsonPath.read(sent.getResponse().getContentAsString(), "$.data.id");
        mvc.perform(get("/api/messages").with(authenticated(alice.token())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total", is(1)))
            .andExpect(jsonPath("$.data.items[0].source", is("ADMIN")))
            .andExpect(jsonPath("$.data.items[0].read", is(false)));
        mvc.perform(get("/api/messages/summary").with(authenticated(alice.token())))
            .andExpect(jsonPath("$.data.unread", is(1))).andExpect(jsonPath("$.data.categories.PRODUCT", is(1)));
        mvc.perform(get("/api/messages/" + id).with(authenticated(admin.token())))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/messages/read").with(authenticated(admin.token()))
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("ids", List.of(id)))))
            .andExpect(status().isOk());
        mvc.perform(get("/api/messages/summary").with(authenticated(alice.token())))
            .andExpect(jsonPath("$.data.unread", is(1)));
        mvc.perform(get("/api/messages/" + id).with(authenticated(alice.token())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", is("消息中心现已上线。")));
        mvc.perform(post("/api/messages/read").with(authenticated(alice.token()))
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("ids", List.of(id)))))
            .andExpect(status().isOk());
        mvc.perform(get("/api/messages/summary").with(authenticated(alice.token())))
            .andExpect(jsonPath("$.data.unread", is(0)));
        mvc.perform(get("/api/messages/summary").with(authenticated(bob.token())))
            .andExpect(jsonPath("$.data.unread", is(1)));
        mvc.perform(get("/api/messages").param("status", "UNREAD").with(authenticated(alice.token())))
            .andExpect(jsonPath("$.data.total", is(0)));
        mvc.perform(get("/api/messages").param("keyword", "上线").with(authenticated(bob.token())))
            .andExpect(jsonPath("$.data.total", is(1)));
        mvc.perform(get("/api/messages").param("category", "SECURITY").with(authenticated(bob.token())))
            .andExpect(jsonPath("$.data.total", is(0)));
        mvc.perform(post("/api/messages/delete").with(authenticated(alice.token()))
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("ids", List.of(id)))))
            .andExpect(status().isOk());
        mvc.perform(get("/api/messages/" + id).with(authenticated(alice.token())))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/messages/" + id).with(authenticated(bob.token())))
            .andExpect(status().isOk());
        mvc.perform(post("/api/messages/read").with(authenticated(bob.token()))
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/messages/read").with(authenticated(bob.token()))
            .contentType(MediaType.APPLICATION_JSON).content("{\"all\":true,\"category\":\"SECURITY\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/messages/summary").with(authenticated(bob.token())))
            .andExpect(jsonPath("$.data.unread", is(1)));
        mvc.perform(post("/api/messages/read").with(authenticated(bob.token()))
            .contentType(MediaType.APPLICATION_JSON).content("{\"all\":true,\"category\":\"PRODUCT\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/messages/summary").with(authenticated(bob.token())))
            .andExpect(jsonPath("$.data.unread", is(0)));
        mvc.perform(get("/api/messages").param("status", "INVALID").with(authenticated(bob.token())))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/messages/send").with(authenticated(admin.token()))
            .contentType(MediaType.APPLICATION_JSON).content(body.replace("产品上线通知", " ")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/messages/system").with(authenticated(bob.token()))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isMethodNotAllowed());

        var system = service.sendSystem(List.of(alice.id()), "SYSTEM", "任务完成", "系统流程发出的消息");
        mvc.perform(get("/api/messages/" + system.id()).with(authenticated(alice.token())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.source", is("SYSTEM")));
        mvc.perform(get("/api/messages/" + system.id()).with(authenticated(bob.token())))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/messages/send").with(authenticated(admin.token()))
            .contentType(MediaType.APPLICATION_JSON).content(body.replace("{", "{\"source\":\"SYSTEM\",\"senderId\":999,")))
            .andExpect(status().isOk());
        mvc.perform(get("/api/messages").param("category", "PRODUCT").param("pageSize", "1").with(authenticated(bob.token())))
            .andExpect(jsonPath("$.data.total", is(2))).andExpect(jsonPath("$.data.items.length()", is(1)))
            .andExpect(jsonPath("$.data.items[0].source", is("ADMIN")));
        mvc.perform(post("/api/messages/send").with(authenticated(admin.token()))
            .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"SYSTEM\",\"title\":\"平台公告\",\"content\":\"所有用户可见\",\"allUsers\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.recipientCount", is(3)));
        mvc.perform(get("/api/messages").param("keyword", "平台公告").with(authenticated(alice.token())))
            .andExpect(jsonPath("$.data.total", is(1)));
        mvc.perform(get("/api/messages").param("keyword", "%").with(authenticated(alice.token())))
            .andExpect(jsonPath("$.data.total", is(0)));
        mvc.perform(get("/api/messages/sent").with(authenticated(alice.token())))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/messages/sent/" + id).with(authenticated(bob.token())))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/messages/sent/" + id + "/recipients").with(authenticated(bob.token())))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/messages/sent").with(authenticated(admin.token())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total", is(4)));
        mvc.perform(get("/api/messages/sent").param("category", "PRODUCT").param("pageSize", "1").with(authenticated(admin.token())))
            .andExpect(jsonPath("$.data.total", is(2))).andExpect(jsonPath("$.data.items.length()", is(1)));
        mvc.perform(get("/api/messages/sent").param("source", "SYSTEM").with(authenticated(admin.token())))
            .andExpect(jsonPath("$.data.total", is(1))).andExpect(jsonPath("$.data.items[0].id", is(system.id())));
        mvc.perform(get("/api/messages/sent/" + id).with(authenticated(admin.token())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.recipientCount", is(2)))
            .andExpect(jsonPath("$.data.readCount", is(2))).andExpect(jsonPath("$.data.unreadCount", is(0)))
            .andExpect(jsonPath("$.data.audience", is("SELECTED")))
            .andExpect(jsonPath("$.data.content", is("消息中心现已上线。")));
        mvc.perform(get("/api/messages/sent/" + id + "/recipients").param("pageSize", "1").with(authenticated(admin.token())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total", is(2)))
            .andExpect(jsonPath("$.data.items.length()", is(1))).andExpect(jsonPath("$.data.items[0].deleted", is(true)));
        mvc.perform(get("/api/messages/sent").param("keyword", "平台公告").with(authenticated(admin.token())))
            .andExpect(jsonPath("$.data.items[0].audience", is("ALL")))
            .andExpect(jsonPath("$.data.items[0].recipientCount", is(3)));
    }

    private Registration register(String mobile) throws Exception {
        var result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("mobile", mobile, "verificationCode",
                registrationVerificationCode(mvc, mobile), "nickname", "消息测试" + mobile.substring(7),
                "password", "Password123")))).andExpect(status().isOk()).andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.user.id");
        return new Registration(id.longValue(), SessionTestSupport.sessionCredential(result));
    }
    private record Registration(long id, String token) {}
}
