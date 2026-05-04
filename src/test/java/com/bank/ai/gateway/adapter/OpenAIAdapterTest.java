package com.bank.ai.gateway.adapter;

import com.bank.ai.gateway.model.dto.request.ChatRequest;
import com.bank.ai.gateway.model.dto.response.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAIAdapter 单元测试
 *
 * @since 1.0.0
 */
@DisplayName("OpenAIAdapter")
class OpenAIAdapterTest {

    private MockWebServer mockServer;
    private OpenAIAdapter adapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();

        objectMapper = new ObjectMapper();
        WebClient.Builder webClientBuilder = WebClient.builder();
        adapter = new OpenAIAdapter(objectMapper, webClientBuilder);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockServer.shutdown();
    }

    @Nested
    @DisplayName("getProvider")
    class GetProviderTest {

        @Test
        @DisplayName("返回 OPENAI")
        void returnsOpenAI() {
            assertEquals("OPENAI", adapter.getProvider());
        }
    }

    @Nested
    @DisplayName("chat")
    class ChatTest {

        @Test
        @DisplayName("非流式调用成功")
        void chat_success() throws Exception {
            // Arrange
            String mockResponse = """
                {
                  "id": "chatcmpl-123",
                  "object": "chat.completion",
                  "created": 1677652288,
                  "model": "gpt-4",
                  "choices": [{
                    "index": 0,
                    "message": {
                      "role": "assistant",
                      "content": "Hello! How can I help you today?"
                    },
                    "finish_reason": "stop"
                  }],
                  "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 9,
                    "total_tokens": 19
                  }
                }
                """;

            mockServer.enqueue(new MockResponse()
                    .setBody(mockResponse)
                    .addHeader("Content-Type", "application/json"));

            ChatRequest request = createChatRequest("gpt-4", false);
            String baseUrl = mockServer.url("/").toString().replaceAll("/$", "");

            // Act
            ChatResponse response = adapter.chat(request, "test-api-key", baseUrl, "gpt-4").block();

            // Assert
            assertNotNull(response);
            assertEquals("chatcmpl-123", response.getId());
            assertEquals("chat.completion", response.getObject());
            assertEquals("gpt-4", response.getModel());
            assertEquals(1, response.getChoices().size());
            assertEquals("assistant", response.getChoices().get(0).getMessage().getRole());
            assertEquals("Hello! How can I help you today?", response.getChoices().get(0).getMessage().getContent());
            assertEquals("stop", response.getChoices().get(0).getFinishReason());
            assertEquals(10, response.getUsage().getPromptTokens());
            assertEquals(9, response.getUsage().getCompletionTokens());
            assertEquals(19, response.getUsage().getTotalTokens());
        }

        @Test
        @DisplayName("带可选参数调用")
        void chat_withOptionalParams() throws Exception {
            // Arrange
            String mockResponse = """
                {
                  "id": "chatcmpl-456",
                  "object": "chat.completion",
                  "created": 1677652288,
                  "model": "gpt-3.5-turbo",
                  "choices": [{
                    "index": 0,
                    "message": {
                      "role": "assistant",
                      "content": "Response with params"
                    },
                    "finish_reason": "stop"
                  }],
                  "usage": {
                    "prompt_tokens": 5,
                    "completion_tokens": 3,
                    "total_tokens": 8
                  }
                }
                """;

            mockServer.enqueue(new MockResponse()
                    .setBody(mockResponse)
                    .addHeader("Content-Type", "application/json"));

            ChatRequest request = createChatRequest("gpt-3.5-turbo", false);
            request.setTemperature(0.7);
            request.setMaxTokens(100);
            request.setTopP(0.9);
            request.setFrequencyPenalty(0.5);
            request.setPresencePenalty(0.3);
            request.setUser("test-user");

            String baseUrl = mockServer.url("/").toString().replaceAll("/$", "");

            // Act
            ChatResponse response = adapter.chat(request, "test-api-key", baseUrl, "gpt-3.5-turbo").block();

            // Assert
            assertNotNull(response);
            assertEquals("chatcmpl-456", response.getId());
        }
    }

    @Nested
    @DisplayName("chatStream")
    class ChatStreamTest {

        @Test
        @DisplayName("流式调用返回数据")
        void chatStream_success() throws Exception {
            // Arrange - SSE 格式响应
            String sseResponse = "data: {\"id\":\"chatcmpl-123\",\"object\":\"chat.completion.chunk\",\"created\":1677652288,\"model\":\"gpt-4\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}]}\n\n" +
                    "data: {\"id\":\"chatcmpl-123\",\"object\":\"chat.completion.chunk\",\"created\":1677652288,\"model\":\"gpt-4\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}\n\n" +
                    "data: [DONE]\n\n";

            mockServer.enqueue(new MockResponse()
                    .setBody(sseResponse)
                    .addHeader("Content-Type", "text/event-stream"));

            ChatRequest request = createChatRequest("gpt-4", true);
            String baseUrl = mockServer.url("/").toString().replaceAll("/$", "");

            // Act
            List<String> chunks = adapter.chatStream(request, "test-api-key", baseUrl, "gpt-4")
                    .collectList()
                    .block();

            // Assert
            assertNotNull(chunks);
            assertTrue(chunks.size() > 0);
        }
    }

    @Nested
    @DisplayName("healthCheck")
    class HealthCheckTest {

        @Test
        @DisplayName("健康检查成功")
        void healthCheck_healthy() throws Exception {
            // Arrange
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"data\":[]}")
                    .addHeader("Content-Type", "application/json"));

            String baseUrl = mockServer.url("/").toString().replaceAll("/$", "");

            // Act
            Boolean healthy = adapter.healthCheck("test-api-key", baseUrl).block();

            // Assert
            assertTrue(healthy);
        }

        @Test
        @DisplayName("健康检查失败")
        void healthCheck_unhealthy() throws Exception {
            // Arrange
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setBody("{\"error\":{\"message\":\"Invalid API key\"}}"));

            String baseUrl = mockServer.url("/").toString().replaceAll("/$", "");

            // Act
            Boolean healthy = adapter.healthCheck("invalid-key", baseUrl).block();

            // Assert
            assertFalse(healthy);
        }
    }

    // ==================== 辅助方法 ====================

    private ChatRequest createChatRequest(String model, boolean stream) {
        ChatRequest request = new ChatRequest();
        request.setModel(model);
        request.setStream(stream);

        ChatRequest.Message systemMsg = new ChatRequest.Message();
        systemMsg.setRole("system");
        systemMsg.setContent("You are a helpful assistant.");

        ChatRequest.Message userMsg = new ChatRequest.Message();
        userMsg.setRole("user");
        userMsg.setContent("Hello!");

        request.setMessages(Arrays.asList(systemMsg, userMsg));
        return request;
    }
}
