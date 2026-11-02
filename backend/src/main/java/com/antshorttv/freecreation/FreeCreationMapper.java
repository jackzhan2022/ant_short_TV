package com.antshorttv.freecreation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface FreeCreationMapper extends BaseMapper<FreeCreationEntity> {
    // Provider results can contain multi-megabyte base64 images. Presentation never needs them.
    static QueryWrapper<FreeCreationEntity> presentationQuery() {
        return new QueryWrapper<FreeCreationEntity>().select("id","tenant_id","creator_id","execution_id",
            "media_type","model_id","model_name","provider_code","prompt","parameters_json",
            "avatar_recovery_source_creation_id","billing_call_log_id","provider_started_at","created_at");
    }
    default FreeCreationEntity selectForPresentation(Long id) {
        return selectOne(presentationQuery().eq("id",id));
    }
    default List<FreeCreationEntity> selectForPresentation(Long tenantId,List<Long> ids) {
        return ids.isEmpty()?List.of():selectList(presentationQuery().eq("tenant_id",tenantId).in("id",ids));
    }
}
