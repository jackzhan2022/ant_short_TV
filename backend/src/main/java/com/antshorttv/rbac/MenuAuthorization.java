package com.antshorttv.rbac;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Maps internal business operations to assignable menus. Operation IDs are never persisted as grants. */
public final class MenuAuthorization {
    public static final Set<String> MENUS = RbacMenuPermissions.ALL.stream()
        .map(RbacPermissionDefinition::code).collect(Collectors.toUnmodifiableSet());
    private static final Map<String, Set<String>> OPERATIONS;
    static {
        Map<String, Set<String>> operations = new LinkedHashMap<>();
        register(operations, Set.of("MENU:DRAMA_LIBRARY"),
            "DRAMA:VIEW", "DRAMA:MANAGE", "DRAMA:PUBLISH", "MINIAPP:MANAGE");
        register(operations, Set.of("MENU:TEAM_MANAGEMENT"),
            "TENANT:VIEW", "TENANT:EDIT", "MEMBER:VIEW", "MEMBER:INVITE", "MEMBER:REMOVE", "ROLE:VIEW", "ROLE:CREATE", "ROLE:EDIT", "ROLE:DELETE", "BILLING:MANAGE");
        register(operations, Set.of("MENU:PROJECTS", "MENU:SHORT_DRAMA_CREATION", "MENU:ASSET_LIBRARY", "MENU:PRODUCTION_TASKS", "MENU:SCRIPT_REVIEW", "MENU:VIDEO_DECOMPOSITION", "MENU:DRAMA_LIBRARY"),
            "PROJECT:VIEW", "PROJECT_MEMBER:VIEW", "PROJECT_ROLE:VIEW", "SCRIPT:VIEW", "ELEMENT:VIEW", "STORYBOARD:VIEW", "PROJECT_AI_CONFIG_VIEW", "AI_VIDEO_TASK:VIEW", "AI_VIDEO_RESULT:DOWNLOAD", "AI_VOICE_TASK:VIEW", "AI_VOICE_RESULT:DOWNLOAD", "SUBTITLE:VIEW", "SHOT_COMPOSE:VIEW", "SHOT_COMPOSE:DOWNLOAD", "EPISODE_COMPOSE:VIEW", "EPISODE_VERSION:VIEW", "EPISODE_VERSION:DOWNLOAD", "AI_IMAGE_TASK:VIEW");
        register(operations, Set.of("MENU:PROJECTS", "MENU:SHORT_DRAMA_CREATION"),
            "PROJECT:CREATE", "PROJECT:EDIT", "PROJECT:DELETE", "PROJECT_MEMBER:ADD", "PROJECT_MEMBER:UPDATE", "PROJECT_MEMBER:REMOVE", "PROJECT_ROLE:CREATE", "PROJECT_ROLE:UPDATE", "PROJECT_ROLE:DELETE", "PROJECT_ROLE:PERMISSION", "SCRIPT:CREATE", "SCRIPT:EDIT", "SCRIPT:DELETE", "ELEMENT:EDIT", "STORYBOARD:EDIT", "PROJECT_AI_CONFIG_EDIT", "SUBTITLE:EDIT", "SUBTITLE:DELETE");
        register(operations, Set.of("MENU:PROJECTS", "MENU:SHORT_DRAMA_CREATION", "MENU:PRODUCTION_TASKS"),
            "SCRIPT:AI_GENERATE", "SCRIPT:AI_REWRITE", "ELEMENT:AI_EXTRACT", "STORYBOARD:AI_BREAKDOWN", "PROMPT:AI_GENERATE", "AI_VIDEO_TASK:CREATE", "AI_VIDEO_TASK:CANCEL", "AI_VIDEO_TASK:DELETE", "AI_VIDEO_RESULT:SAVE", "AI_VIDEO_RESULT:BIND", "AI_VOICE_TASK:CREATE", "AI_VOICE_TASK:CANCEL", "AI_VOICE_TASK:DELETE", "AI_VOICE_RESULT:SAVE", "AI_VOICE_RESULT:BIND", "SHOT_COMPOSE:CREATE", "SHOT_COMPOSE:SAVE", "SHOT_COMPOSE:DELETE", "SHOT_COMPOSE:BIND", "EPISODE_COMPOSE:CREATE", "EPISODE_COMPOSE:CANCEL", "EPISODE_COMPOSE:DELETE", "EPISODE_VERSION:SET_CURRENT", "EPISODE_VERSION:DELETE", "EPISODE_VERSION:SAVE_MATERIAL", "AI_IMAGE_TASK:CREATE", "AI_IMAGE_TASK:CANCEL", "AI_IMAGE_TASK:DELETE", "AI_IMAGE_RESULT:SAVE", "AI_IMAGE_RESULT:BIND");
        register(operations, Set.of("MENU:PROJECTS", "MENU:SHORT_DRAMA_CREATION", "MENU:SCRIPT_REVIEW", "MENU:VIDEO_DECOMPOSITION", "MENU:FREE_CREATION", "MENU:PRODUCTION_TASKS"),
            "AI_SERVICE:USE");
        register(operations, Set.of("MENU:AI_MANAGEMENT"),
            "AI_CALL_LOG:VIEW");
        register(operations, Set.of("MENU:TEAM_MANAGEMENT", "MENU:PROJECTS", "MENU:SHORT_DRAMA_CREATION"),
            "PROJECT_MEMBER:CANDIDATES");
        register(operations, Set.of("MENU:PRODUCTION_TASKS", "MENU:FREE_CREATION"),
            "PRODUCTION_TASK:CONTROL");
        register(operations, Set.of("MENU:PROJECTS", "MENU:SHORT_DRAMA_CREATION", "MENU:FREE_CREATION",
            "MENU:VIDEO_DECOMPOSITION", "MENU:SCRIPT_REVIEW", "MENU:DRAMA_LIBRARY"), "MEDIA:UPLOAD");
        OPERATIONS = Map.copyOf(operations);
    }

    private static void register(Map<String, Set<String>> operations, Set<String> menus, String... names) {
        for (String name : names) operations.put(name, menus);
    }

    public static boolean allows(Set<String> granted, String operation) {
        if (granted == null || operation == null) return false;
        Set<String> required = MENUS.contains(operation) ? Set.of(operation) : OPERATIONS.get(operation);
        return required != null && required.stream().anyMatch(granted::contains);
    }

    public static boolean canReadProject(Set<String> granted) {
        return allows(granted, "PROJECT:VIEW");
    }

    public static boolean canWorkOnProject(Set<String> granted) {
        return allows(granted, "PROJECT:EDIT");
    }

    private MenuAuthorization() { }
}
