package com.bank.ai.gateway.compliance;

import reactor.core.publisher.Mono;

/**
 * 内容过滤器接口
 */
public interface ContentFilter {

    /**
     * 过滤器名称
     */
    String getName();

    /**
     * 过滤顺序（越小越先执行）
     */
    int getOrder();

    /**
     * 过滤内容
     *
     * @param content   原始内容
     * @param direction 方向：INPUT/OUTPUT
     * @param context   过滤上下文
     * @return 过滤结果
     */
    Mono<FilterResult> filter(String content, String direction, FilterContext context);

    /**
     * 过滤结果
     */
    record FilterResult(
            /** 是否通过 */
            boolean passed,
            /** 处理后的内容（MASK时使用） */
            String processedContent,
            /** 违规信息（不通过时） */
            ViolationInfo violation
    ) {}

    /**
     * 违规信息
     */
    record ViolationInfo(
            /** 过滤器名称 */
            String filterName,
            /** 命中的词/规则 */
            String matchedWord,
            /** 分类 */
            String category,
            /** 处理动作：BLOCK/WARN/MASK */
            String action,
            /** 命中内容片段 */
            String contentSnippet
    ) {}

    /**
     * 过滤上下文
     */
    class FilterContext {
        private Long apiKeyId;
        private Long userId;
        private Long callLogId;
        private String model;

        public Long getApiKeyId() { return apiKeyId; }
        public void setApiKeyId(Long apiKeyId) { this.apiKeyId = apiKeyId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getCallLogId() { return callLogId; }
        public void setCallLogId(Long callLogId) { this.callLogId = callLogId; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }
}
