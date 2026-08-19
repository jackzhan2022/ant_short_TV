package com.antshorttv.points;

import java.util.List;

public record TeamPointTransactionPageResponse(
    List<TeamPointTransactionResponse> records,
    Long total,
    Integer current,
    Integer pageSize
) {
}
