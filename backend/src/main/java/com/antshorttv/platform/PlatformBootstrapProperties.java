package com.antshorttv.platform;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "auth.platform")
public class PlatformBootstrapProperties {
    private String initialAdminMobile;

    public String getInitialAdminMobile() {
        return initialAdminMobile;
    }

    public void setInitialAdminMobile(String initialAdminMobile) {
        this.initialAdminMobile = initialAdminMobile;
    }
}
