package com.antshorttv.rbac;

import java.util.List;

public record UpdateMemberRolesRequest(
    List<Long> roleIds
) {
}
