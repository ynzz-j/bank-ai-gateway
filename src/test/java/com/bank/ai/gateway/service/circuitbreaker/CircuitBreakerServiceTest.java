package com.bank.ai.gateway.service.circuitbreaker;

import com.bank.ai.gateway.service.circuitbreaker.CircuitBreakerService.CircuitBreakerException;
import com.bank.ai.gateway.service.circuitbreaker.CircuitBreakerService.CircuitBreakerMetrics;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 熔断器服务测试
 */
@DisplayName("熔断器服务测试")
class CircuitBreakerServiceTest {

    private CircuitBreakerRegistry registry;
    private CircuitBreakerService service;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(5)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(Exception.class)
                .build();
        
        registry = CircuitBreakerRegistry.of(defaultConfig);
        service = new CircuitBreakerService(registry);
    }

    @Nested
    @DisplayName("获取断路器测试")
    class GetCircuitBreakerTest {

        @Test
        @DisplayName("首次获取应创建新断路器")
        void shouldCreateNewCircuitBreaker() {
            CircuitBreaker cb = service.getOrCreateCircuitBreaker(1L, "test-channel");
            
            assertNotNull(cb);
            assertEquals("channel-1", cb.getName());
            assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        }

        @Test
        @DisplayName("多次获取应返回同一断路器")
        void shouldReturnSameCircuitBreaker() {
            CircuitBreaker cb1 = service.getOrCreateCircuitBreaker(1L, "test-channel");
            CircuitBreaker cb2 = service.getOrCreateCircuitBreaker(1L, "test-channel");
            
            assertSame(cb1, cb2);
        }

        @Test
        @DisplayName("不同渠道应有不同断路器")
        void shouldCreateDifferentCircuitBreakers() {
            CircuitBreaker cb1 = service.getOrCreateCircuitBreaker(1L, "channel-1");
            CircuitBreaker cb2 = service.getOrCreateCircuitBreaker(2L, "channel-2");
            
            assertNotSame(cb1, cb2);
            assertEquals("channel-1", cb1.getName());
            assertEquals("channel-2", cb2.getName());
        }
    }

    @Nested
    @DisplayName("熔断状态测试")
    class CircuitStateTest {

        @Test
        @DisplayName("初始状态应为 CLOSED")
        void shouldBeClosedInitially() {
            service.getOrCreateCircuitBreaker(1L, "test-channel");
            
            assertFalse(service.isCircuitOpen(1L));
            assertEquals("CLOSED", service.getCircuitState(1L));
        }

        @Test
        @DisplayName("强制打开后应为 OPEN")
        void shouldBeOpenAfterForceOpen() {
            service.getOrCreateCircuitBreaker(1L, "test-channel");
            service.forceOpen(1L);
            
            assertTrue(service.isCircuitOpen(1L));
            assertEquals("OPEN", service.getCircuitState(1L));
        }

        @Test
        @DisplayName("强制关闭后应为 CLOSED")
        void shouldBeClosedAfterForceClose() {
            service.getOrCreateCircuitBreaker(1L, "test-channel");
            service.forceOpen(1L);
            service.forceClose(1L);
            
            assertFalse(service.isCircuitOpen(1L));
            assertEquals("CLOSED", service.getCircuitState(1L));
        }

        @Test
        @DisplayName("重置后应为 CLOSED")
        void shouldBeClosedAfterReset() {
            service.getOrCreateCircuitBreaker(1L, "test-channel");
            service.forceOpen(1L);
            service.reset(1L);
            
            assertFalse(service.isCircuitOpen(1L));
            assertEquals("CLOSED", service.getCircuitState(1L));
        }
    }

    @Nested
    @DisplayName("熔断触发测试")
    class CircuitTriggerTest {

        @Test
        @DisplayName("失败率达到阈值应触发熔断")
        void shouldTriggerCircuitOnHighFailureRate() {
            CircuitBreaker cb = service.getOrCreateCircuitBreaker(1L, "test-channel");
            
            // 模拟失败调用（3次失败 / 3次最小调用 = 100% 失败率 > 50%）
            cb.onError(100, TimeUnit.MILLISECONDS, new RuntimeException("test error 1"));
            cb.onError(100, TimeUnit.MILLISECONDS, new RuntimeException("test error 2"));
            cb.onError(100, TimeUnit.MILLISECONDS, new RuntimeException("test error 3"));
            
            // 等待状态转换
            assertEquals(CircuitBreaker.State.OPEN, cb.getState());
            assertTrue(service.isCircuitOpen(1L));
        }

        @Test
        @DisplayName("成功调用不应触发熔断")
        void shouldNotTriggerCircuitOnSuccess() {
            CircuitBreaker cb = service.getOrCreateCircuitBreaker(1L, "test-channel");
            
            // 模拟成功调用
            cb.onSuccess(100, TimeUnit.MILLISECONDS);
            cb.onSuccess(100, TimeUnit.MILLISECONDS);
            cb.onSuccess(100, TimeUnit.MILLISECONDS);
            
            assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
            assertFalse(service.isCircuitOpen(1L));
        }
    }

    @Nested
    @DisplayName("指标测试")
    class MetricsTest {

        @Test
        @DisplayName("应返回正确的指标")
        void shouldReturnCorrectMetrics() {
            CircuitBreaker cb = service.getOrCreateCircuitBreaker(1L, "test-channel");
            
            cb.onSuccess(100, TimeUnit.MILLISECONDS);
            cb.onSuccess(200, TimeUnit.MILLISECONDS);
            cb.onError(100, TimeUnit.MILLISECONDS, new RuntimeException("test"));
            
            CircuitBreakerMetrics metrics = service.getMetrics(1L);
            
            assertEquals("CLOSED", metrics.state());
            assertEquals(3, metrics.bufferedCalls());
            assertEquals(1, metrics.failedCalls());
        }

        @Test
        @DisplayName("不存在的渠道应返回默认指标")
        void shouldReturnDefaultMetricsForNonExistentChannel() {
            CircuitBreakerMetrics metrics = service.getMetrics(999L);
            
            assertEquals("CLOSED", metrics.state());
            assertEquals(0, metrics.bufferedCalls());
            assertEquals(0, metrics.failedCalls());
        }
    }

    @Nested
    @DisplayName("响应式执行测试")
    class ReactiveExecutionTest {

        @Test
        @DisplayName("熔断状态下应抛出异常")
        void shouldThrowExceptionWhenCircuitOpen() {
            service.getOrCreateCircuitBreaker(1L, "test-channel");
            service.forceOpen(1L);
            
            assertThrows(CircuitBreakerException.class, () -> {
                service.executeReactive(1L, "test-channel", () -> reactor.core.publisher.Mono.just("test"))
                        .block();
            });
        }

        @Test
        @DisplayName("正常状态应成功执行")
        void shouldExecuteSuccessfullyWhenClosed() {
            service.getOrCreateCircuitBreaker(1L, "test-channel");
            
            String result = service.executeReactive(1L, "test-channel", () -> reactor.core.publisher.Mono.just("success"))
                    .block();
            
            assertEquals("success", result);
        }
    }
}
