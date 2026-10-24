package com.antshorttv.freecreation;

import com.antshorttv.storage.ImageDisplayRenditionService;
import com.antshorttv.storage.MediaObjectIdentity;
import com.antshorttv.storage.SensitiveValueRedactor;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class FreeCreationThumbnailBackfill {
    private static final Logger LOG = LoggerFactory.getLogger(FreeCreationThumbnailBackfill.class);
    private final JdbcTemplate jdbc;
    private final ImageDisplayRenditionService images;

    public FreeCreationThumbnailBackfill(JdbcTemplate jdbc, ImageDisplayRenditionService images) {
        this.jdbc = jdbc;
        this.images = images;
    }

    @Scheduled(initialDelayString = "${media.free-creation-thumbnail.initial-delay-ms:10000}",
        fixedDelayString = "${media.free-creation-thumbnail.fixed-delay-ms:30000}")
    public synchronized void submitMissing() {
        LocalDateTime now = LocalDateTime.now();
        var candidates = jdbc.query("""
            select o.tenant_id,o.project_id,o.asset_type,o.asset_id,o.version_id
              from media_object o
              left join media_object t on t.tenant_id=o.tenant_id and t.project_id is null
                and t.asset_type=o.asset_type and t.asset_id=o.asset_id and t.version_id=o.version_id
                and t.rendition_type='LIST_THUMBNAIL'
              left join media_processing_job j on j.media_object_id=t.id and j.operation='LIST_THUMBNAIL'
             where o.project_id is null and o.rendition_type='ORIGINAL' and o.status='READY'
               and o.asset_type in ('FREE_CREATION_OUTPUT','FREE_CREATION_VIDEO_COVER')
               and o.mime_type like 'image/%'
               and (t.id is null
                 or (t.status='PENDING' and (j.id is null or (j.status in ('SUBMITTING','PENDING') and j.updated_at<?)))
                 or (t.status='FAILED' and j.attempt_no<3 and j.updated_at<?))
             order by o.id limit 10
            """, (rs, row) -> new MediaObjectIdentity(rs.getLong("tenant_id"), null,
                rs.getString("asset_type"), rs.getLong("asset_id"), rs.getString("version_id")),
            now.minusMinutes(5), now.minusMinutes(10));
        for (var identity : candidates) {
            try {
                images.submitThumbnail(identity);
            } catch (RuntimeException failure) {
                LOG.warn("Free creation thumbnail submission failed for {}: {}", identity.assetId(),
                    SensitiveValueRedactor.redact(failure.getMessage()));
            }
        }
    }
}
