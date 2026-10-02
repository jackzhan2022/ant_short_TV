package com.antshorttv.storage;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface FailedDisplayOutputCleanupMapper {
    String CANDIDATE_ROWS = """
        from media_processing_job j
        join media_object m on m.id = j.media_object_id
         where j.status = 'FAILED' and j.operation = 'DISPLAY_IMAGE_SLIM'
           and j.completed_at <= #{cutoff} and j.attempt_no > 0
           and m.status = 'FAILED' and m.rendition_type = 'DISPLAY_IMAGE_SLIM'
           and m.object_key = j.output_key
           and not exists (
               select 1 from media_processing_output_cleanup c
                where c.job_id = j.id and c.attempt_no = j.attempt_no
           )
        """;

    @Select("select coalesce(max(j.id), 0) " + CANDIDATE_ROWS)
    long latestCandidateId(@Param("cutoff") LocalDateTime cutoff);

    @Select("select j.id " + CANDIDATE_ROWS + """
           and j.id > #{afterId} and j.id <= #{upperId}
         order by j.id limit #{limit}
        """)
    List<Long> candidatesAfter(
        @Param("afterId") long afterId,
        @Param("upperId") long upperId,
        @Param("cutoff") LocalDateTime cutoff,
        @Param("limit") int limit
    );

    @Select("select * from media_processing_job where id = #{jobId} for update")
    MediaProcessingJobEntity jobForUpdate(@Param("jobId") Long jobId);

    @Select("select * from media_object where id = #{mediaId}")
    MediaObjectEntity media(@Param("mediaId") Long mediaId);

    @Select("""
        select * from media_object where object_key = #{inputKey} and rendition_type = 'ORIGINAL'
        """)
    MediaObjectEntity original(@Param("inputKey") String inputKey);

    @Select("""
        select count(*) from media_processing_output_cleanup
         where job_id = #{jobId} and attempt_no = #{attemptNo}
        """)
    int cleaned(@Param("jobId") Long jobId, @Param("attemptNo") int attemptNo);

    @Insert("""
        insert into media_processing_output_cleanup (job_id, attempt_no, cleaned_at)
        values (#{jobId}, #{attemptNo}, current_timestamp)
        """)
    int markCleaned(@Param("jobId") Long jobId, @Param("attemptNo") int attemptNo);
}
