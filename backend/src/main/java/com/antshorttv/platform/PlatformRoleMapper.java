package com.antshorttv.platform;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlatformRoleMapper extends BaseMapper<PlatformRoleEntity> {
    default PlatformRoleEntity selectActiveByCode(String code) {
        return selectOne(new LambdaQueryWrapper<PlatformRoleEntity>()
            .eq(PlatformRoleEntity::getCode, code)
            .eq(PlatformRoleEntity::getStatus, "ACTIVE"));
    }
}
