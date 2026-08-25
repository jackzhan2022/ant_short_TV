package com.antshorttv.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class SessionTestSupport {

    private static final String SESSION_COOKIE_NAME = "ANT_SHORT_SESSION";

    private SessionTestSupport() {
    }

    public static String sessionCredential(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE_NAME);
        if (cookie == null) {
            throw new IllegalStateException("Authentication response did not set the session cookie");
        }
        return cookie.getValue();
    }

    public static RequestPostProcessor authenticated(String credential) {
        return request -> {
            request.setCookies(new Cookie(SESSION_COOKIE_NAME, credential));
            return csrf().postProcessRequest(request);
        };
    }
}
