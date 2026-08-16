package com.antshorttv.rbac;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PermissionMapper extends BaseMapper<PermissionEntity> {

    default PermissionEntity selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<PermissionEntity>().eq(PermissionEntity::getCode, code));
    }

    default List<PermissionEntity> selectByCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<PermissionEntity>().in(PermissionEntity::getCode, codes));
    }
}
