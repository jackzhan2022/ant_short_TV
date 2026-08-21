package com.antshorttv.video;

public class VideoAnalysisParseException extends RuntimeException {
    public VideoAnalysisParseException(String message) {
        super(message);
    }

    public VideoAnalysisParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
