package com.antshorttv.workflowagent.tool;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class EpisodeScriptCurrentSelector {
    private final JdbcTemplate jdbc;

    public EpisodeScriptCurrentSelector(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void selectCurrent(
        long tenantId,
        long projectId,
        long episodeId,
        long versionId,
        String content
    ) {
        int changed = jdbc.update("""
            update script_episode
               set content = ?, content_fingerprint = ?, updated_at = now()
             where tenant_id = ? and project_id = ? and id = ?
               and exists (
                 select 1 from script_episode_version version
                  where version.id = ? and version.episode_id = script_episode.id
                    and version.is_current = true)
            """, content, sha256(content), tenantId, projectId, episodeId, versionId);
        if (changed != 1) {
            throw new IllegalStateException("Episode script version could not become current");
        }
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
