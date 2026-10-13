package com.antshorttv.drama;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class MiniappOwnership {
    private final Environment environment;

    public MiniappOwnership(Environment environment) { this.environment = environment; }

    public void require(String appid, Long tenantId) {
        String configured = environment.getProperty("miniapp.apps." + appid + ".tenant-id");
        Long owner;
        try {
            owner = configured == null ? null : Long.valueOf(configured.trim());
        } catch (NumberFormatException exception) {
            throw unconfigured();
        }
        if (owner == null || owner <= 0) throw unconfigured();
        if (!owner.equals(tenantId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该小程序可信归属与当前团队或绑定不一致。");
        }
    }

    private BusinessException unconfigured() {
        return DramaLibraryService.invalid("该小程序未配置有效的服务端可信团队归属 tenant-id，不能绑定或认证。");
    }
}
