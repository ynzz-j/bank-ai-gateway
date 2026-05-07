package com.bank.ai.gateway.compliance.impl;

import com.bank.ai.gateway.compliance.ContentFilter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 免责声明过滤器
 * 在 OUTPUT 方向末尾追加风险提示
 */
@Component
public class DisclaimerFilter implements ContentFilter {

    private static final String NAME = "DisclaimerFilter";
    private static final int ORDER = 30;  // 最后执行

    private static final String DISCLAIMER = "\n\n【风险提示】本回复由人工智能生成，仅供参考，不构成任何投资建议或专业意见。\n" +
            "银行不对 AI 生成内容的准确性、完整性和可靠性承担责任。如需专业服务，请咨询相关专业人士。";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<FilterResult> filter(String content, String direction, FilterContext context) {
        if (content == null || content.isEmpty()) {
            return Mono.just(new FilterResult(true, content, null));
        }

        // 仅在 OUTPUT 方向追加免责声明
        if ("OUTPUT".equals(direction)) {
            String withDisclaimer = content + DISCLAIMER;
            return Mono.just(new FilterResult(true, withDisclaimer, null));
        }

        return Mono.just(new FilterResult(true, content, null));
    }
}
