package com.bank.ai.gateway.service;

import com.bank.ai.gateway.compliance.ContentFilter.FilterResult;
import com.bank.ai.gateway.model.dto.request.ChatRequest;
import com.bank.ai.gateway.model.dto.response.ChatResponse;
import com.bank.ai.gateway.service.channel.ChannelRoutingService;
import com.bank.ai.gateway.service.circuitbreaker.CircuitBreakerService;
import com.bank.ai.gateway.service.circuitbreaker.CircuitBreakerService.CircuitBreakerException;
import com.bank.ai.gateway.service.compliance.ComplianceService;
import com.bank.ai.gateway.compliance.ContentFilter.FilterContext;
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
    private final ComplianceService complianceService;

    // 超时配置
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(5);

    /**
     * 聊天补全（非流式）
     *
     * @param request 请求
     * @param context 合规上下文
     * @return 响应
     */
    public Mono<ChatResponse> chat(ChatRequest request, FilterContext context) {
        // 1. 入参合规过滤
        String inputContent = extractContent(request);
        return complianceService.filterInput(inputContent, context)
                .flatMap(inputResult -> {
                    if (!inputResult.passed()) {
                        log.warn("Input blocked by compliance filter: {}", inputResult.violation());
                        return Mono.error(new ComplianceBlockedException(inputResult.violation()));
                    }

                    // 2. 路由到渠道
                    try {
                        ChannelRoutingService.RouteResult route = routingService.route(request.getModel());
                        Long channelId = route.channel().getId();
                        String channelName = route.channel().getName();
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
                })
                // 3. 出参合规过滤
                .flatMap(response -> {
                    String outputContent = extractOutputContent(response);
                    return complianceService.filterOutput(outputContent, context)
                            .map(outputResult -> {
                                if (outputResult.passed() && outputResult.processedContent() != null) {
                                    // 更新响应内容
                                    return updateResponseContent(response, outputResult.processedContent());
                                }
                                return response;
                            });
                });
    }

    /**
     * 聊天补全（流式）
     *
     * @param request 请求
     * @param context 合规上下文
     * @return SSE 数据流
     */
    public Flux<String> chatStream(ChatRequest request, FilterContext context) {
        // 1. 入参合规过滤
        String inputContent = extractContent(request);
        return complianceService.filterInput(inputContent, context)
                .flatMapMany(inputResult -> {
                    if (!inputResult.passed()) {
                        log.warn("Input blocked by compliance filter: {}", inputResult.violation());
                        return Flux.error(new ComplianceBlockedException(inputResult.violation()));
                    }

                    // 2. 路由到渠道
                    try {
                        ChannelRoutingService.RouteResult route = routingService.route(request.getModel());
                        Long channelId = route.channel().getId();
                        String channelName = route.channel().getName();
                        TimeLimiter timeLimiter = getTimeLimiter(channelId, STREAM_TIMEOUT);

                        return circuitBreakerService.executeReactiveFlux(channelId, channelName, () ->
                                route.adapter()
                                        .chatStream(request, route.apiKey(), route.baseUrl(), route.actualModel())
                                        .timeout(STREAM_TIMEOUT)
                                        // 流式合规过滤：逐块检测
                                        .flatMap(chunk -> complianceService.filterStreamChunk(chunk, context)
                                                .map(result -> result.passed() ? chunk : "[内容已过滤]"))
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
                });
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

    // ==================== 合规辅助方法 ====================

    /**
     * 从请求中提取内容文本
     */
    private String extractContent(ChatRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return "";
        }
        return request.getMessages().stream()
                .map(m -> m.getContent() != null ? m.getContent().toString() : "")
                .reduce("", (a, b) -> a + " " + b)
                .trim();
    }

    /**
     * 从响应中提取内容文本
     */
    private String extractOutputContent(ChatResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return "";
        }
        return response.getChoices().stream()
                .filter(c -> c.getMessage() != null)
                .map(c -> c.getMessage().getContent())
                .reduce("", (a, b) -> a + " " + b)
                .trim();
    }

    /**
     * 更新响应内容
     */
    private ChatResponse updateResponseContent(ChatResponse response, String newContent) {
        if (response.getChoices() != null && !response.getChoices().isEmpty()) {
            response.getChoices().get(0).getMessage().setContent(newContent);
        }
        return response;
    }
}
