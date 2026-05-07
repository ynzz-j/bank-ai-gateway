package com.bank.ai.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 熔断器配置
 *
 * <p>使用 Spring Boot Auto-Config，配置来自 application.yml。
 *
 * <h3>application.yml 配置示例：</h3>
 * <pre>
 * resilience4j:
 *   circuitbreaker:
 *     configs:
 *       default:
 *         failure-rate-threshold: 50
 *         slow-call-rate-threshold: 80
 *         slow-call-duration-threshold: 10s
 *         sliding-window-type: COUNT_BASED
 *         sliding-window-size: 10
 *         minimum-number-of-calls: 5
 *         wait-duration-in-open-state: 60s
 *         permitted-number-of-calls-in-half-open-state: 3
 *     instances:
 *       channel-circuit:
 *         base-config: default
 * </pre>
 *
 * <h3>熔断状态机：</h3>
 * <ul>
 *   <li>CLOSED：正常状态，允许所有请求</li>
 *   <li>OPEN：熔断状态，拒绝所有请求，等待恢复</li>
 *   <li>HALF_OPEN：半开状态，允许少量请求探测是否恢复</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class CircuitBreakerConfiguration {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private TimeLimiterRegistry timeLimiterRegistry;

    /**
     * 注册熔断器事件监听
     */
    @PostConstruct
    public void init() {
        // 注册事件监听
        circuitBreakerRegistry.getEventPublisher()
                .onEntryAdded(event -> 
                    log.info("CircuitBreaker '{}' created with config: {}", 
                        event.getAddedEntry().getName(),
                        event.getAddedEntry().getCircuitBreakerConfig()))
                .onEvent(event -> 
                    log.debug("CircuitBreaker event: {}", event.toString()));
        
        log.info("CircuitBreakerRegistry initialized with {} instances", 
            circuitBreakerRegistry.getAllCircuitBreakers().size());
        log.info("TimeLimiterRegistry initialized with timeout: {}s", 
            timeLimiterRegistry.getDefaultConfig().getTimeoutDuration().getSeconds());
    }
}
