package com.bank.ai.gateway.compliance.impl;

import com.bank.ai.gateway.compliance.ContentFilter;
import com.bank.ai.gateway.config.ComplianceConfig;
import com.bank.ai.gateway.model.entity.SensitiveWord;
import com.bank.ai.gateway.repository.compliance.SensitiveWordMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 敏感词过滤器
 * 支持 DFA 算法高效匹配
 */
@Slf4j
@Component
public class KeywordFilter implements ContentFilter {

    private static final String NAME = "KeywordFilter";
    private static final int ORDER = 20;  // 在 RegexFilter 之后

    private final SensitiveWordMapper sensitiveWordMapper;
    private final ComplianceConfig complianceConfig;

    /** DFA 树根节点 */
    private volatile DFANode dfaRoot;

    /** 敏感词-> 实体映射 */
    private final Map<String, SensitiveWord> wordEntityMap = new ConcurrentHashMap<>();

    public KeywordFilter(SensitiveWordMapper sensitiveWordMapper, ComplianceConfig complianceConfig) {
        this.sensitiveWordMapper = sensitiveWordMapper;
        this.complianceConfig = complianceConfig;
    }

    @PostConstruct
    public void init() {
        reloadWords();
    }

    /**
     * 根据配置的重载间隔定时重载敏感词库
     */
    @Scheduled(fixedRateString = "${compliance.keyword.reload-interval:300000}")
    public void scheduledReload() {
        reloadWords();
    }

    /**
     * 手动重载敏感词库
     */
    public synchronized void reloadWords() {
        try {
            String source = complianceConfig.getKeyword().getSource();
            List<SensitiveWord> words;

            if ("FILE".equalsIgnoreCase(source)) {
                // 从文件加载（后续实现）
                String filePath = complianceConfig.getKeyword().getFilePath();
                log.warn("FILE 模式暂未实现，filePath={}", filePath);
                words = Collections.emptyList();
            } else {
                // 默认从数据库加载
                words = sensitiveWordMapper.selectAllEnabled();
            }

            DFANode newRoot = new DFANode();
            Map<String, SensitiveWord> newMap = new ConcurrentHashMap<>();

            for (SensitiveWord entity : words) {
                String word = entity.getWord();
                if (word == null || word.isEmpty()) continue;

                newMap.put(word, entity);
                insertWord(newRoot, word);
            }

            this.dfaRoot = newRoot;
            this.wordEntityMap.clear();
            this.wordEntityMap.putAll(newMap);

            log.info("敏感词库重载完成，来源={}, 共 {} 个词", source, words.size());
        } catch (Exception e) {
            log.error("重载敏感词库失败", e);
        }
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

        List<MatchResult> matches = searchWords(content);

        if (matches.isEmpty()) {
            return Mono.just(new FilterResult(true, content, null));
        }

        // 如果启用组合规则，检查是否有多个敏感词组合命中
        if (complianceConfig.getKeyword().isComboRuleEnabled() && matches.size() > 1) {
            log.debug("组合规则命中，共 {} 个敏感词", matches.size());
            // 组合规则下，所有命中词都记录
            matches.forEach(m -> log.info("组合命中: {} [{}]", m.word(), m.category()));
        }

        // 找到最高优先级的处理动作
        String highestAction = getHighestPriorityAction(matches);
        MatchResult firstMatch = matches.get(0);

        ViolationInfo violation = new ViolationInfo(
                NAME,
                firstMatch.word(),
                firstMatch.category(),
                highestAction,
                truncateContent(content, firstMatch.startIndex(), firstMatch.endIndex())
        );

        if ("BLOCK".equals(highestAction)) {
            return Mono.just(new FilterResult(false, null, violation));
        } else if ("MASK".equals(highestAction)) {
            String masked = maskContent(content, matches);
            return Mono.just(new FilterResult(true, masked, violation));
        } else { // WARN
            return Mono.just(new FilterResult(true, content, violation));
        }
    }

    // ==================== DFA 算法 ====================

    private static class DFANode {
        Map<Character, DFANode> children = new HashMap<>();
        boolean isEnd;
        String word;
    }

    private void insertWord(DFANode root, String word) {
        DFANode current = root;
        for (char c : word.toCharArray()) {
            current = current.children.computeIfAbsent(c, k -> new DFANode());
        }
        current.isEnd = true;
        current.word = word;
    }

    private List<MatchResult> searchWords(String content) {
        List<MatchResult> results = new ArrayList<>();
        DFANode root = this.dfaRoot;
        if (root == null) return results;

        char[] chars = content.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            DFANode current = root;
            int j = i;

            while (j < chars.length && current.children.containsKey(chars[j])) {
                current = current.children.get(chars[j]);
                j++;

                if (current.isEnd) {
                    String word = current.word;
                    SensitiveWord entity = wordEntityMap.get(word);
                    if (entity != null) {
                        results.add(new MatchResult(
                                word,
                                entity.getCategory(),
                                entity.getAction(),
                                i,
                                j
                        ));
                    }
                }
            }
        }

        return results;
    }

    private record MatchResult(
            String word,
            String category,
            String action,
            int startIndex,
            int endIndex
    ) {}

    // ==================== 辅助方法 ====================

    private String getHighestPriorityAction(List<MatchResult> matches) {
        // 优先级：BLOCK > MASK > WARN
        for (MatchResult match : matches) {
            if ("BLOCK".equals(match.action())) return "BLOCK";
        }
        for (MatchResult match : matches) {
            if ("MASK".equals(match.action())) return "MASK";
        }
        return "WARN";
    }

    private String maskContent(String content, List<MatchResult> matches) {
        StringBuilder sb = new StringBuilder(content);
        // 从后往前替换，避免索引变化
        matches.sort((a, b) -> Integer.compare(b.startIndex(), a.startIndex()));
        for (MatchResult match : matches) {
            String word = match.word();
            String masked = "*".repeat(word.length());
            sb.replace(match.startIndex(), match.endIndex(), masked);
        }
        return sb.toString();
    }

    private String truncateContent(String content, int start, int end) {
        int snippetStart = Math.max(0, start - 10);
        int snippetEnd = Math.min(content.length(), end + 10);
        String snippet = content.substring(snippetStart, snippetEnd);
        // 脱敏
        return snippet.replaceAll("[\u4e00-\u9fa5a-zA-Z0-9]", "*");
    }
}
