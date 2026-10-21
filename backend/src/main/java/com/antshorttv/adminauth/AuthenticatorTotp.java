package com.antshorttv.adminauth;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238: SHA-1, six digits, 30-second steps, one-step clock tolerance. */
public final class AuthenticatorTotp {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private AuthenticatorTotp() {}

    static String newSecret() {
        byte[] bytes = new byte[20];
        new SecureRandom().nextBytes(bytes);
        StringBuilder encoded = new StringBuilder(32);
        int buffer = 0, bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 255);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                encoded.append(ALPHABET.charAt((buffer >>> bits) & 31));
            }
        }
        return encoded.toString();
    }

    public static String code(String secret, long step) {
        byte[] key = new byte[secret.length() * 5 / 8];
        int buffer = 0, bits = 0, index = 0;
        for (char character : secret.toCharArray()) {
            int value = ALPHABET.indexOf(character);
            if (value < 0) throw new IllegalArgumentException("Invalid authenticator secret");
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) { bits -= 8; key[index++] = (byte) (buffer >>> bits); }
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 15;
            int binary = ByteBuffer.wrap(hash, offset, 4).getInt() & 0x7fffffff;
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Authenticator algorithm unavailable", exception);
        }
    }

    static long match(String secret, String input, Instant now, long lastUsedStep) {
        if (input == null || !input.matches("[0-9]{6}")) return -1;
        long current = now.getEpochSecond() / 30;
        for (long step : new long[]{current, current - 1, current + 1}) {
            if (step > lastUsedStep && MessageDigest.isEqual(
                code(secret, step).getBytes(StandardCharsets.US_ASCII), input.getBytes(StandardCharsets.US_ASCII))) return step;
        }
        return -1;
    }
}
