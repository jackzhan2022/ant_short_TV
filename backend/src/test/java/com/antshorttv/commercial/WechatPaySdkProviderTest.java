package com.antshorttv.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WechatPaySdkProviderTest {
    @TempDir Path tempDir;

    @Test
    void disabledPaymentDoesNotCreateSdk() {
        WechatPayProperties properties = new WechatPayProperties();
        WechatPaySdkFactory factory = mock(WechatPaySdkFactory.class);
        WechatPaySdkProvider provider = new WechatPaySdkProvider(properties, factory);

        assertThatThrownBy(provider::get).hasMessageContaining("disabled");
        verifyNoInteractions(factory);
    }

    @Test
    void enabledPaymentCreatesSdkOnlyOnce() throws Exception {
        WechatPayProperties properties = validProperties();
        WechatPaySdk sdk = mock(WechatPaySdk.class);
        WechatPaySdkFactory factory = mock(WechatPaySdkFactory.class);
        when(factory.create(properties)).thenReturn(sdk);
        WechatPaySdkProvider provider = new WechatPaySdkProvider(properties, factory);

        assertThat(provider.get()).isSameAs(sdk);
        assertThat(provider.get()).isSameAs(sdk);
        org.mockito.Mockito.verify(factory).create(properties);
    }

    @Test
    void rejectsMissingMerchantCredentialsWithoutLeakingValues() throws Exception {
        WechatPayProperties properties = validProperties();
        properties.setMerchantId("");
        WechatPaySdkProvider provider = new WechatPaySdkProvider(properties, mock(WechatPaySdkFactory.class));

        assertThatThrownBy(provider::get)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("merchant ID")
            .hasMessageNotContaining(properties.getApiV3Key());
    }

    @Test
    void rejectsApiV3KeyThatIsNot32Bytes() throws Exception {
        WechatPayProperties properties = validProperties();
        properties.setApiV3Key("too-short");
        WechatPaySdkProvider provider = new WechatPaySdkProvider(properties, mock(WechatPaySdkFactory.class));

        assertThatThrownBy(provider::get)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("32 bytes")
            .hasMessageNotContaining("too-short");
    }

    @Test
    void rejectsMerchantKeyThatIsNotPkcs8Pem() throws Exception {
        WechatPayProperties properties = validProperties();
        Path invalidKey = tempDir.resolve("invalid-key.pem");
        Files.writeString(invalidKey, "-----BEGIN RSA PRIVATE KEY-----\ninvalid\n-----END RSA PRIVATE KEY-----\n");
        properties.setMerchantPrivateKeyPath(invalidKey.toString());
        WechatPaySdkProvider provider = new WechatPaySdkProvider(properties, mock(WechatPaySdkFactory.class));

        assertThatThrownBy(provider::get)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PKCS#8");
    }

    private WechatPayProperties validProperties() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
            .encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        Path privateKey = tempDir.resolve("apiclient_key.pem");
        Files.writeString(privateKey, "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n");

        WechatPayProperties properties = new WechatPayProperties();
        properties.setEnabled(true);
        properties.setMerchantId("merchant-1001");
        properties.setMerchantSerialNumber("ABCDEF123456");
        properties.setMerchantPrivateKeyPath(privateKey.toString());
        properties.setApiV3Key("0123456789abcdef0123456789abcdef");
        return properties;
    }
}
