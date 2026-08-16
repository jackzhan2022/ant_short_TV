package com.antshorttv.rbac;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemberRoleMapper extends BaseMapper<MemberRoleEntity> {

    default List<MemberRoleEntity> selectByMemberId(Long memberId) {
        return selectList(new LambdaQueryWrapper<MemberRoleEntity>().eq(MemberRoleEntity::getMemberId, memberId));
    }

    default MemberRoleEntity selectByMemberIdAndRoleId(Long memberId, Long roleId) {
        return selectOne(new LambdaQueryWrapper<MemberRoleEntity>()
            .eq(MemberRoleEntity::getMemberId, memberId)
            .eq(MemberRoleEntity::getRoleId, roleId));
    }

    default List<MemberRoleEntity> selectByMemberIds(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<MemberRoleEntity>().in(MemberRoleEntity::getMemberId, memberIds));
    }

    default long countByRoleId(Long roleId) {
        return selectCount(new LambdaQueryWrapper<MemberRoleEntity>().eq(MemberRoleEntity::getRoleId, roleId));
    }

    default void deleteByMemberId(Long memberId) {
        delete(new LambdaQueryWrapper<MemberRoleEntity>().eq(MemberRoleEntity::getMemberId, memberId));
    }

    default void deleteByRoleId(Long roleId) {
        delete(new LambdaQueryWrapper<MemberRoleEntity>().eq(MemberRoleEntity::getRoleId, roleId));
    }
}
