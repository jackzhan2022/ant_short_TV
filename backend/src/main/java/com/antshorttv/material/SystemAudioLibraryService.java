package com.antshorttv.material;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.common.MediaPage;
import com.antshorttv.common.PageBounds;
import com.antshorttv.security.CurrentPrincipal;
import com.antshorttv.storage.DeliveryGrant;
import com.antshorttv.storage.DeliveryGrantRequest;
import com.antshorttv.storage.MediaDeliveryGrantService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemAudioLibraryService {
    private final JdbcTemplate jdbc;
    private final CurrentPrincipal principal;
    private final MediaDeliveryGrantService grants;

    public SystemAudioLibraryService(JdbcTemplate jdbc, CurrentPrincipal principal, MediaDeliveryGrantService grants) {
        this.jdbc = jdbc; this.principal = principal; this.grants = grants;
    }

    @Transactional(readOnly = true)
    public MediaPage<Audio> list(String keyword, String gender, String ageGroup, Integer current, Integer pageSize) {
        principal.require();
        var bounds = PageBounds.of(current, pageSize);
        StringBuilder where = new StringBuilder(" where status='ACTIVE'");
        List<Object> arguments = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" and (name like ? or category like ? or source_path like ?)");
            String value = "%" + keyword.trim() + "%";
            arguments.add(value); arguments.add(value); arguments.add(value);
        }
        if (gender != null && !gender.isBlank()) { where.append(" and gender=?"); arguments.add(gender); }
        if (ageGroup != null && !ageGroup.isBlank()) { where.append(" and age_group=?"); arguments.add(ageGroup); }
        long total = jdbc.queryForObject("select count(*) from system_audio_asset" + where, Long.class, arguments.toArray());
        arguments.add(bounds.pageSize()); arguments.add(bounds.offset());
        var items = jdbc.query("select id,name,category,gender,age_group,mime_type,file_size,duration_seconds from system_audio_asset"
            + where + " order by id limit ? offset ?", (rs, row) -> new Audio(rs.getLong("id"), rs.getString("name"),
                rs.getString("category"), rs.getString("gender"), rs.getString("age_group"), rs.getString("mime_type"),
                rs.getLong("file_size"), rs.getBigDecimal("duration_seconds"), playbackPath(rs.getLong("id"))), arguments.toArray());
        return new MediaPage<>(items, bounds.current(), bounds.pageSize(), total);
    }

    public DeliveryGrant playback(Long id) {
        var user = principal.require();
        var rows = jdbc.queryForList("select storage_path from system_audio_asset where id=? and status='ACTIVE'", String.class, id);
        if (rows.isEmpty() || !validStoragePath(rows.get(0))) throw new BusinessException(ErrorCode.NOT_FOUND, "音频不存在或已失效。");
        return grants.issue(new DeliveryGrantRequest(0L, null, user.userId(), "SYSTEM_AUDIO_ASSET", id,
            "v1", "AUDIO", rows.get(0), false));
    }

    static boolean validStoragePath(String key) {
        return key != null && key.matches("materials/0/system_audio/[0-9]{6}/[a-f0-9]{64}/v1/original\\.(mp3|wav)");
    }

    public static String playbackPath(Long id) { return "/api/system-audio/" + id + "/playback"; }

    public record Audio(Long id, String name, String category, String gender, String ageGroup,
        String mimeType, Long fileSize, BigDecimal durationSeconds, String previewUrl) {}
}
