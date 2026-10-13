package com.antshorttv.drama;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

final class WechatComponentCrypto {
    private WechatComponentCrypto() {}
    static String seal(String value, String master, String context) {
        try {
            byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key(master), new GCMParameterSpec(128, iv));
            c.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = c.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + encrypted.length).put(iv).put(encrypted).array());
        } catch (Exception e) { throw rejected(); }
    }
    static String open(String value, String master, String context) {
        try {
            byte[] data = Base64.getDecoder().decode(value);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(master), new GCMParameterSpec(128, Arrays.copyOf(data, 12)));
            c.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new String(c.doFinal(Arrays.copyOfRange(data, 12, data.length)), StandardCharsets.UTF_8);
        } catch (Exception e) { throw rejected(); }
    }
    static SecretKeySpec key(String master) {
        byte[] key = Base64.getDecoder().decode(master);
        if (key.length != 32) throw rejected();
        return new SecretKeySpec(key, "AES");
    }
    static Document xml(String text) {
        if (text == null || text.length() > 65536) throw rejected();
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            f.setXIncludeAware(false); f.setExpandEntityReferences(false);
            var builder = f.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            });
            Document d = builder.parse(new InputSource(new StringReader(text)));
            if (!"xml".equals(d.getDocumentElement().getTagName())) throw rejected();
            return d;
        } catch (Exception e) { throw rejected(); }
    }
    static String field(Document d, String name) {
        var nodes = d.getElementsByTagName(name);
        if (nodes.getLength() != 1 || nodes.item(0).getParentNode() != d.getDocumentElement()) throw rejected();
        return nodes.item(0).getTextContent();
    }
    static void verify(String signature, String timestamp, String nonce, String token, String encrypted) {
        try {
            if (token == null || token.isBlank() || signature == null || !signature.matches("[0-9a-f]{40}")
                || timestamp == null || !timestamp.matches("[0-9]{1,20}") || nonce == null || nonce.isBlank()
                || nonce.length() > 256 || encrypted != null && encrypted.length() > 65536) throw rejected();
            String[] parts = encrypted == null ? new String[]{token, timestamp, nonce} : new String[]{token, timestamp, nonce, encrypted};
            Arrays.sort(parts);
            byte[] expected = MessageDigest.getInstance("SHA-1").digest(String.join("", parts).getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature))) throw rejected();
        } catch (Exception e) { throw rejected(); }
    }
    static String decrypt(String encrypted, String signature, String timestamp, String nonce,
                          String token, String aes, String appid) {
        try {
            if (encrypted == null || encrypted.isBlank()) throw rejected();
            verify(signature, timestamp, nonce, token, encrypted);
            if (aes == null || aes.length() != 43) throw rejected();
            byte[] key = Base64.getDecoder().decode(aes + "=");
            if (key.length != 32) throw rejected();
            Cipher c = Cipher.getInstance("AES/CBC/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(Arrays.copyOf(key, 16)));
            byte[] plain = c.doFinal(Base64.getDecoder().decode(encrypted));
            int padding = plain[plain.length - 1] & 255;
            if (padding < 1 || padding > 32 || plain.length < padding + 20) throw rejected();
            for (int i = plain.length - padding; i < plain.length; i++) if ((plain[i] & 255) != padding) throw rejected();
            int length = ByteBuffer.wrap(plain, 16, 4).getInt();
            if (length < 0 || length > plain.length - padding - 20) throw rejected();
            String receiver = new String(plain, 20 + length, plain.length - padding - 20 - length, StandardCharsets.UTF_8);
            if (!appid.equals(receiver)) throw rejected();
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(plain, 20, length)).toString();
        } catch (Exception e) { throw rejected(); }
    }
    static com.antshorttv.common.BusinessException rejected() {
        return DramaLibraryService.invalid("微信授权数据无效。");
    }
}
