package com.antshorttv.accounting;

public record AiResolvedPrice(
    AiModelPriceVersionEntity version,
    AiModelPriceComponentEntity component
) {
}
