package com.bank.ai.gateway.service;

import com.bank.ai.gateway.model.dto.request.ChatRequest;
import com.bank.ai.gateway.model.dto.response.ChatResponse;
import com.bank.ai.gateway.service.channel.ChannelRoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 聊天服务
 *
 * <p>负责路由请求到对应渠道适配器。
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChannelRoutingService routingService;

    /**
     * 聊天补全（非流式）
     *
     * @param request 请求
     * @return 响应
     */
    public Mono<ChatResponse> chat(ChatRequest request) {
        try {
            ChannelRoutingService.RouteResult route = routingService.route(request.getModel());

            return route.adapter()
                    .chat(request, route.apiKey(), route.baseUrl(), route.actualModel())
                    .doOnError(e -> {
                        log.error("Chat request failed for model {}: {}",
                                request.getModel(), e.getMessage());
                        // TODO: 熔断逻辑
                    });
        } catch (Exception e) {
            log.error("Route failed for model {}: {}", request.getModel(), e.getMessage());
            return Mono.error(e);
        }
    }

    /**
     * 聊天补全（流式）
     *
     * @param request 请求
     * @return SSE 数据流
     */
    public Flux<String> chatStream(ChatRequest request) {
        try {
            ChannelRoutingService.RouteResult route = routingService.route(request.getModel());

            return route.adapter()
                    .chatStream(request, route.apiKey(), route.baseUrl(), route.actualModel())
                    .doOnError(e -> {
                        log.error("Stream request failed for model {}: {}",
                                request.getModel(), e.getMessage());
                        // TODO: 熔断逻辑
                    });
        } catch (Exception e) {
            log.error("Route failed for model {}: {}", request.getModel(), e.getMessage());
            return Flux.error(e);
        }
    }
}
