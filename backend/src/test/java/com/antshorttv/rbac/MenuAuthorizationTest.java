package com.antshorttv.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MenuAuthorizationTest {
    @Test
    void onlyMenuGrantsAuthorizeOperations() {
        assertThat(MenuAuthorization.MENUS).hasSize(10);
        assertThat(MenuAuthorization.allows(Set.of("ROLE:EDIT"), "ROLE:EDIT")).isFalse();
        assertThat(MenuAuthorization.allows(Set.of("PROJECT:EDIT_ALL"), "PROJECT:EDIT")).isFalse();
        assertThat(MenuAuthorization.allows(Set.of("MENU:PROJECTS"), "UNKNOWN")).isFalse();
        assertThat(MenuAuthorization.allows(Set.of("MENU:TEAM_MANAGEMENT"), "ROLE:EDIT")).isTrue();
        assertThat(MenuAuthorization.allows(Set.of("MENU:TEAM_MANAGEMENT"), "ROLE:DELETE")).isTrue();
        assertThat(MenuAuthorization.allows(Set.of("MENU:PROJECTS"), "ROLE:EDIT")).isFalse();
    }

    @Test
    void projectAndCreationMenusEnableProjectActionsWhileAssetsOnlyReadAndDownload() {
        for (String menu : Set.of("MENU:PROJECTS", "MENU:SHORT_DRAMA_CREATION")) {
            for (String operation : Set.of("PROJECT:CREATE", "SCRIPT:EDIT", "AI_VIDEO_TASK:CREATE", "AI_SERVICE:USE")) {
                assertThat(MenuAuthorization.allows(Set.of(menu), operation)).as(menu + " / " + operation).isTrue();
            }
        }
        assertThat(MenuAuthorization.allows(Set.of("MENU:ASSET_LIBRARY"), "AI_VIDEO_RESULT:DOWNLOAD")).isTrue();
        assertThat(MenuAuthorization.allows(Set.of("MENU:ASSET_LIBRARY"), "PROJECT:DELETE")).isFalse();
        assertThat(MenuAuthorization.allows(Set.of("MENU:ASSET_LIBRARY"), "AI_VIDEO_TASK:CREATE")).isFalse();
        assertThat(MenuAuthorization.allows(Set.of("MENU:PRODUCTION_TASKS"), "AI_VIDEO_TASK:CANCEL")).isTrue();
    }

    @Test
    void defaultMemberMenusDoNotGrantTeamAdministration() {
        assertThat(RbacMenuPermissions.defaultsFor("MEMBER"))
            .contains("MENU:PROJECTS", "MENU:FREE_CREATION")
            .doesNotContain("MENU:TEAM_MANAGEMENT", "MENU:AI_MANAGEMENT", "MENU:DRAMA_LIBRARY");
        assertThat(RbacMenuPermissions.defaultsFor("ADMIN")).hasSize(10);
    }
}
