package com.antshorttv.video;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.Map;

public record SubtitleEraseRequest(String method, String quality, Region region) {
    public void validate() {
        if (!"AUTO".equals(method) && !"REGION".equals(method)) throw invalid("请选择正确的字幕擦除方式。");
        if (!"STANDARD".equals(quality) && !"PRO".equals(quality)) throw invalid("请选择正确的处理版本。");
        if ("REGION".equals(method)) {
            if (!"PRO".equals(quality)) throw invalid("指定区域擦除需要使用精细化版。");
            if (region == null) throw invalid("请框选字幕擦除区域。");
            region.validate();
        } else if (region != null) throw invalid("智能擦除无需指定区域。");
    }
    public record Region(double left, double top, double right, double bottom) {
        void validate() {
            if (!Double.isFinite(left) || !Double.isFinite(top) || !Double.isFinite(right) || !Double.isFinite(bottom)
                || left < 0 || top < 0 || right > 1 || bottom > 1 || right <= left || bottom <= top)
                throw invalid("擦除区域必须位于视频画面内，且宽高大于零。");
        }
        Map<String, Double> providerRegion() {
            return Map.of("top_left_x", left, "top_left_y", top, "bottom_right_x", right, "bottom_right_y", bottom);
        }
    }
    private static BusinessException invalid(String message) { return new BusinessException(ErrorCode.VALIDATION_ERROR, message); }
}
