package com.antshorttv.adminauth;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthenticatorTotpTest {
    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test void matchesRfc6238Sha1VectorsWithSixDigits() {
        long[] times = {59, 1111111109L, 1111111111L, 1234567890L, 2000000000L, 20000000000L};
        String[] codes = {"287082", "081804", "050471", "005924", "279037", "353130"};
        for (int i = 0; i < times.length; i++) {
            assertThat(AuthenticatorTotp.code(SECRET, times[i] / 30)).isEqualTo(codes[i]);
        }
    }

    @Test void rejectsMalformedExpiredAndReplayedCodes() {
        Instant now = Instant.ofEpochSecond(1234567890L);
        long step = now.getEpochSecond() / 30;
        assertThat(AuthenticatorTotp.match(SECRET, "005924", now, -1)).isEqualTo(step);
        assertThat(AuthenticatorTotp.match(SECRET, "005924", now, step)).isEqualTo(-1);
        assertThat(AuthenticatorTotp.match(SECRET, "005924", now.plusSeconds(90), -1)).isEqualTo(-1);
        assertThat(AuthenticatorTotp.match(SECRET, "x05924", now, -1)).isEqualTo(-1);
        assertThat(AuthenticatorTotp.match(SECRET, null, now, -1)).isEqualTo(-1);
        assertThat(AuthenticatorTotp.match(SECRET, AuthenticatorTotp.code(SECRET, step - 1), now, -1)).isEqualTo(step - 1);
    }
}
