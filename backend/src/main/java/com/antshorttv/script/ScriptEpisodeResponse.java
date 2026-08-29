package com.antshorttv.script;

public record ScriptEpisodeResponse(
    Long episodeId,
    Integer episodeNo,
    String title,
    String content,
    String summary
) {
    public ScriptEpisodeResponse(Integer episodeNo, String title, String content) {
        this(null, episodeNo, title, content, null);
    }
}
