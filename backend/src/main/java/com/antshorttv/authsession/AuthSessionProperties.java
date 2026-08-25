package com.antshorttv.authsession;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "auth.session")
public class AuthSessionProperties {

    @NotBlank
    @Size(min = 32)
    private String tokenPepper;

    @NotNull
    private Duration ttl = Duration.ofDays(7);

    @NotNull
    private Duration activityUpdateInterval = Duration.ofMinutes(5);

    @NotNull
    private Duration cleanupRetention = Duration.ofDays(30);

    @NotBlank
    private String cookieName = "ANT_SHORT_SESSION";

    private boolean cookieSecure = true;

    @NotBlank
    private String cookieSameSite = "Lax";

    public String getTokenPepper() {
        return tokenPepper;
    }

    public void setTokenPepper(String tokenPepper) {
        this.tokenPepper = tokenPepper;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration getActivityUpdateInterval() {
        return activityUpdateInterval;
    }

    public void setActivityUpdateInterval(Duration activityUpdateInterval) {
        this.activityUpdateInterval = activityUpdateInterval;
    }

    public Duration getCleanupRetention() {
        return cleanupRetention;
    }

    public void setCleanupRetention(Duration cleanupRetention) {
        this.cleanupRetention = cleanupRetention;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public String getCookieSameSite() {
        return cookieSameSite;
    }

    public void setCookieSameSite(String cookieSameSite) {
        this.cookieSameSite = cookieSameSite;
    }
}
