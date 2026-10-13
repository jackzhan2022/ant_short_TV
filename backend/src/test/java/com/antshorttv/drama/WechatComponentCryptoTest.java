package com.antshorttv.drama;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import static org.junit.jupiter.api.Assertions.*;

class WechatComponentCryptoTest {
    static final String MASTER = Base64.getEncoder().encodeToString(new byte[32]);
    static final String APP = "wx1234567890abcdef";
    static String encrypt(String xml, String receiver) throws Exception {
        byte[] msg = xml.getBytes(StandardCharsets.UTF_8), id = receiver.getBytes(StandardCharsets.UTF_8);
        int length = 20 + msg.length + id.length; int padding = 32 - length % 32;
        byte[] data = ByteBuffer.allocate(length + padding).put(new byte[16]).putInt(msg.length).put(msg).put(id).array();
        Arrays.fill(data, length, data.length, (byte)padding);
        Cipher c = Cipher.getInstance("AES/CBC/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(new byte[32], "AES"), new IvParameterSpec(new byte[16]));
        return Base64.getEncoder().encodeToString(c.doFinal(data));
    }
    static String signature(String encrypted) throws Exception {
        String[] parts = {"event-token", "1", "nonce", encrypted}; Arrays.sort(parts);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(String.join("", parts).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void gcmIsRandomAuthenticatedAndBoundToCredential() {
        String one = WechatComponentCrypto.seal("secret", MASTER, "ticket");
        assertNotEquals(one, WechatComponentCrypto.seal("secret", MASTER, "ticket"));
        assertEquals("secret", WechatComponentCrypto.open(one, MASTER, "ticket"));
        assertThrows(RuntimeException.class, () -> WechatComponentCrypto.open(one, MASTER, "access"));
        byte[] changed = Base64.getDecoder().decode(one); changed[15] ^= 1;
        assertThrows(RuntimeException.class, () -> WechatComponentCrypto.open(Base64.getEncoder().encodeToString(changed), MASTER, "ticket"));
    }
    @Test void signedInvalidCipherPaddingAndLengthAreRejected() throws Exception {
        for (byte[] plain : List.of(new byte[32], ByteBuffer.allocate(32).put(new byte[16]).putInt(Integer.MAX_VALUE).put(new byte[11]).put((byte)1).array())) {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(new byte[32], "AES"), new IvParameterSpec(new byte[16]));
            String encrypted = Base64.getEncoder().encodeToString(cipher.doFinal(plain));
            assertThrows(RuntimeException.class, () -> WechatComponentCrypto.decrypt(encrypted, signature(encrypted), "1", "nonce", "event-token", MASTER.substring(0,43), APP));
        }
        for (String encrypted : List.of("not-base64", "AA==", "x".repeat(65537)))
            assertThrows(RuntimeException.class, () -> WechatComponentCrypto.decrypt(encrypted, signature(encrypted), "1", "nonce", "event-token", MASTER.substring(0,43), APP));
    }
    @Test void maliciousSignatureReceiverAndXxeAreRejected() throws Exception {
        String xml = "<xml><AppId>" + APP + "</AppId></xml>";
        String encrypted = encrypt(xml, APP);
        assertEquals(xml, WechatComponentCrypto.decrypt(encrypted, signature(encrypted), "1", "nonce", "event-token", MASTER.substring(0,43), APP));
        assertThrows(RuntimeException.class, () -> WechatComponentCrypto.decrypt(encrypted, "0".repeat(40), "1", "nonce", "event-token", MASTER.substring(0,43), APP));
        assertThrows(RuntimeException.class, () -> WechatComponentCrypto.decrypt(encrypted, signature(encrypted), "1", "nonce", "event-token", MASTER.substring(0,43), "wxother"));
        assertThrows(RuntimeException.class, () -> WechatComponentCrypto.xml("<!DOCTYPE xml [<!ENTITY x SYSTEM 'file:///secret'>]><xml><Encrypt>&x;</Encrypt></xml>"));
        assertThrows(RuntimeException.class, () -> WechatComponentCrypto.field(WechatComponentCrypto.xml("<xml><Encrypt>a</Encrypt><Encrypt>b</Encrypt></xml>"), "Encrypt"));
    }
}
