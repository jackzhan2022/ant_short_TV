package com.antshorttv.platform;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PlatformAuthorizationMapper {

    @Select("""
        select distinct role.code
        from platform_user_role user_role
        join platform_role role on role.id = user_role.role_id and role.status = 'ACTIVE'
        where user_role.user_id = #{userId}
        order by role.code
        """)
    List<String> selectRoleCodes(@Param("userId") Long userId);

    @Select("""
        select distinct permission.code
        from platform_user_role user_role
        join platform_role role on role.id = user_role.role_id and role.status = 'ACTIVE'
        join platform_role_permission role_permission on role_permission.role_id = role.id
        join platform_permission permission on permission.id = role_permission.permission_id
        where user_role.user_id = #{userId}
        order by permission.code
        """)
    List<String> selectPermissionCodes(@Param("userId") Long userId);
}
