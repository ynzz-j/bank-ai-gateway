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
 * Claude (Anthropic) 适配器
 *
 * <p>Anthropic Claude API 适配，支持 Messages API 格式。
 * API 文档: https://docs.anthropic.com/en/docs/about-claude/models
 *
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeAdapter implements ModelAdapter {

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    private static final String PROVIDER = "CLAUDE";
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final String API_VERSION = "2023-06-01";

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public Mono<ChatResponse> chat(ChatRequest request, String apiKey, String baseUrl, String model) {
        ObjectNode claudeRequest = buildClaudeRequest(request, model, false);

        WebClient webClient = buildWebClient(apiKey, baseUrl);

        return webClient.post()
                .uri("/v1/messages")
                .bodyValue(claudeRequest)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseResponse)
                .doOnError(e -> log.error("Claude 调用失败: {}", e.getMessage()))
                .onErrorMap(e -> new BizException(ErrorCode.CHANNEL_ERROR, "Claude 渠道调用失败: " + e.getMessage()));
    }

    @Override
    public Flux<String> chatStream(ChatRequest request, String apiKey, String baseUrl, String model) {
        ObjectNode claudeRequest = buildClaudeRequest(request, model, true);

        WebClient webClient = buildWebClient(apiKey, baseUrl);

        return webClient.post()
                .uri("/v1/messages")
                .bodyValue(claudeRequest)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnError(e -> log.error("Claude 流式调用失败: {}", e.getMessage()))
                .onErrorMap(e -> new BizException(ErrorCode.CHANNEL_ERROR, "Claude 渠道调用失败: " + e.getMessage()));
    }

    @Override
    public Mono<Boolean> healthCheck(String apiKey, String baseUrl) {
        WebClient webClient = buildWebClient(apiKey, baseUrl);

        return webClient.get()
                .uri("/v1/models")
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> true)
                .onErrorReturn(false);
    }

    // ==================== 私有方法 ====================

    private WebClient buildWebClient(String apiKey, String baseUrl) {
        return webClientBuilder
                .baseUrl(normalizeBaseUrl(baseUrl))
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", API_VERSION)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * 构建 Claude Messages API 请求
     *
     * <p>Claude 的 system 是顶级参数，不从 messages 中提取。
     * messages 只包含 user/assistant 角色。
     */
    private ObjectNode buildClaudeRequest(ChatRequest request, String model, boolean stream) {
        ObjectNode claudeRequest = objectMapper.createObjectNode();
        claudeRequest.put("model", model);
        claudeRequest.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 4096);
        claudeRequest.put("stream", stream);

        // Claude 的 system 是顶级参数
        String systemPrompt = extractSystemPrompt(request);
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            claudeRequest.put("system", systemPrompt);
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
        claudeRequest.set("messages", messages);

        // 可选参数
        if (request.getTemperature() != null) {
            claudeRequest.put("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            claudeRequest.put("top_p", request.getTopP());
        }
        if (request.getStop() != null && !request.getStop().isEmpty()) {
            claudeRequest.set("stop_sequences", objectMapper.valueToTree(request.getStop()));
        }

        return claudeRequest;
    }

    /**
     * 从消息列表中提取 system prompt（可能有多条，合并）
     */
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
     * 解析 Claude 响应为统一 ChatResponse 格式
     */
    private ChatResponse parseResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            List<ChatResponse.Choice> choices = new ArrayList<>();

            // Claude 的 content 可能是一个数组（多 content block）或字符串
            StringBuilder contentBuilder = new StringBuilder();
            JsonNode contentNode = root.path("content");
            if (contentNode.isArray()) {
                for (JsonNode block : contentNode) {
                    if ("text".equals(block.path("type").asText())) {
                        contentBuilder.append(block.path("text").asText());
                    }
                }
            } else {
                contentBuilder.append(contentNode.asText(""));
            }

            ChatResponse.Message message = ChatResponse.Message.builder()
                    .role("assistant")
                    .content(contentBuilder.toString())
                    .build();

            String stopReason = root.path("stop_reason").asText(null);
            // 映射 Claude stop_reason → OpenAI finish_reason
            String finishReason = mapStopReason(stopReason);

            choices.add(ChatResponse.Choice.builder()
                    .index(0)
                    .message(message)
                    .finishReason(finishReason)
                    .build());

            // Claude 的 usage 字段
            JsonNode usageNode = root.path("usage");
            ChatResponse.Usage usage = ChatResponse.Usage.builder()
                    .promptTokens(usageNode.path("input_tokens").asInt(0))
                    .completionTokens(usageNode.path("output_tokens").asInt(0))
                    .totalTokens(usageNode.path("input_tokens").asInt(0)
                            + usageNode.path("output_tokens").asInt(0))
                    .build();

            return ChatResponse.builder()
                    .id(root.path("id").asText("msg_" + UUID.randomUUID()))
                    .object("chat.completion")
                    .created(Instant.now().getEpochSecond())
                    .model(root.path("model").asText())
                    .choices(choices)
                    .usage(usage)
                    .build();

        } catch (Exception e) {
            log.error("解析 Claude 响应失败: {}", e.getMessage());
            throw new BizException(ErrorCode.CHANNEL_ERROR, "Claude 响应解析失败");
        }
    }

    /**
     * 映射 Claude stop_reason 到标准 finish_reason
     */
    private String mapStopReason(String stopReason) {
        if (stopReason == null) {
            return null;
        }
        return switch (stopReason) {
            case "end_turn" -> "stop";
            case "max_tokens" -> "length";
            case "stop_sequence" -> "stop";
            default -> stopReason;
        };
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return DEFAULT_BASE_URL;
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
