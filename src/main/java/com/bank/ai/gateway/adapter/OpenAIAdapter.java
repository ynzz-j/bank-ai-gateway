package com.bank.ai.gateway.adapter;

import com.bank.ai.gateway.common.BizException;
import com.bank.ai.gateway.common.ErrorCode;
import com.bank.ai.gateway.model.dto.request.ChatRequest;
import com.bank.ai.gateway.model.dto.response.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * OpenAI 适配器
 *
 * <p>支持 OpenAI API（含 Azure OpenAI）。
 *
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAIAdapter implements ModelAdapter {

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    private static final String PROVIDER = "OPENAI";

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public Mono<ChatResponse> chat(ChatRequest request, String apiKey, String baseUrl, String model) {
        // 构建 OpenAI 请求
        ObjectNode openaiRequest = buildOpenAIRequest(request, model, false);

        WebClient webClient = webClientBuilder
                .baseUrl(normalizeBaseUrl(baseUrl))
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        return webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(openaiRequest)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseResponse)
                .doOnError(e -> log.error("OpenAI 调用失败: {}", e.getMessage()))
                .onErrorMap(e -> new BizException(ErrorCode.CHANNEL_ERROR, "渠道调用失败: " + e.getMessage()));
    }

    @Override
    public Flux<String> chatStream(ChatRequest request, String apiKey, String baseUrl, String model) {
        // 构建 OpenAI 请求（流式）
        ObjectNode openaiRequest = buildOpenAIRequest(request, model, true);

        WebClient webClient = webClientBuilder
                .baseUrl(normalizeBaseUrl(baseUrl))
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        return webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(openaiRequest)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnError(e -> log.error("OpenAI 流式调用失败: {}", e.getMessage()))
                .onErrorMap(e -> new BizException(ErrorCode.CHANNEL_ERROR, "渠道调用失败: " + e.getMessage()));
    }

    @Override
    public Mono<Boolean> healthCheck(String apiKey, String baseUrl) {
        WebClient webClient = webClientBuilder
                .baseUrl(normalizeBaseUrl(baseUrl))
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();

        return webClient.get()
                .uri("/v1/models")
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> true)
                .onErrorReturn(false);
    }

    // ==================== 私有方法 ====================

    /**
     * 构建 OpenAI 请求
     */
    private ObjectNode buildOpenAIRequest(ChatRequest request, String model, boolean stream) {
        ObjectNode openaiRequest = objectMapper.createObjectNode();
        openaiRequest.put("model", model);
        openaiRequest.put("stream", stream);

        // 消息列表
        openaiRequest.set("messages", objectMapper.valueToTree(request.getMessages()));

        // 可选参数
        if (request.getTemperature() != null) {
            openaiRequest.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            openaiRequest.put("max_tokens", request.getMaxTokens());
        }
        if (request.getTopP() != null) {
            openaiRequest.put("top_p", request.getTopP());
        }
        if (request.getStop() != null && !request.getStop().isEmpty()) {
            openaiRequest.set("stop", objectMapper.valueToTree(request.getStop()));
        }
        if (request.getFrequencyPenalty() != null) {
            openaiRequest.put("frequency_penalty", request.getFrequencyPenalty());
        }
        if (request.getPresencePenalty() != null) {
            openaiRequest.put("presence_penalty", request.getPresencePenalty());
        }
        if (request.getUser() != null) {
            openaiRequest.put("user", request.getUser());
        }

        return openaiRequest;
    }

    /**
     * 解析 OpenAI 响应
     */
    private ChatResponse parseResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

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
                    .id(root.path("id").asText(UUID.randomUUID().toString()))
                    .object(root.path("object").asText("chat.completion"))
                    .created(root.path("created").asLong(Instant.now().getEpochSecond()))
                    .model(root.path("model").asText())
                    .choices(choices)
                    .usage(usage)
                    .build();

        } catch (Exception e) {
            log.error("解析 OpenAI 响应失败: {}", e.getMessage());
            throw new BizException(ErrorCode.CHANNEL_ERROR, "响应解析失败");
        }
    }

    /**
     * 规范化基础 URL
     */
    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return "https://api.openai.com";
        }
        // 移除末尾斜杠
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
