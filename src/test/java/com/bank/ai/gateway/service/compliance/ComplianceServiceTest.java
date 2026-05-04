package com.bank.ai.gateway.service.compliance;

import com.bank.ai.gateway.compliance.ContentFilter;
import com.bank.ai.gateway.compliance.ContentFilter.FilterContext;
import com.bank.ai.gateway.compliance.ContentFilter.FilterResult;
import com.bank.ai.gateway.compliance.ContentFilter.ViolationInfo;
import com.bank.ai.gateway.repository.compliance.ViolationLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ComplianceService 单元测试
 */
@DisplayName("ComplianceService 测试")
class ComplianceServiceTest {

    @Mock
    private ViolationLogMapper violationLogMapper;

    private ComplianceService complianceService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Nested
    @DisplayName("空过滤器链测试")
    class EmptyFilterChainTest {

        @BeforeEach
        void setUpEmptyFilters() {
            complianceService = new ComplianceService(Collections.emptyList(), violationLogMapper);
            complianceService.init();
        }

        @Test
        @DisplayName("空过滤器链应直接通过")
        void shouldPassWithEmptyFilterChain() {
            FilterContext context = new FilterContext();

            StepVerifier.create(complianceService.filterInput("test content", context))
                    .assertNext(result -> {
                        assertTrue(result.passed());
                        assertEquals("test content", result.processedContent());
                        assertNull(result.violation());
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("单过滤器测试")
    class SingleFilterTest {

        @BeforeEach
        void setUpSingleFilter() {
            ContentFilter passFilter = new ContentFilter() {
                @Override
                public String getName() { return "PassFilter"; }

                @Override
                public int getOrder() { return 1; }

                @Override
                public Mono<FilterResult> filter(String content, String direction, FilterContext context) {
                    return Mono.just(new FilterResult(true, content, null));
                }
            };

            complianceService = new ComplianceService(Arrays.asList(passFilter), violationLogMapper);
            complianceService.init();
        }

        @Test
        @DisplayName("单个过滤器通过")
        void shouldPassSingleFilter() {
            FilterContext context = new FilterContext();

            StepVerifier.create(complianceService.filterInput("content", context))
                    .assertNext(result -> assertTrue(result.passed()))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("多过滤器链测试")
    class MultiFilterChainTest {

        @BeforeEach
        void setUpMultiFilters() {
            // 第一个过滤器：添加前缀
            ContentFilter prefixFilter = new ContentFilter() {
                @Override
                public String getName() { return "PrefixFilter"; }

                @Override
                public int getOrder() { return 1; }

                @Override
                public Mono<FilterResult> filter(String content, String direction, FilterContext context) {
                    return Mono.just(new FilterResult(true, "[PREFIX] " + content, null));
                }
            };

            // 第二个过滤器：添加后缀
            ContentFilter suffixFilter = new ContentFilter() {
                @Override
                public String getName() { return "SuffixFilter"; }

                @Override
                public int getOrder() { return 2; }

                @Override
                public Mono<FilterResult> filter(String content, String direction, FilterContext context) {
                    return Mono.just(new FilterResult(true, content + " [SUFFIX]", null));
                }
            };

            complianceService = new ComplianceService(Arrays.asList(prefixFilter, suffixFilter), violationLogMapper);
            complianceService.init();
        }

        @Test
        @DisplayName("多过滤器链按顺序执行")
        void shouldExecuteFiltersInOrder() {
            FilterContext context = new FilterContext();

            StepVerifier.create(complianceService.filterInput("content", context))
                    .assertNext(result -> {
                        assertTrue(result.passed());
                        assertEquals("[PREFIX] content [SUFFIX]", result.processedContent());
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("过滤器拦截测试")
    class BlockFilterTest {

        @BeforeEach
        void setUpBlockFilter() {
            ContentFilter blockFilter = new ContentFilter() {
                @Override
                public String getName() { return "BlockFilter"; }

                @Override
                public int getOrder() { return 1; }

                @Override
                public Mono<FilterResult> filter(String content, String direction, FilterContext context) {
                    if (content.contains("blocked")) {
                        return Mono.just(new FilterResult(false, null,
                                new ViolationInfo("BlockFilter", "blocked", "PROHIBITED", "BLOCK", content)));
                    }
                    return Mono.just(new FilterResult(true, content, null));
                }
            };

            complianceService = new ComplianceService(Arrays.asList(blockFilter), violationLogMapper);
            complianceService.init();
        }

        @Test
        @DisplayName("应拦截包含敏感词的内容")
        void shouldBlockSensitiveContent() {
            FilterContext context = new FilterContext();
            context.setApiKeyId(1L);
            context.setUserId(100L);

            StepVerifier.create(complianceService.filterInput("this is blocked content", context))
                    .assertNext(result -> {
                        assertFalse(result.passed());
                        assertNotNull(result.violation());
                        assertEquals("BlockFilter", result.violation().filterName());
                        assertEquals("blocked", result.violation().matchedWord());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("应通过正常内容")
        void shouldPassNormalContent() {
            FilterContext context = new FilterContext();

            StepVerifier.create(complianceService.filterInput("normal content", context))
                    .assertNext(result -> assertTrue(result.passed()))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("输入输出方向测试")
    class DirectionTest {

        @BeforeEach
        void setUpDirectionFilter() {
            ContentFilter directionFilter = new ContentFilter() {
                @Override
                public String getName() { return "DirectionFilter"; }

                @Override
                public int getOrder() { return 1; }

                @Override
                public Mono<FilterResult> filter(String content, String direction, FilterContext context) {
                    // 根据方向处理
                    if ("INPUT".equals(direction)) {
                        return Mono.just(new FilterResult(true, "[IN] " + content, null));
                    } else {
                        return Mono.just(new FilterResult(true, "[OUT] " + content, null));
                    }
                }
            };

            complianceService = new ComplianceService(Arrays.asList(directionFilter), violationLogMapper);
            complianceService.init();
        }

        @Test
        @DisplayName("输入过滤应标记INPUT方向")
        void shouldMarkInputDirection() {
            FilterContext context = new FilterContext();

            StepVerifier.create(complianceService.filterInput("content", context))
                    .assertNext(result -> assertEquals("[IN] content", result.processedContent()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("输出过滤应标记OUTPUT方向")
        void shouldMarkOutputDirection() {
            FilterContext context = new FilterContext();

            StepVerifier.create(complianceService.filterOutput("content", context))
                    .assertNext(result -> assertEquals("[OUT] content", result.processedContent()))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("流式过滤测试")
    class StreamFilterTest {

        @BeforeEach
        void setUpStreamFilter() {
            complianceService = new ComplianceService(Collections.emptyList(), violationLogMapper);
            complianceService.init();
        }

        @Test
        @DisplayName("流式块过滤应正常工作")
        void shouldFilterStreamChunk() {
            FilterContext context = new FilterContext();

            StepVerifier.create(complianceService.filterStreamChunk("chunk", context))
                    .assertNext(result -> assertTrue(result.passed()))
                    .verifyComplete();
        }
    }
}
