package com.antshorttv.authsession;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthSessionCookieService {

    private final AuthSessionProperties properties;

    public AuthSessionCookieService(AuthSessionProperties properties) {
        this.properties = properties;
    }

    public void write(HttpServletResponse response, IssuedSession session) {
        ResponseCookie cookie = baseCookie(session.credential(), properties.getTtl()).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = baseCookie("", Duration.ZERO).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
            .filter(cookie -> properties.getCookieName().equals(cookie.getName()))
            .map(Cookie::getValue)
            .filter(value -> !value.isBlank())
            .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value, Duration maxAge) {
        return ResponseCookie.from(properties.getCookieName(), value)
            .httpOnly(true)
            .secure(properties.isCookieSecure())
            .sameSite(properties.getCookieSameSite())
            .path("/")
            .maxAge(maxAge);
    }
}
