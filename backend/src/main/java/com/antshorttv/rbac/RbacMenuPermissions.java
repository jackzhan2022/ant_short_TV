package com.antshorttv.rbac;

import java.util.List;

/** The only assignable tenant permissions. Data access is checked independently. */
final class RbacMenuPermissions {
    static final List<RbacPermissionDefinition> ALL = List.of(
        menu("SHORT_DRAMA_CREATION", "短剧创作", "MENU_CREATION"),
        menu("FREE_CREATION", "自由创作", "MENU_CREATION"),
        menu("VIDEO_DECOMPOSITION", "视频拆剧", "MENU_CREATION"),
        menu("SCRIPT_REVIEW", "剧本审核", "MENU_CREATION"),
        menu("PROJECTS", "项目中心", "MENU_WORKSPACE"),
        menu("ASSET_LIBRARY", "资产库", "MENU_WORKSPACE"),
        menu("PRODUCTION_TASKS", "任务中心", "MENU_WORKSPACE"),
        menu("DRAMA_LIBRARY", "剧集管理", "MENU_MANAGEMENT"),
        menu("TEAM_MANAGEMENT", "团队管理", "MENU_MANAGEMENT"),
        menu("AI_MANAGEMENT", "管理后台", "MENU_MANAGEMENT")
    );

    private static RbacPermissionDefinition menu(String action, String name, String resource) {
        return new RbacPermissionDefinition("MENU:" + action, name, PermissionType.MENU, resource, action);
    }

    static List<String> defaultsFor(String roleCode) {
        return ALL.stream().map(RbacPermissionDefinition::code)
            .filter(code -> !"MEMBER".equals(roleCode) || !List.of(
                "MENU:TEAM_MANAGEMENT", "MENU:AI_MANAGEMENT", "MENU:DRAMA_LIBRARY").contains(code))
            .toList();
    }

    private RbacMenuPermissions() {
    }
}
