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
 * 智谱 AI (Zhipu / ChatGLM) 适配器
 *
 * <p>智谱 AI 开放平台 API 适配。
 * API 文档: https://open.bigmodel.cn/dev/api#glm-4
 *
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZhipuAdapter implements ModelAdapter {

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    private static final String PROVIDER = "ZHIPU";
    private static final String DEFAULT_BASE_URL = "https://open.bigmodel.cn";

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public Mono<ChatResponse> chat(ChatRequest request, String apiKey, String baseUrl, String model) {
        ObjectNode zhipuRequest = buildZhipuRequest(request, model, false);

        WebClient webClient = buildWebClient(apiKey, baseUrl);

        return webClient.post()
                .uri("/api/paas/v4/chat/completions")
                .bodyValue(zhipuRequest)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseResponse)
                .doOnError(e -> log.error("智谱 AI 调用失败: {}", e.getMessage()))
                .onErrorMap(e -> new BizException(ErrorCode.CHANNEL_ERROR, "智谱 AI 渠道调用失败: " + e.getMessage()));
    }

    @Override
    public Flux<String> chatStream(ChatRequest request, String apiKey, String baseUrl, String model) {
        ObjectNode zhipuRequest = buildZhipuRequest(request, model, true);

        WebClient webClient = buildWebClient(apiKey, baseUrl);

        return webClient.post()
                .uri("/api/paas/v4/chat/completions")
                .bodyValue(zhipuRequest)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnError(e -> log.error("智谱 AI 流式调用失败: {}", e.getMessage()))
                .onErrorMap(e -> new BizException(ErrorCode.CHANNEL_ERROR, "智谱 AI 渠道调用失败: " + e.getMessage()));
    }

    @Override
    public Mono<Boolean> healthCheck(String apiKey, String baseUrl) {
        WebClient webClient = buildWebClient(apiKey, baseUrl);

        return webClient.get()
                .uri("/api/paas/v4/models")
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> true)
                .onErrorReturn(false);
    }

    // ==================== 私有方法 ====================

    private WebClient buildWebClient(String apiKey, String baseUrl) {
        return webClientBuilder
                .baseUrl(normalizeBaseUrl(baseUrl))
                .defaultHeader("Authorization", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * 构建智谱 AI 请求
     *
     * <p>智谱 API 特点：
     * - 请求格式与 OpenAI 基本一致
     * - 支持 tools/tool_choice 参数
     * - 支持额外的参数如 do_sample、max_tokens 等
     */
    private ObjectNode buildZhipuRequest(ChatRequest request, String model, boolean stream) {
        ObjectNode zhipuRequest = objectMapper.createObjectNode();
        zhipuRequest.put("model", model);
        zhipuRequest.put("stream", stream);

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
        zhipuRequest.set("messages", messages);

        // 可选参数
        if (request.getTemperature() != null) {
            zhipuRequest.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            zhipuRequest.put("max_tokens", request.getMaxTokens());
        }
        if (request.getTopP() != null) {
            zhipuRequest.put("top_p", request.getTopP());
        }
        if (request.getStop() != null && !request.getStop().isEmpty()) {
            zhipuRequest.set("stop", objectMapper.valueToTree(request.getStop()));
        }

        return zhipuRequest;
    }

    /**
     * 解析智谱 AI 响应
     *
     * <p>响应格式与 OpenAI 基本一致。
     */
    private ChatResponse parseResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            // 检查错误
            if (root.has("error")) {
                JsonNode errorNode = root.path("error");
                String errorMsg = errorNode.path("message").asText("未知错误");
                throw new BizException(ErrorCode.CHANNEL_ERROR, "智谱 AI 错误: " + errorMsg);
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
                    .id(root.path("id").asText("zhipu-" + UUID.randomUUID()))
                    .object(root.path("object").asText("chat.completion"))
                    .created(root.path("created").asLong(Instant.now().getEpochSecond()))
                    .model(root.path("model").asText())
                    .choices(choices)
                    .usage(usage)
                    .build();

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析智谱 AI 响应失败: {}", e.getMessage());
            throw new BizException(ErrorCode.CHANNEL_ERROR, "智谱 AI 响应解析失败");
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return DEFAULT_BASE_URL;
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
