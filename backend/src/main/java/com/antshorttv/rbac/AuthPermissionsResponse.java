package com.antshorttv.rbac;

import java.util.List;
import java.util.Set;

public record AuthPermissionsResponse(
    List<String> menus,
    Set<String> permissions
) {
}
