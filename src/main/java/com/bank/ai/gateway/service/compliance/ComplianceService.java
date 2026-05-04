package com.bank.ai.gateway.service.compliance;

import com.bank.ai.gateway.compliance.ContentFilter;
import com.bank.ai.gateway.compliance.ContentFilter.FilterResult;
import com.bank.ai.gateway.compliance.ContentFilter.FilterContext;
import com.bank.ai.gateway.compliance.ContentFilter.ViolationInfo;
import com.bank.ai.gateway.model.entity.ViolationLog;
import com.bank.ai.gateway.repository.compliance.ViolationLogMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 合规服务
 * 协调过滤器链执行
 */
@Slf4j
@Service
public class ComplianceService {

    private final List<ContentFilter> filters;
    private final ViolationLogMapper violationLogMapper;

    /** 排序后的过滤器链 */
    private List<ContentFilter> filterChain;

    public ComplianceService(List<ContentFilter> filters, ViolationLogMapper violationLogMapper) {
        this.filters = filters;
        this.violationLogMapper = violationLogMapper;
    }

    @PostConstruct
    public void init() {
        // 按 order 排序
        this.filterChain = new ArrayList<>(filters);
        this.filterChain.sort(Comparator.comparingInt(ContentFilter::getOrder));
        log.info("合规过滤器链初始化完成，共 {} 个过滤器", filterChain.size());
        filterChain.forEach(f -> log.debug("  - {} (order={})", f.getName(), f.getOrder()));
    }

    /**
     * 过滤输入内容
     *
     * @param content 原始内容
     * @param context 过滤上下文
     * @return 过滤结果
     */
    public Mono<FilterResult> filterInput(String content, FilterContext context) {
        return filterChain(content, "INPUT", context);
    }

    /**
     * 过滤输出内容
     *
     * @param content AI 响应内容
     * @param context 过滤上下文
     * @return 过滤结果
     */
    public Mono<FilterResult> filterOutput(String content, FilterContext context) {
        return filterChain(content, "OUTPUT", context);
    }

    /**
     * 流式输出过滤（滑动窗口）
     * 用于 SSE 流式响应的逐块过滤
     *
     * @param chunk   单个 chunk
     * @param context 过滤上下文
     * @return 过滤结果
     */
    public Mono<FilterResult> filterStreamChunk(String chunk, FilterContext context) {
        // 流式过滤：只做敏感词检测，不做脱敏（脱敏会破坏流式结构）
        return filterChain(chunk, "OUTPUT", context);
    }

    // ==================== 私有方法 ====================

    private Mono<FilterResult> filterChain(String content, String direction, FilterContext context) {
        if (filterChain.isEmpty()) {
            return Mono.just(new FilterResult(true, content, null));
        }

        FilterContext ctx = context != null ? context : new FilterContext();
        return executeFilterChain(content, direction, ctx, 0);
    }

    private Mono<FilterResult> executeFilterChain(String content, String direction, FilterContext ctx, int index) {
        if (index >= filterChain.size()) {
            return Mono.just(new FilterResult(true, content, null));
        }

        ContentFilter filter = filterChain.get(index);
        return filter.filter(content, direction, ctx)
                .flatMap(result -> {
                    if (!result.passed()) {
                        return recordViolation(result.violation(), direction, ctx)
                                .thenReturn(result);
                    }
                    String nextContent = result.processedContent() != null
                            ? result.processedContent()
                            : content;
                    return executeFilterChain(nextContent, direction, ctx, index + 1);
                });
    }

    private Mono<Void> recordViolation(ViolationInfo violation, String direction, FilterContext context) {
        if (violation == null) {
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {
            try {
                ViolationLog log = new ViolationLog();
                log.setApiKeyId(context.getApiKeyId());
                log.setUserId(context.getUserId());
                log.setCallLogId(context.getCallLogId());
                log.setFilterName(violation.filterName());
                log.setMatchedWord(violation.matchedWord());
                log.setCategory(violation.category());
                log.setAction(violation.action());
                log.setContentSnippet(violation.contentSnippet());
                log.setDirection(direction);
                log.setCreatedAt(LocalDateTime.now());

                violationLogMapper.insert(log);
            } catch (Exception e) {
                ComplianceService.log.error("记录违规日志失败", e);
            }
        });
    }
}
