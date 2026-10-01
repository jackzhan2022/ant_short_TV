package com.antshorttv.inspiration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InspirationCreationMapper extends BaseMapper<InspirationCreationEntity> {
    default InspirationCreationEntity selectByExternalId(String externalId) {
        return selectOne(new LambdaQueryWrapper<InspirationCreationEntity>()
            .eq(InspirationCreationEntity::getExternalId, externalId)
            .last("limit 1"));
    }

    default InspirationCreationEntity selectImportedById(Long id) {
        return selectOne(new LambdaQueryWrapper<InspirationCreationEntity>()
            .eq(InspirationCreationEntity::getId, id)
            .eq(InspirationCreationEntity::getImportStatus, InspirationCreationImportStatus.IMPORTED.name())
            .eq(InspirationCreationEntity::getPublishStatus, "PUBLISHED")
            .isNull(InspirationCreationEntity::getDeletedAt)
            .last("limit 1"));
    }

    default List<InspirationCreationEntity> selectThumbnailBackfillCandidates(int limit) {
        return selectList(new LambdaQueryWrapper<InspirationCreationEntity>()
            .eq(InspirationCreationEntity::getImportStatus, InspirationCreationImportStatus.IMPORTED.name())
            .isNull(InspirationCreationEntity::getDeletedAt)
            .and(query -> query.isNull(InspirationCreationEntity::getThumbnailStatus)
                .or()
                .ne(InspirationCreationEntity::getThumbnailStatus, "READY"))
            .orderByAsc(InspirationCreationEntity::getId)
            .last("limit %d".formatted(limit)));
    }

    default List<InspirationCreationEntity> selectImageRenditionCandidates(int limit) {
        int bounded = Math.max(1, Math.min(limit, 100));
        return selectList(new LambdaQueryWrapper<InspirationCreationEntity>()
            .eq(InspirationCreationEntity::getCreationType, "IMAGE")
            .eq(InspirationCreationEntity::getImportStatus,
                InspirationCreationImportStatus.PROCESSING.name())
            .eq(InspirationCreationEntity::getThumbnailStatus, "PENDING")
            .isNull(InspirationCreationEntity::getDeletedAt)
            .orderByAsc(InspirationCreationEntity::getId)
            .last("limit " + bounded));
    }

    @Update("""
        update inspiration_creation
           set thumbnail_path = #{path}, thumbnail_mime_type = #{mimeType},
               thumbnail_file_size = #{fileSize}, thumbnail_status = 'READY',
               thumbnail_error = null, import_status = 'IMPORTED', import_error = null,
               updated_at = current_timestamp
         where id = #{id} and creation_type = 'IMAGE'
           and import_status = 'PROCESSING' and thumbnail_status = 'PENDING'
           and deleted_at is null
        """)
    int markImageRenditionReady(
        @Param("id") Long id,
        @Param("path") String path,
        @Param("mimeType") String mimeType,
        @Param("fileSize") long fileSize
    );

    @Update("""
        update inspiration_creation
           set thumbnail_status = 'FAILED', thumbnail_error = #{error},
               import_status = 'FAILED', import_error = #{error},
               updated_at = current_timestamp
         where id = #{id} and creation_type = 'IMAGE'
           and import_status = 'PROCESSING' and thumbnail_status = 'PENDING'
           and deleted_at is null
        """)
    int markImageRenditionFailed(
        @Param("id") Long id,
        @Param("error") String error
    );
}
