package com.antshorttv.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void successWrapsDataWithAntDesignProShape() {
        ApiResponse<String> response = ApiResponse.success("ok");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("ok");
        assertThat(response.errorCode()).isNull();
        assertThat(response.errorMessage()).isNull();
    }
}
