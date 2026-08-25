package com.antshorttv.platform;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlatformAuthorizationService {

    private final PlatformAuthorizationMapper mapper;

    public PlatformAuthorizationService(PlatformAuthorizationMapper mapper) {
        this.mapper = mapper;
    }

    public List<String> roleCodes(Long userId) {
        return mapper.selectRoleCodes(userId);
    }

    public List<String> permissionCodes(Long userId) {
        return mapper.selectPermissionCodes(userId);
    }

    public boolean hasPermission(Long userId, String permissionCode) {
        return permissionCodes(userId).contains(permissionCode);
    }
}
