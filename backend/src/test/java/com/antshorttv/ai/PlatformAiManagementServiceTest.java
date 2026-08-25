package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antshorttv.authsession.AuthenticatedUser;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.security.CurrentPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PlatformAiManagementServiceTest {

    private final AiProviderMapper providerMapper = mock(AiProviderMapper.class);
    private final AiProviderConfigMapper configMapper = mock(AiProviderConfigMapper.class);
    private final AiModelMapper modelMapper = mock(AiModelMapper.class);
    private final AiModelCapabilityMapper capabilityMapper = mock(AiModelCapabilityMapper.class);
    private final AiSecretCodec secretCodec = new AiSecretCodec("test-ai-secret-key");
    private final OperationLogService operationLogService = mock(OperationLogService.class);
    private final AiModelRouter router = mock(AiModelRouter.class);
    private final CurrentPrincipal currentPrincipal = mock(CurrentPrincipal.class);
    private final PlatformAiManagementService service = new PlatformAiManagementService(
        providerMapper,
        configMapper,
        modelMapper,
        capabilityMapper,
        secretCodec,
        operationLogService,
        router,
        currentPrincipal
    );

    @Test
    void providerTestSendsCredentialEndpointAndModelRequest() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = server(200, authorization, body);
        try {
            AiModelRoute route = route("http://127.0.0.1:%d/v1".formatted(server.getAddress().getPort()));
            prepare(route);

            AiServiceTestResponse response = service.testProvider(1L, null);

            assertThat(response.status()).isEqualTo("SUCCESS");
            assertThat(authorization.get()).isEqualTo("Bearer sk-real-platform-test");
            assertThat(body.get()).contains("\"model\":\"platform-connectivity-model\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void providerTestRecordsAuthenticationFailure() throws Exception {
        HttpServer server = server(401, new AtomicReference<>(), new AtomicReference<>());
        try {
            AiModelRoute route = route("http://127.0.0.1:%d/v1".formatted(server.getAddress().getPort()));
            prepare(route);

            AiServiceTestResponse response = service.testProvider(1L, null);

            assertThat(response.status()).isEqualTo("FAILED");
            assertThat(response.message()).contains("HTTP 401");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void providerTestFailsWhenProviderHasNoEnabledModelCapability() {
        preparePrincipalAndProvider();
        when(modelMapper.selectList(any())).thenReturn(List.of());

        AiServiceTestResponse response = service.testProvider(1L, null);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.message()).contains("启用的模型能力");
    }

    private void prepare(AiModelRoute route) {
        preparePrincipalAndProvider();
        when(configMapper.selectOne(any())).thenReturn(route.providerConfig());
        when(modelMapper.selectList(any())).thenReturn(List.of(route.model()));
        AiModelCapabilityEntity capability = new AiModelCapabilityEntity();
        capability.setModelId(route.model().getId());
        capability.setCapability("TEXT_GENERATION");
        capability.setStatus("ENABLED");
        when(capabilityMapper.selectList(any())).thenReturn(List.of(capability));
        when(router.route(route.model().getId(), route.model().getServiceType())).thenReturn(route);
    }

    private void preparePrincipalAndProvider() {
        when(currentPrincipal.require()).thenReturn(new AuthenticatedUser(9L, "13800000009", "session", LocalDateTime.now().plusHours(1)));
        when(providerMapper.selectById(1L)).thenReturn(provider());
        when(configMapper.selectOne(any())).thenReturn(config("http://127.0.0.1:1/v1"));
    }

    private AiModelRoute route(String baseUrl) {
        AiModelEntity model = new AiModelEntity();
        model.setId(10L);
        model.setProviderId(1L);
        model.setModelCode("platform-connectivity-model");
        model.setServiceType("TEXT");
        model.setStatus("ENABLED");
        AiProviderConfigEntity config = config(baseUrl);
        return new AiModelRoute(model, provider(), config, new OpenAiAdapter(secretCodec, new ObjectMapper()));
    }

    private AiProviderEntity provider() {
        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(1L);
        provider.setCode("OpenAI");
        provider.setStatus("ENABLED");
        return provider;
    }

    private AiProviderConfigEntity config(String baseUrl) {
        AiProviderConfigEntity config = new AiProviderConfigEntity();
        config.setId(2L);
        config.setProviderId(1L);
        config.setBaseUrl(baseUrl);
        config.setApiKeyCipher(secretCodec.encrypt("sk-real-platform-test"));
        config.setStatus("ENABLED");
        return config;
    }

    private HttpServer server(int status, AtomicReference<String> authorization, AtomicReference<String> body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = (status == 200
                ? "{\"id\":\"connectivity-test\",\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"
                : "{\"error\":\"invalid credential\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }
}
