package com.antshorttv.accounting;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AiUsageExtractorTest {
    @Test
    void rejectsNegativeProviderOutputInsteadOfCreatingNegativeCost() {
        assertThatThrownBy(() -> new AiUsageExtractor().providerTokens(
            new AiUsageContext(1L,2L,3L,4L,5L),100,-1,LocalDateTime.now()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
