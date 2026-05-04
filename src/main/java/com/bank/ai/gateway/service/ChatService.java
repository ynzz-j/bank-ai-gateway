package com.bank.ai.gateway.service;

import com.bank.ai.gateway.model.dto.request.ChatRequest;
import com.bank.ai.gateway.model.dto.response.ChatResponse;
import com.bank.ai.gateway.service.channel.ChannelRoutingService;
import com.bank.ai.gateway.service.circuitbreaker.CircuitBreakerService;
import com.bank.ai.gateway.service.circuitbreaker.CircuitBreakerService.CircuitBreakerException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 聊天服务
 *
 * <p>负责路由请求到对应渠道适配器，集成熔断和超时控制。
 *
 * <h3>容错机制：</h3>
 * <ul>
 *   <li>熔断：渠道故障自动熔断，防止级联失败</li>
 *   <li>超时：非流式 60s，流式 5 分钟</li>
 *   <li>降级：熔断时返回友好错误信息</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChannelRoutingService routingService;
    private final CircuitBreakerService circuitBreakerService;
    private final TimeLimiterRegistry timeLimiterRegistry;

    // 超时配置
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(5);

    /**
     * 聊天补全（非流式）
     *
     * @param request 请求
     * @return 响应
     */
    public Mono<ChatResponse> chat(ChatRequest request) {
        try {
            ChannelRoutingService.RouteResult route = routingService.route(request.getModel());
            Long channelId = route.channel().getId();
            String channelName = route.channel().getName();

            // 获取超时限制器
            TimeLimiter timeLimiter = getTimeLimiter(channelId, CHAT_TIMEOUT);

            return circuitBreakerService.executeReactive(channelId, channelName, () ->
                    route.adapter()
                            .chat(request, route.apiKey(), route.baseUrl(), route.actualModel())
                            .timeout(CHAT_TIMEOUT)
                            .transformDeferred(TimeLimiterOperator.of(timeLimiter))
            ).doOnError(CircuitBreakerException.class, e -> {
                log.warn("Circuit breaker open for channel {}: {}", channelName, e.getMessage());
            }).doOnError(e -> {
                if (!(e instanceof CircuitBreakerException)) {
                    log.error("Chat request failed for model {}: {}", request.getModel(), e.getMessage());
                }
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
            Long channelId = route.channel().getId();
            String channelName = route.channel().getName();

            // 获取超时限制器（流式使用更长的超时）
            TimeLimiter timeLimiter = getTimeLimiter(channelId, STREAM_TIMEOUT);

            return circuitBreakerService.executeReactiveFlux(channelId, channelName, () ->
                    route.adapter()
                            .chatStream(request, route.apiKey(), route.baseUrl(), route.actualModel())
                            .timeout(STREAM_TIMEOUT)
                            // 流式不使用 TimeLimiter（因为是无限流）
            ).doOnError(CircuitBreakerException.class, e -> {
                log.warn("Circuit breaker open for channel {}: {}", channelName, e.getMessage());
            }).doOnError(e -> {
                if (!(e instanceof CircuitBreakerException)) {
                    log.error("Stream request failed for model {}: {}", request.getModel(), e.getMessage());
                }
            });

        } catch (Exception e) {
            log.error("Route failed for model {}: {}", request.getModel(), e.getMessage());
            return Flux.error(e);
        }
    }

    /**
     * 获取或创建超时限制器
     */
    private TimeLimiter getTimeLimiter(Long channelId, Duration timeout) {
        String name = "channel-" + channelId;
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(timeout)
                .cancelRunningFuture(true)
                .build();
        
        return timeLimiterRegistry.timeLimiter(name, config);
    }
}
