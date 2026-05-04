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
 * 通义千问 (Qwen / DashScope) 适配器
 *
 * <p>阿里云 DashScope 平台 API 适配。
 * 使用兼容模式（OpenAI-compatible）或原生模式。
 * API 文档: https://help.aliyun.com/zh/dashscope/developer-reference/api-details
 *
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TongyiAdapter implements ModelAdapter {

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    private static final String PROVIDER = "QWEN";
    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com";

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public Mono<ChatResponse> chat(ChatRequest request, String apiKey, String baseUrl, String model) {
        ObjectNode qwenRequest = buildQwenRequest(request, model, false);

        WebClient webClient = buildWebClient(apiKey, baseUrl);

        return webClient.post()
                .uri("/compatible-mode/v1/chat/completions")
                .bodyValue(qwenRequest)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseResponse)
                .doOnError(e -> log.error("通义千问调用失败: {}", e.getMessage()))
                .onErrorMap(e -> new BizException(ErrorCode.CHANNEL_ERROR, "通义千问渠道调用失败: " + e.getMessage()));
    }

    @Override
    public Flux<String> chatStream(ChatRequest request, String apiKey, String baseUrl, String model) {
        ObjectNode qwenRequest = buildQwenRequest(request, model, true);

        WebClient webClient = buildWebClient(apiKey, baseUrl);

        return webClient.post()
                .uri("/compatible-mode/v1/chat/completions")
                .bodyValue(qwenRequest)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnError(e -> log.error("通义千问流式调用失败: {}", e.getMessage()))
                .onErrorMap(e -> new BizException(ErrorCode.CHANNEL_ERROR, "通义千问渠道调用失败: " + e.getMessage()));
    }

    @Override
    public Mono<Boolean> healthCheck(String apiKey, String baseUrl) {
        WebClient webClient = buildWebClient(apiKey, baseUrl);

        return webClient.get()
                .uri("/compatible-mode/v1/models")
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> true)
                .onErrorReturn(false);
    }

    // ==================== 私有方法 ====================

    private WebClient buildWebClient(String apiKey, String baseUrl) {
        return webClientBuilder
                .baseUrl(normalizeBaseUrl(baseUrl))
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * 构建通义千问请求
     *
     * <p>使用兼容模式，请求格式与 OpenAI 基本一致。
     * 支持 Qwen 特有参数如 enable_search、response_format 等。
     */
    private ObjectNode buildQwenRequest(ChatRequest request, String model, boolean stream) {
        ObjectNode qwenRequest = objectMapper.createObjectNode();
        qwenRequest.put("model", model);
        qwenRequest.put("stream", stream);

        // 消息列表
        ArrayNode messages = objectMapper.createArrayNode();
        if (request.getMessages() != null) {
            for (ChatRequest.Message msg : request.getMessages()) {
                ObjectNode msgNode = objectMapper.createObjectNode();
                msgNode.put("role", msg.getRole());
                msgNode.put("content", msg.getContent());
                messages.add(msgNode);
            }
        }
        qwenRequest.set("messages", messages);

        // 可选参数
        if (request.getTemperature() != null) {
            qwenRequest.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            qwenRequest.put("max_tokens", request.getMaxTokens());
        }
        if (request.getTopP() != null) {
            qwenRequest.put("top_p", request.getTopP());
        }
        if (request.getStop() != null && !request.getStop().isEmpty()) {
            qwenRequest.set("stop", objectMapper.valueToTree(request.getStop()));
        }

        return qwenRequest;
    }

    /**
     * 解析通义千问响应
     *
     * <p>兼容模式下响应格式与 OpenAI 基本一致。
     */
    private ChatResponse parseResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            // 检查错误
            if (root.has("error")) {
                JsonNode errorNode = root.path("error");
                String errorMsg = errorNode.path("message").asText("未知错误");
                throw new BizException(ErrorCode.CHANNEL_ERROR, "通义千问错误: " + errorMsg);
            }

            List<ChatResponse.Choice> choices = new ArrayList<>();
            JsonNode choicesNode = root.path("choices");
            if (choicesNode.isArray()) {
                for (JsonNode choiceNode : choicesNode) {
                    JsonNode messageNode = choiceNode.path("message");
                    ChatResponse.Message message = ChatResponse.Message.builder()
                            .role(messageNode.path("role").asText())
                            .content(messageNode.path("content").asText())
                            .build();

                    choices.add(ChatResponse.Choice.builder()
                            .index(choiceNode.path("index").asInt())
                            .message(message)
                            .finishReason(choiceNode.path("finish_reason").asText(null))
                            .build());
                }
            }

            JsonNode usageNode = root.path("usage");
            ChatResponse.Usage usage = ChatResponse.Usage.builder()
                    .promptTokens(usageNode.path("prompt_tokens").asInt(0))
                    .completionTokens(usageNode.path("completion_tokens").asInt(0))
                    .totalTokens(usageNode.path("total_tokens").asInt(0))
                    .build();

            return ChatResponse.builder()
                    .id(root.path("id").asText("qwen-" + UUID.randomUUID()))
                    .object(root.path("object").asText("chat.completion"))
                    .created(root.path("created").asLong(Instant.now().getEpochSecond()))
                    .model(root.path("model").asText())
                    .choices(choices)
                    .usage(usage)
                    .build();

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析通义千问响应失败: {}", e.getMessage());
            throw new BizException(ErrorCode.CHANNEL_ERROR, "通义千问响应解析失败");
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return DEFAULT_BASE_URL;
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
