package com.antshorttv.ai;

import java.util.List;

public record AiImageRequest(
    String prompt,
    String negativePrompt,
    String size,
    String aspectRatio,
    Integer count,
    List<String> referenceImages,
    String quality,
    String outputFormat
) {
    public AiImageRequest(String prompt,String negativePrompt,String size,String aspectRatio,Integer count,List<String> referenceImages) {
        this(prompt,negativePrompt,size,aspectRatio,count,referenceImages,null,null);
    }
}
