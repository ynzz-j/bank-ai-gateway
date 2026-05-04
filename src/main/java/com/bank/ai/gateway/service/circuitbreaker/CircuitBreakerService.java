package com.bank.ai.gateway.service.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 熔断器服务
 *
 * <p>封装 Resilience4j CircuitBreaker，提供渠道级熔断能力。
 *
 * <h3>使用方式：</h3>
 * <pre>{@code
 * // 同步调用
 * circuitBreakerService.execute("channel-openai", () -> adapter.chat(request));
 *
 * // 响应式调用
 * circuitBreakerService.executeReactive("channel-openai", () -> adapter.chatReactive(request));
 * }</pre>
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CircuitBreakerService {

    private final CircuitBreakerRegistry registry;

    /**
     * 渠道断路器缓存（渠道ID -> 断路器名称）
     */
    private final Map<Long, String> channelCircuitBreakerMap = new ConcurrentHashMap<>();

    /**
     * 获取或创建渠道断路器
     *
     * @param channelId 渠道ID
     * @param channelName 渠道名称（用于日志）
     * @return 断路器
     */
    public CircuitBreaker getOrCreateCircuitBreaker(Long channelId, String channelName) {
        String circuitBreakerName = "channel-" + channelId;
        channelCircuitBreakerMap.put(channelId, circuitBreakerName);
        
        // 从 Registry 获取断路器（如果不存在则使用默认配置创建）
        CircuitBreaker circuitBreaker = registry.circuitBreaker(circuitBreakerName);
        
        // 注册状态转换监听
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    log.warn("CircuitBreaker '{}' ({}) state changed: {} -> {}",
                            circuitBreakerName, channelName,
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState());
                })
                .onError(event -> {
                    log.debug("CircuitBreaker '{}' recorded error: {}",
                            circuitBreakerName, event.getThrowable().getMessage());
                })
                .onSuccess(event -> {
                    log.debug("CircuitBreaker '{}' recorded success: {}ms",
                            circuitBreakerName, event.getElapsedDuration().toMillis());
                });
        
        return circuitBreaker;
    }

    /**
     * 检查渠道是否熔断
     *
     * @param channelId 渠道ID
     * @return true=已熔断，false=正常
     */
    public boolean isCircuitOpen(Long channelId) {
        String name = channelCircuitBreakerMap.get(channelId);
        if (name == null) {
            return false;
        }
        
        CircuitBreaker circuitBreaker = registry.getAllCircuitBreakers()
                .stream()
                .filter(cb -> cb.getName().equals(name))
                .findFirst()
                .orElse(null);
        if (circuitBreaker == null) {
            return false;
        }
        
        CircuitBreaker.State state = circuitBreaker.getState();
        return state == CircuitBreaker.State.OPEN;
    }

    /**
     * 获取渠道熔断状态
     *
     * @param channelId 渠道ID
     * @return 状态字符串：CLOSED / OPEN / HALF_OPEN / DISABLED / METRICS_ONLY
     */
    public String getCircuitState(Long channelId) {
        String name = channelCircuitBreakerMap.get(channelId);
        if (name == null) {
            return "CLOSED";
        }
        
        CircuitBreaker circuitBreaker = registry.getAllCircuitBreakers()
                .stream()
                .filter(cb -> cb.getName().equals(name))
                .findFirst()
                .orElse(null);
        if (circuitBreaker == null) {
            return "CLOSED";
        }
        
        return circuitBreaker.getState().name();
    }

    /**
     * 获取断路器指标
     *
     * @param channelId 渠道ID
     * @return 指标快照
     */
    public CircuitBreakerMetrics getMetrics(Long channelId) {
        String name = channelCircuitBreakerMap.get(channelId);
        if (name == null) {
            return new CircuitBreakerMetrics("CLOSED", 0, 0, 0.0, 0.0, Duration.ZERO);
        }
        
        CircuitBreaker circuitBreaker = registry.getAllCircuitBreakers()
                .stream()
                .filter(cb -> cb.getName().equals(name))
                .findFirst()
                .orElse(null);
        if (circuitBreaker == null) {
            return new CircuitBreakerMetrics("CLOSED", 0, 0, 0.0, 0.0, Duration.ZERO);
        }
        
        var metrics = circuitBreaker.getMetrics();
        return new CircuitBreakerMetrics(
                circuitBreaker.getState().name(),
                metrics.getNumberOfBufferedCalls(),
                metrics.getNumberOfFailedCalls(),
                metrics.getFailureRate(),
                metrics.getSlowCallRate(),
                Duration.ZERO // Resilience4j 2.x 不支持平均调用时长
        );
    }

    /**
     * 重置渠道断路器
     *
     * @param channelId 渠道ID
     */
    public void reset(Long channelId) {
        String name = channelCircuitBreakerMap.get(channelId);
        if (name == null) {
            return;
        }
        
        CircuitBreaker circuitBreaker = registry.getAllCircuitBreakers()
                .stream()
                .filter(cb -> cb.getName().equals(name))
                .findFirst()
                .orElse(null);
        if (circuitBreaker != null) {
            circuitBreaker.reset();
            log.info("CircuitBreaker '{}' reset to CLOSED", name);
        }
    }

    /**
     * 强制打开断路器
     *
     * @param channelId 渠道ID
     */
    public void forceOpen(Long channelId) {
        String name = channelCircuitBreakerMap.get(channelId);
        if (name == null) {
            return;
        }
        
        CircuitBreaker circuitBreaker = registry.getAllCircuitBreakers()
                .stream()
                .filter(cb -> cb.getName().equals(name))
                .findFirst()
                .orElse(null);
        if (circuitBreaker != null) {
            circuitBreaker.transitionToOpenState();
            log.warn("CircuitBreaker '{}' forced to OPEN", name);
        }
    }

    /**
     * 强制关闭断路器
     *
     * @param channelId 渠道ID
     */
    public void forceClose(Long channelId) {
        String name = channelCircuitBreakerMap.get(channelId);
        if (name == null) {
            return;
        }
        
        CircuitBreaker circuitBreaker = registry.getAllCircuitBreakers()
                .stream()
                .filter(cb -> cb.getName().equals(name))
                .findFirst()
                .orElse(null);
        if (circuitBreaker != null) {
            circuitBreaker.transitionToClosedState();
            log.info("CircuitBreaker '{}' forced to CLOSED", name);
        }
    }

    /**
     * 使用断路器执行响应式操作（Mono）
     *
     * @param channelId 渠道ID
     * @param channelName 渠道名称
     * @param supplier 操作
     * @return 结果
     */
    public <T> Mono<T> executeReactive(Long channelId, String channelName, Supplier<Mono<T>> supplier) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(channelId, channelName);
        
        // 检查是否熔断
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            log.warn("CircuitBreaker '{}' is OPEN, rejecting request", circuitBreaker.getName());
            return Mono.error(new CircuitBreakerException(
                    "渠道 " + channelName + " 已熔断，请稍后重试",
                    circuitBreaker.getName(),
                    circuitBreaker.getState()
            ));
        }
        
        return supplier.get()
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }

    /**
     * 使用断路器执行响应式操作（Flux）
     *
     * @param channelId 渠道ID
     * @param channelName 渠道名称
     * @param supplier 操作
     * @return 结果
     */
    public <T> Flux<T> executeReactiveFlux(Long channelId, String channelName, Supplier<Flux<T>> supplier) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(channelId, channelName);
        
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            log.warn("CircuitBreaker '{}' is OPEN, rejecting request", circuitBreaker.getName());
            return Flux.error(new CircuitBreakerException(
                    "渠道 " + channelName + " 已熔断，请稍后重试",
                    circuitBreaker.getName(),
                    circuitBreaker.getState()
            ));
        }
        
        return supplier.get()
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }

    /**
     * 断路器指标
     */
    public record CircuitBreakerMetrics(
            String state,
            int bufferedCalls,
            int failedCalls,
            double failureRate,
            double slowCallRate,
            Duration averageCallDuration
    ) {}

    /**
     * 断路器异常
     */
    public static class CircuitBreakerException extends RuntimeException {
        @Getter
        private final String circuitBreakerName;
        @Getter
        private final CircuitBreaker.State state;

        public CircuitBreakerException(String message, String circuitBreakerName, CircuitBreaker.State state) {
            super(message);
            this.circuitBreakerName = circuitBreakerName;
            this.state = state;
        }
    }
}
