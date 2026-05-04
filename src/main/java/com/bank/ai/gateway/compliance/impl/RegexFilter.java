package com.bank.ai.gateway.compliance.impl;

import com.bank.ai.gateway.compliance.ContentFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正则过滤器
 * 用于脱敏敏感信息（手机号、身份证、银行卡、邮箱等）
 */
@Slf4j
@Component
public class RegexFilter implements ContentFilter {

    private static final String NAME = "RegexFilter";
    private static final int ORDER = 10;  // 最先执行

    /** 脱敏规则：正则 -> 替换函数 */
    private static final Map<Pattern, MaskFunction> MASK_RULES = new LinkedHashMap<>();

    static {
        // 手机号：138****1234
        MASK_RULES.put(
                Pattern.compile("1[3-9]\\d{9}"),
                (m, s) -> s.substring(0, 3) + "****" + s.substring(7)
        );

        // 身份证：110***********1234
        MASK_RULES.put(
                Pattern.compile("[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]"),
                (m, s) -> s.substring(0, 3) + "***********" + s.substring(14)
        );

        // 银行卡：6222************1234
        MASK_RULES.put(
                Pattern.compile("[1-9]\\d{14,18}"),
                (m, s) -> s.substring(0, 4) + "************" + s.substring(s.length() - 4)
        );

        // 邮箱：z***@example.com
        MASK_RULES.put(
                Pattern.compile("[\\w.-]+@[\\w.-]+\\.\\w+"),
                (m, s) -> {
                    int atIndex = s.indexOf('@');
                    if (atIndex <= 1) return s;
                    return s.charAt(0) + "***" + s.substring(atIndex);
                }
        );
    }

    @FunctionalInterface
    private interface MaskFunction {
        String apply(Matcher matcher, String matched);
    }

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

        String processed = content;
        boolean hasMatch = false;

        for (Map.Entry<Pattern, MaskFunction> entry : MASK_RULES.entrySet()) {
            Pattern pattern = entry.getKey();
            MaskFunction maskFn = entry.getValue();
            Matcher matcher = pattern.matcher(processed);

            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                hasMatch = true;
                String matched = matcher.group();
                String masked = maskFn.apply(matcher, matched);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(masked));
            }
            matcher.appendTail(sb);
            processed = sb.toString();
        }

        // 正则过滤器不拦截，只脱敏
        return Mono.just(new FilterResult(true, processed, null));
    }
}
