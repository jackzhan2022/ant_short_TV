package com.antshorttv.style;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface StyleLibraryMapper extends BaseMapper<StyleLibraryEntity> {
    @Select("select category from style_library where is_public = true and category is not null group by category order by min(sort_order), category")
    List<String> selectPublicCategories();
}
