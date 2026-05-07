package com.bank.ai.gateway.adapter;

import com.bank.ai.gateway.model.dto.request.ChatRequest;
import com.bank.ai.gateway.model.dto.response.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 模型适配器接口
 *
 * <p>所有渠道适配器必须实现此接口。
 *
 * @since 1.0.0
 */
public interface ModelAdapter {

    /**
     * 获取渠道提供商名称
     *
     * @return 提供商名称（如 OPENAI、CLAUDE）
     */
    String getProvider();

    /**
     * 聊天补全（非流式）
     *
     * @param request    请求
     * @param apiKey     API Key
     * @param baseUrl    基础 URL
     * @param model      渠道模型名
     * @return 响应
     */
    Mono<ChatResponse> chat(ChatRequest request, String apiKey, String baseUrl, String model);

    /**
     * 聊天补全（流式）
     *
     * @param request    请求
     * @param apiKey     API Key
     * @param baseUrl    基础 URL
     * @param model      渠道模型名
     * @return SSE 数据流
     */
    Flux<String> chatStream(ChatRequest request, String apiKey, String baseUrl, String model);

    /**
     * 检查渠道健康状态
     *
     * @param apiKey  API Key
     * @param baseUrl 基础 URL
     * @return 是否健康
     */
    Mono<Boolean> healthCheck(String apiKey, String baseUrl);
}
