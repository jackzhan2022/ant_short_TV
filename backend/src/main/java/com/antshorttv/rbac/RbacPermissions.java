package com.antshorttv.rbac;

import java.util.List;

final class RbacPermissions {

    static final List<RbacPermissionDefinition> ALL = List.of(
        new RbacPermissionDefinition("TENANT:VIEW", "查看团队", PermissionType.PAGE, "TENANT", "VIEW"),
        new RbacPermissionDefinition("TENANT:EDIT", "修改团队", PermissionType.BUTTON, "TENANT", "EDIT"),
        new RbacPermissionDefinition("MEMBER:VIEW", "查看成员", PermissionType.PAGE, "MEMBER", "VIEW"),
        new RbacPermissionDefinition("MEMBER:INVITE", "邀请成员", PermissionType.BUTTON, "MEMBER", "INVITE"),
        new RbacPermissionDefinition("MEMBER:REMOVE", "移除成员", PermissionType.BUTTON, "MEMBER", "REMOVE"),
        new RbacPermissionDefinition("ROLE:VIEW", "查看角色", PermissionType.PAGE, "ROLE", "VIEW"),
        new RbacPermissionDefinition("ROLE:CREATE", "创建角色", PermissionType.BUTTON, "ROLE", "CREATE"),
        new RbacPermissionDefinition("ROLE:EDIT", "编辑角色", PermissionType.BUTTON, "ROLE", "EDIT"),
        new RbacPermissionDefinition("ROLE:DELETE", "删除角色", PermissionType.BUTTON, "ROLE", "DELETE"),
        new RbacPermissionDefinition("ORGANIZATION:VIEW", "查看组织", PermissionType.PAGE, "ORGANIZATION", "VIEW"),
        new RbacPermissionDefinition("ORGANIZATION:CREATE", "创建组织", PermissionType.BUTTON, "ORGANIZATION", "CREATE"),
        new RbacPermissionDefinition("ORGANIZATION:EDIT", "编辑组织", PermissionType.BUTTON, "ORGANIZATION", "EDIT"),
        new RbacPermissionDefinition("ORGANIZATION:DELETE", "删除组织", PermissionType.BUTTON, "ORGANIZATION", "DELETE"),
        new RbacPermissionDefinition("PROJECT:VIEW", "查看项目", PermissionType.PAGE, "PROJECT", "VIEW"),
        new RbacPermissionDefinition("PROJECT:CREATE", "创建项目", PermissionType.BUTTON, "PROJECT", "CREATE"),
        new RbacPermissionDefinition("PROJECT:EDIT", "编辑项目", PermissionType.BUTTON, "PROJECT", "EDIT"),
        new RbacPermissionDefinition("PROJECT:DELETE", "删除项目", PermissionType.BUTTON, "PROJECT", "DELETE"),
        new RbacPermissionDefinition("PROJECT_MEMBER:VIEW", "查看项目成员", PermissionType.PAGE, "PROJECT_MEMBER", "VIEW"),
        new RbacPermissionDefinition("PROJECT_MEMBER:ADD", "添加项目成员", PermissionType.BUTTON, "PROJECT_MEMBER", "ADD"),
        new RbacPermissionDefinition("PROJECT_MEMBER:UPDATE", "修改项目成员", PermissionType.BUTTON, "PROJECT_MEMBER", "UPDATE"),
        new RbacPermissionDefinition("PROJECT_MEMBER:REMOVE", "移除项目成员", PermissionType.BUTTON, "PROJECT_MEMBER", "REMOVE"),
        new RbacPermissionDefinition("PROJECT_ROLE:VIEW", "查看项目角色", PermissionType.PAGE, "PROJECT_ROLE", "VIEW"),
        new RbacPermissionDefinition("PROJECT_ROLE:CREATE", "创建项目角色", PermissionType.BUTTON, "PROJECT_ROLE", "CREATE"),
        new RbacPermissionDefinition("PROJECT_ROLE:UPDATE", "编辑项目角色", PermissionType.BUTTON, "PROJECT_ROLE", "UPDATE"),
        new RbacPermissionDefinition("PROJECT_ROLE:DELETE", "删除项目角色", PermissionType.BUTTON, "PROJECT_ROLE", "DELETE"),
        new RbacPermissionDefinition("PROJECT_ROLE:PERMISSION", "配置项目角色权限", PermissionType.BUTTON, "PROJECT_ROLE", "PERMISSION"),
        new RbacPermissionDefinition("SCRIPT:VIEW", "查看剧本", PermissionType.PAGE, "SCRIPT", "VIEW"),
        new RbacPermissionDefinition("SCRIPT:CREATE", "创建剧本", PermissionType.BUTTON, "SCRIPT", "CREATE"),
        new RbacPermissionDefinition("SCRIPT:EDIT", "编辑剧本", PermissionType.BUTTON, "SCRIPT", "EDIT"),
        new RbacPermissionDefinition("SCRIPT:DELETE", "删除剧本", PermissionType.BUTTON, "SCRIPT", "DELETE"),
        new RbacPermissionDefinition("AI_SERVICE:VIEW", "查看AI服务", PermissionType.PAGE, "AI_SERVICE", "VIEW"),
        new RbacPermissionDefinition("AI_SERVICE:CREATE", "新增AI服务", PermissionType.BUTTON, "AI_SERVICE", "CREATE"),
        new RbacPermissionDefinition("AI_SERVICE:EDIT", "编辑AI服务", PermissionType.BUTTON, "AI_SERVICE", "EDIT"),
        new RbacPermissionDefinition("AI_SERVICE:DELETE", "删除AI服务", PermissionType.BUTTON, "AI_SERVICE", "DELETE"),
        new RbacPermissionDefinition("AI_SERVICE:TEST", "测试AI服务", PermissionType.BUTTON, "AI_SERVICE", "TEST"),
        new RbacPermissionDefinition("AI_SERVICE:USE", "使用AI服务", PermissionType.BUTTON, "AI_SERVICE", "USE")
    );

    private RbacPermissions() {
    }
}
