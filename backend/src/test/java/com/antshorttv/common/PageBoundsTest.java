package com.antshorttv.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class PageBoundsTest {
    @Test
    void defaultsAndNormalizesInvalidValues() {
        assertEquals(new PageBounds(1, 20), PageBounds.of(null, null));
        assertEquals(new PageBounds(1, 20), PageBounds.of(0, -1));
        assertEquals(new PageBounds(3, 100), PageBounds.of(3, 101));
    }

    @Test
    void computesOffsetWithoutIntegerOverflow() {
        assertEquals(214748364600L, PageBounds.of(Integer.MAX_VALUE, 100).offset());
        assertEquals(40L, PageBounds.of(3, 20).offset());
    }

    public static void main(String[] args) {
        PageBoundsTest test = new PageBoundsTest();
        test.defaultsAndNormalizesInvalidValues();
        test.computesOffsetWithoutIntegerOverflow();
    }
}
