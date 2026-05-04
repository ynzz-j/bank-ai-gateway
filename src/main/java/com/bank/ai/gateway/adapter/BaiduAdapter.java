package com.bank.ai.gateway.adapter;

import com.bank.ai.gateway.common.BizException;
import com.bank.ai.gateway.common.ErrorCode;
import com.bank.ai.gateway.model.dto.request.ChatRequest;
import com.bank.ai.gateway.model.dto.response.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文心一言 (Baidu ERNIE) 适配器
 *
 * <p>百度智能云千帆大模型平台 API 适配。
 * API 文档: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/clntwmv7t
 *
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BaiduAdapter implements ModelAdapter {

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    private static final String PROVIDER = "BAIDU";
    private static final String DEFAULT_BASE_URL = "https://aip.baidubce.com";

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public Mono<ChatResponse> chat(ChatRequest request, String apiKey, String baseUrl, String model) {
        ObjectNode baiduRequest = buildBaiduRequest(request, false);

        WebClient webClient = buildWebClient(baseUrl);

        // 文心 API 使用 access_token 作为 query param
        String endpoint = "/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/" + model;

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(endpoint)
                        .queryParam("access_token", apiKey)
                        .build())
                .bodyValue(baiduRequest)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseResponse)
                .doOnError(e -> log.error("文心一言调用失败: {}", e.getMessage()))
                .onErrorMap(e -> new BizException(ErrorCode.CHANNEL_ERROR, "文心一言渠道调用失败: " + e.getMessage()));
    }

    @Override
    public Flux<String> chatStream(ChatRequest request, String apiKey, String baseUrl, String model) {
        ObjectNode baiduRequest = buildBaiduRequest(request, true);

        WebClient webClient = buildWebClient(baseUrl);

        String endpoint = "/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/" + model;

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(endpoint)
                        .queryParam("access_token", apiKey)
                        .build())
                .bodyValue(baiduRequest)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnError(e -> log.error("文心一言流式调用失败: {}", e.getMessage()))
                .onErrorMap(e -> new BizException(ErrorCode.CHANNEL_ERROR, "文心一言渠道调用失败: " + e.getMessage()));
    }

    @Override
    public Mono<Boolean> healthCheck(String apiKey, String baseUrl) {
        // 文心没有直接的 models 接口，通过一个简单的 token 计数请求验证
        WebClient webClient = buildWebClient(baseUrl);

        ObjectNode checkRequest = objectMapper.createObjectNode();
        checkRequest.put("messages", "[{\"role\":\"user\",\"content\":\"hi\"}]");

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/ernie-lite-8k")
                        .queryParam("access_token", apiKey)
                        .build())
                .bodyValue(checkRequest)
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> !response.contains("error_code"))
                .onErrorReturn(false);
    }

    // ==================== 私有方法 ====================

    private WebClient buildWebClient(String baseUrl) {
        return webClientBuilder
                .baseUrl(normalizeBaseUrl(baseUrl))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * 构建文心一言请求
     *
     * <p>文心 API 特点：
     * - system 是顶级参数
     * - messages 只包含 user/assistant
     * - 支持 stream 参数
     */
    private ObjectNode buildBaiduRequest(ChatRequest request, boolean stream) {
        ObjectNode baiduRequest = objectMapper.createObjectNode();
        baiduRequest.put("stream", stream);

        // 提取 system prompt
        String systemPrompt = extractSystemPrompt(request);
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            baiduRequest.put("system", systemPrompt);
        }

        // 构建消息列表（排除 system 角色）
        ArrayNode messages = objectMapper.createArrayNode();
        if (request.getMessages() != null) {
            for (ChatRequest.Message msg : request.getMessages()) {
                if (!"system".equalsIgnoreCase(msg.getRole())) {
                    ObjectNode msgNode = objectMapper.createObjectNode();
                    msgNode.put("role", msg.getRole());
                    msgNode.put("content", msg.getContent());
                    messages.add(msgNode);
                }
            }
        }
        baiduRequest.set("messages", messages);

        // 可选参数
        if (request.getTemperature() != null) {
            baiduRequest.put("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            baiduRequest.put("top_p", request.getTopP());
        }
        if (request.getMaxTokens() != null) {
            baiduRequest.put("max_output_tokens", request.getMaxTokens());
        }

        return baiduRequest;
    }

    private String extractSystemPrompt(ChatRequest request) {
        if (request.getMessages() == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (ChatRequest.Message msg : request.getMessages()) {
            if ("system".equalsIgnoreCase(msg.getRole())) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(msg.getContent());
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 解析文心一言响应
     *
     * <p>文心响应格式：
     * {
     *   "id": "as-xxxx",
     *   "object": "chat.completion",
     *   "created": 1234567890,
     *   "result": "回复内容",
     *   "is_truncated": false,
     *   "need_clear_history": false,
     *   "usage": {
     *     "prompt_tokens": 10,
     *     "completion_tokens": 20,
     *     "total_tokens": 30
     *   }
     * }
     */
    private ChatResponse parseResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            // 检查错误
            if (root.has("error_code")) {
                String errorMsg = root.path("error_msg").asText("未知错误");
                throw new BizException(ErrorCode.CHANNEL_ERROR, "文心一言错误: " + errorMsg);
            }

            List<ChatResponse.Choice> choices = new ArrayList<>();

            ChatResponse.Message message = ChatResponse.Message.builder()
                    .role("assistant")
                    .content(root.path("result").asText(""))
                    .build();

            choices.add(ChatResponse.Choice.builder()
                    .index(0)
                    .message(message)
                    .finishReason("stop")
                    .build());

            JsonNode usageNode = root.path("usage");
            ChatResponse.Usage usage = ChatResponse.Usage.builder()
                    .promptTokens(usageNode.path("prompt_tokens").asInt(0))
                    .completionTokens(usageNode.path("completion_tokens").asInt(0))
                    .totalTokens(usageNode.path("total_tokens").asInt(0))
                    .build();

            return ChatResponse.builder()
                    .id(root.path("id").asText("baidu-" + UUID.randomUUID()))
                    .object(root.path("object").asText("chat.completion"))
                    .created(root.path("created").asLong(Instant.now().getEpochSecond()))
                    .model(root.path("model").asText(""))
                    .choices(choices)
                    .usage(usage)
                    .build();

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析文心一言响应失败: {}", e.getMessage());
            throw new BizException(ErrorCode.CHANNEL_ERROR, "文心一言响应解析失败");
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return DEFAULT_BASE_URL;
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
