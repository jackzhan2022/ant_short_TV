package com.antshorttv.video;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SubtitleEraseRequestTest {
    @Test void acceptsOnlyFiniteOrderedNormalizedRegionsWithProQuality() {
        new SubtitleEraseRequest("REGION","PRO",new SubtitleEraseRequest.Region(.1,.1,.9,.4)).validate();
        for(var region:new SubtitleEraseRequest.Region[]{new SubtitleEraseRequest.Region(-.1,0,.9,.4),
            new SubtitleEraseRequest.Region(0,0,1.01,1),new SubtitleEraseRequest.Region(.9,.1,.1,.8),
            new SubtitleEraseRequest.Region(.1,.5,.8,.5),new SubtitleEraseRequest.Region(Double.NaN,0,1,1)}) {
            assertThatThrownBy(()->new SubtitleEraseRequest("REGION","PRO",region).validate()).hasMessageContaining("区域");
        }
        assertThatThrownBy(()->new SubtitleEraseRequest("REGION","STANDARD",new SubtitleEraseRequest.Region(0,0,1,1)).validate())
            .hasMessageContaining("精细化");
        assertThatThrownBy(()->new SubtitleEraseRequest("REGION","PRO",null).validate()).hasMessageContaining("区域");
        assertThatThrownBy(()->new SubtitleEraseRequest("AUTO","UNKNOWN",null).validate()).hasMessageContaining("版本");
    }
}
