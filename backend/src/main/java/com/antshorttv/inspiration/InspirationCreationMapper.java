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
        return selectImageRenditionCandidatesAfter(0L, limit);
    }

    default List<InspirationCreationEntity> selectImageRenditionCandidatesAfter(long afterId, int limit) {
        int bounded = Math.max(1, Math.min(limit, 100));
        return selectList(new LambdaQueryWrapper<InspirationCreationEntity>()
            .in(InspirationCreationEntity::getCreationType, "IMAGE", "VIDEO")
            .and(query -> query.eq(InspirationCreationEntity::getImportStatus, "PROCESSING")
                .eq(InspirationCreationEntity::getThumbnailStatus, "PENDING")
                .or(failed -> failed.eq(InspirationCreationEntity::getImportStatus, "FAILED")
                    .eq(InspirationCreationEntity::getThumbnailStatus, "FAILED")))
            .isNull(InspirationCreationEntity::getDeletedAt)
            .isNotNull(InspirationCreationEntity::getStoragePath)
            .ne(InspirationCreationEntity::getStoragePath, "")
            .isNotNull(InspirationCreationEntity::getThumbnailPath)
            .ne(InspirationCreationEntity::getThumbnailPath, "")
            .gt(InspirationCreationEntity::getId, afterId)
            .orderByAsc(InspirationCreationEntity::getId)
            .last("limit " + bounded));
    }

    @Update("""
        update inspiration_creation
           set thumbnail_path = #{path}, thumbnail_mime_type = #{mimeType},
               thumbnail_file_size = #{fileSize}, thumbnail_status = 'READY',
               thumbnail_error = null, import_status = 'IMPORTED', import_error = null,
               updated_at = current_timestamp
         where id = #{id} and creation_type in ('IMAGE', 'VIDEO')
           and import_status in ('PROCESSING', 'FAILED')
           and thumbnail_status in ('PENDING', 'FAILED')
           and storage_path is not null and trim(storage_path) <> '' and file_size > 0
           and thumbnail_path is not null and trim(thumbnail_path) <> ''
           and thumbnail_path = #{path}
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
           set thumbnail_status = 'PENDING', thumbnail_error = null,
               import_status = 'PROCESSING', import_error = null,
               updated_at = current_timestamp
         where id = #{id} and creation_type in ('IMAGE', 'VIDEO')
           and import_status = 'FAILED' and thumbnail_status = 'FAILED'
           and storage_path is not null and trim(storage_path) <> '' and file_size > 0
           and thumbnail_path is not null and trim(thumbnail_path) <> ''
           and thumbnail_path = #{path} and deleted_at is null
        """)
    int markImageRenditionPending(@Param("id") Long id, @Param("path") String path);

    @Update("""
        update inspiration_creation
           set import_error = null, thumbnail_error = null
         where id = #{id} and creation_type in ('IMAGE', 'VIDEO') and deleted_at is null
           and thumbnail_path = #{path}
           and ((import_status = 'PROCESSING' and thumbnail_status = 'PENDING')
             or (import_status = 'IMPORTED' and thumbnail_status = 'READY'))
        """)
    int clearImageRenditionErrors(@Param("id") Long id, @Param("path") String path);

    @Update("""
        update inspiration_creation
           set publish_status = #{status}, updated_at = current_timestamp
         where id = #{id} and deleted_at is null
        """)
    int updatePublishStatusIfActive(@Param("id") Long id, @Param("status") String status);

    @Update("""
        update inspiration_creation
           set thumbnail_status = 'FAILED', thumbnail_error = #{error},
               import_status = 'FAILED', import_error = #{error},
               updated_at = current_timestamp
         where id = #{id} and creation_type in ('IMAGE', 'VIDEO')
           and import_status = 'PROCESSING' and thumbnail_status = 'PENDING'
           and deleted_at is null
        """)
    int markImageRenditionFailed(
        @Param("id") Long id,
        @Param("error") String error
    );

    @Update("""
        update inspiration_creation
           set storage_path = #{media.storagePath}, mime_type = #{media.mimeType},
               file_size = #{media.fileSize}, url = #{media.url}, creation_type = #{media.creationType},
               thumbnail_path = #{media.thumbnailPath}, thumbnail_url = #{media.thumbnailUrl},
               thumbnail_mime_type = case when thumbnail_status = 'READY' then thumbnail_mime_type else #{media.thumbnailMimeType} end,
               thumbnail_file_size = case when thumbnail_status = 'READY' then thumbnail_file_size else #{media.thumbnailFileSize} end,
               thumbnail_status = case when thumbnail_status = 'READY' then 'READY' else #{media.thumbnailStatus} end,
               import_status = case when thumbnail_status = 'READY' then 'IMPORTED' else #{media.importStatus} end,
               thumbnail_error = null, import_error = null, updated_at = current_timestamp
         where id = #{media.id} and external_id = #{media.externalId} and deleted_at is null
           and import_status in ('PROCESSING', 'FAILED', 'IMPORTED')
           and (storage_path is null or trim(storage_path) = '' or storage_path = #{media.storagePath})
           and (thumbnail_path is null or trim(thumbnail_path) = '' or thumbnail_path = #{media.thumbnailPath})
        """)
    int attachMediaIfActive(@Param("media") InspirationCreationEntity media);

    @Update("""
        update inspiration_creation
           set external_task_id = #{media.externalTaskId}, task_type = #{media.taskType},
               title = #{media.title}, author_name = #{media.authorName},
               source_created_at = #{media.sourceCreatedAt}, source_updated_at = #{media.sourceUpdatedAt},
               sort_order = #{media.sortOrder}, detail_json = #{media.detailJson}, updated_at = current_timestamp
         where id = #{media.id} and deleted_at is null
        """)
    int updateImportMetadataIfActive(@Param("media") InspirationCreationEntity media);
}
